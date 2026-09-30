terraform {
  required_version = ">= 1.9, < 2.0"
}

variable "name_prefix" {
  type = string
}

variable "subnet_ids" {
  description = "Public subnets for EKS control-plane ENIs and managed worker nodes; there is no NAT."
  type        = list(string)
}

# Must be a version in EKS STANDARD support. Versions past end-of-standard-support
# enter extended support, which bills a significantly higher per-cluster-hour
# rate. Verified against the AWS EKS version lifecycle on 2026-09-30:
# standard support = 1.34, 1.35, 1.36; extended support = 1.31, 1.32, 1.33.
# See docs/aws-plan-review.md for the recorded dates and upgrade policy.
variable "kubernetes_version" {
  type    = string
  default = "1.35"

  validation {
    condition     = contains(["1.34", "1.35", "1.36"], var.kubernetes_version)
    error_message = "kubernetes_version must be an EKS standard-support version (1.34, 1.35 or 1.36 as of 2026-09-30). Older versions bill extended-support fees; re-verify the lifecycle table before widening this list."
  }
}

# CPU architecture for the worker nodes. This single value drives the node AMI
# family AND is exported so image builds target the same platform. Pushing an
# image built for the other architecture makes every pod crash-loop with
# "exec format error", so the two must never be configured independently.
# The ALB controller can create internet-facing load balancers, so its IAM role
# exists only when the HTTPS gateway ALB is explicitly requested.
variable "create_alb_controller_role" {
  type    = bool
  default = false
}

variable "node_architecture" {
  type    = string
  default = "arm64"

  validation {
    condition     = contains(["amd64", "arm64"], var.node_architecture)
    error_message = "node_architecture must be amd64 or arm64."
  }
}

# t4g.large (2 vCPU / 8 GiB) rather than t4g.medium: the fleet's pod *requests*
# fit on two medium nodes, but its memory *limits* total ~8.6 GiB against ~6.4 GiB
# of allocatable memory there, so warmed-up JVMs would trigger node memory
# pressure and eviction. See docs/aws-plan-review.md for the arithmetic.
variable "node_instance_type" {
  type    = string
  default = "t4g.large"
}

variable "node_desired_size" {
  type    = number
  default = 2
}

variable "node_max_size" {
  type    = number
  default = 2
}

variable "operator_cidr" {
  type = string
}

variable "service_account_namespace" {
  type    = string
  default = "pharmacy"
}

variable "workload_secret_arns" {
  description = "Exact Secrets Manager ARNs each workload can read."
  type        = map(list(string))
}

variable "db_bootstrap_secret_arns" {
  description = "Database login secrets required by the one-time schema bootstrap job."
  type        = list(string)
}

variable "control_plane_log_types" {
  description = "EKS control-plane log streams to ingest. 'audit' is the most voluminous and costly."
  type        = list(string)
  default     = ["api", "authenticator"]
}

variable "control_plane_log_retention_days" {
  type    = number
  default = 1
}

# 30 GiB: twelve JVM images, Kafka, and the observability stack's bounded
# node-local storage (Prometheus/Loki/Tempo emptyDir, ~6 GiB worst case) must
# fit below kubelet's image/node-fs eviction thresholds.
variable "node_volume_size_gb" {
  type    = number
  default = 30
}

variable "tags" {
  type    = map(string)
  default = {}
}

resource "aws_iam_role" "cluster" {
  name = "${var.name_prefix}-eks-cluster-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "eks.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })

  tags = var.tags
}

resource "aws_iam_role_policy_attachment" "cluster_policy" {
  role       = aws_iam_role.cluster.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSClusterPolicy"
}

resource "aws_iam_role" "node" {
  name = "${var.name_prefix}-eks-node-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })

  tags = var.tags
}

resource "aws_iam_role_policy_attachment" "node_worker" {
  role       = aws_iam_role.node.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSWorkerNodePolicy"
}

resource "aws_iam_role_policy_attachment" "node_cni" {
  role       = aws_iam_role.node.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKS_CNI_Policy"
}

resource "aws_iam_role_policy_attachment" "node_ecr_readonly" {
  role       = aws_iam_role.node.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"
}

resource "aws_launch_template" "workers" {
  name_prefix = "${var.name_prefix}-workers-"

  block_device_mappings {
    device_name = "/dev/xvda"

    ebs {
      volume_size           = var.node_volume_size_gb
      volume_type           = "gp3"
      encrypted             = true
      delete_on_termination = true
    }
  }

  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required"
    http_put_response_hop_limit = 1
  }

  tag_specifications {
    resource_type = "instance"
    tags = merge(var.tags, {
      Name                                       = "${var.name_prefix}-worker"
      "eks:cluster-name"                         = var.name_prefix
      "kubernetes.io/cluster/${var.name_prefix}" = "owned"
    })
  }

  tag_specifications {
    resource_type = "volume"
    tags          = var.tags
  }
}

resource "aws_cloudwatch_log_group" "control_plane" {
  name              = "/aws/eks/${var.name_prefix}/cluster"
  retention_in_days = var.control_plane_log_retention_days
  tags              = var.tags
}

resource "aws_eks_cluster" "this" {
  name     = var.name_prefix
  role_arn = aws_iam_role.cluster.arn
  version  = var.kubernetes_version

  vpc_config {
    subnet_ids              = var.subnet_ids
    endpoint_public_access  = true
    endpoint_private_access = true
    public_access_cidrs     = [var.operator_cidr]
  }

  # Fail closed on cost: refuse to run in (or silently roll into) extended
  # support, which is billed at a much higher per-cluster-hour rate. With
  # STANDARD the cluster cannot enter paid extended support.
  upgrade_policy {
    support_type = "STANDARD"
  }

  enabled_cluster_log_types = var.control_plane_log_types
  tags                      = var.tags

  depends_on = [
    aws_iam_role_policy_attachment.cluster_policy,
    aws_cloudwatch_log_group.control_plane,
  ]
}

locals {
  node_ami_type = var.node_architecture == "arm64" ? "AL2023_ARM_64_STANDARD" : "AL2023_x86_64_STANDARD"

  # Graviton instance families. Guarding on the family prefix keeps an
  # amd64 instance type from being paired with an arm64 AMI (and vice versa),
  # which would leave the nodes unable to join the cluster.
  node_is_graviton = can(regex("^(t4g|m6g|m7g|c6g|c7g|r6g|r7g)\\.", var.node_instance_type))
}

resource "aws_eks_node_group" "this" {
  lifecycle {
    precondition {
      condition     = local.node_is_graviton == (var.node_architecture == "arm64")
      error_message = "node_instance_type '${var.node_instance_type}' does not match node_architecture '${var.node_architecture}'. Use a Graviton type (e.g. t4g.large) for arm64 or an x86_64 type (e.g. t3.large) for amd64."
    }
  }

  ami_type = local.node_ami_type

  cluster_name    = aws_eks_cluster.this.name
  node_group_name = "${var.name_prefix}-ng"
  node_role_arn   = aws_iam_role.node.arn
  subnet_ids      = var.subnet_ids

  instance_types = [var.node_instance_type]
  capacity_type  = "ON_DEMAND"

  launch_template {
    id      = aws_launch_template.workers.id
    version = aws_launch_template.workers.latest_version
  }

  scaling_config {
    desired_size = var.node_desired_size
    min_size     = 2
    max_size     = var.node_max_size
  }

  update_config {
    max_unavailable = 1
  }

  tags = var.tags

  depends_on = [
    aws_iam_role_policy_attachment.node_worker,
    aws_iam_role_policy_attachment.node_cni,
    aws_iam_role_policy_attachment.node_ecr_readonly,
  ]
}

data "aws_eks_addon_version" "vpc_cni" {
  addon_name         = "vpc-cni"
  kubernetes_version = var.kubernetes_version
  most_recent        = true
}

resource "aws_eks_addon" "vpc_cni" {
  cluster_name  = aws_eks_cluster.this.name
  addon_name    = "vpc-cni"
  addon_version = data.aws_eks_addon_version.vpc_cni.version
  configuration_values = jsonencode({
    enableNetworkPolicy = true
  })
  resolve_conflicts_on_create = "OVERWRITE"
  resolve_conflicts_on_update = "PRESERVE"

  tags = var.tags

  depends_on = [aws_eks_node_group.this]
}

data "tls_certificate" "eks" {
  url = aws_eks_cluster.this.identity[0].oidc[0].issuer
}

resource "aws_iam_openid_connect_provider" "eks" {
  url             = aws_eks_cluster.this.identity[0].oidc[0].issuer
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = [data.tls_certificate.eks.certificates[0].sha1_fingerprint]
  tags            = var.tags
}

locals {
  oidc_provider_url = replace(aws_iam_openid_connect_provider.eks.url, "https://", "")
  oidc_provider_arn = aws_iam_openid_connect_provider.eks.arn
}

resource "aws_iam_role" "workload" {
  for_each = var.workload_secret_arns

  name = "${var.name_prefix}-irsa-${each.key}"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Federated = local.oidc_provider_arn }
      Action    = "sts:AssumeRoleWithWebIdentity"
      Condition = {
        StringEquals = {
          "${local.oidc_provider_url}:sub" = "system:serviceaccount:${var.service_account_namespace}:${each.key}"
          "${local.oidc_provider_url}:aud" = "sts.amazonaws.com"
        }
      }
    }]
  })

  tags = var.tags
}

resource "aws_iam_role_policy" "workload_secrets" {
  for_each = var.workload_secret_arns

  name = "secrets-read"
  role = aws_iam_role.workload[each.key].id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["secretsmanager:GetSecretValue"]
      Resource = each.value
    }]
  })
}

resource "aws_iam_role" "db_bootstrap" {
  name = "${var.name_prefix}-irsa-db-bootstrap"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Federated = local.oidc_provider_arn }
      Action    = "sts:AssumeRoleWithWebIdentity"
      Condition = {
        StringEquals = {
          "${local.oidc_provider_url}:sub" = "system:serviceaccount:${var.service_account_namespace}:db-bootstrap"
          "${local.oidc_provider_url}:aud" = "sts.amazonaws.com"
        }
      }
    }]
  })

  tags = var.tags
}

resource "aws_iam_role_policy" "db_bootstrap_secrets" {
  name = "database-secrets-read"
  role = aws_iam_role.db_bootstrap.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["secretsmanager:GetSecretValue"]
      Resource = var.db_bootstrap_secret_arns
    }]
  })
}

resource "aws_iam_role" "alb_controller" {
  count = var.create_alb_controller_role ? 1 : 0

  name = "${var.name_prefix}-irsa-alb-controller"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Federated = local.oidc_provider_arn }
      Action    = "sts:AssumeRoleWithWebIdentity"
      Condition = {
        StringEquals = {
          "${local.oidc_provider_url}:sub" = "system:serviceaccount:kube-system:aws-load-balancer-controller"
          "${local.oidc_provider_url}:aud" = "sts.amazonaws.com"
        }
      }
    }]
  })

  tags = var.tags
}

resource "aws_iam_role_policy" "alb_controller" {
  count = var.create_alb_controller_role ? 1 : 0

  name   = "alb-controller-policy"
  role   = aws_iam_role.alb_controller[0].id
  policy = file("${path.module}/policies/alb-controller-policy.json")
}

# ---------------------------------------------------------------------------
# EBS CSI driver
#
# EKS ships no working default StorageClass for dynamic provisioning: the
# in-tree "kubernetes.io/aws-ebs" provisioner behind the legacy gp2 class was
# removed in Kubernetes 1.31, so on this cluster any PersistentVolumeClaim
# stays Pending forever without this driver. The Kafka broker and any
# observability stack with PVCs therefore depend on it.
# ---------------------------------------------------------------------------

resource "aws_iam_role" "ebs_csi" {
  name = "${var.name_prefix}-irsa-ebs-csi"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Federated = local.oidc_provider_arn }
      Action    = "sts:AssumeRoleWithWebIdentity"
      Condition = {
        StringEquals = {
          # Only the driver's own controller service account may assume this.
          "${local.oidc_provider_url}:sub" = "system:serviceaccount:kube-system:ebs-csi-controller-sa"
          "${local.oidc_provider_url}:aud" = "sts.amazonaws.com"
        }
      }
    }]
  })

  tags = var.tags
}

# AWS-managed policy purpose-built for this driver. It is already scoped: the
# destructive actions (DeleteVolume, DeleteSnapshot, attach/detach) are
# conditioned on resources carrying the CSI driver's own tags, so the driver
# cannot touch volumes it did not create, including the RDS-backed storage.
resource "aws_iam_role_policy_attachment" "ebs_csi" {
  role       = aws_iam_role.ebs_csi.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonEBSCSIDriverPolicy"
}

# Deny any volume creation that is not tagged for this cluster, so a
# compromised or misconfigured driver cannot create untracked, unbilled-to-us
# volumes that would survive teardown unnoticed.
resource "aws_iam_role_policy" "ebs_csi_tagging" {
  name = "ebs-csi-require-cluster-tag"
  role = aws_iam_role.ebs_csi.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Deny"
      Action   = "ec2:CreateVolume"
      Resource = "*"
      Condition = {
        StringNotEquals = {
          "aws:RequestTag/kubernetes.io/cluster/${aws_eks_cluster.this.name}" = "owned"
        }
      }
    }]
  })
}

data "aws_eks_addon_version" "ebs_csi" {
  addon_name         = "aws-ebs-csi-driver"
  kubernetes_version = var.kubernetes_version
  most_recent        = true
}

resource "aws_eks_addon" "ebs_csi" {
  cluster_name             = aws_eks_cluster.this.name
  addon_name               = "aws-ebs-csi-driver"
  addon_version            = data.aws_eks_addon_version.ebs_csi.version
  service_account_role_arn = aws_iam_role.ebs_csi.arn

  configuration_values = jsonencode({
    controller = {
      # Default is 2 replicas with anti-affinity. This sandbox runs two small
      # nodes that are already carrying the full fleet, so a second controller
      # replica costs scheduling headroom for no benefit in a disposable
      # environment.
      replicaCount = 1
      resources = {
        requests = { cpu = "10m", memory = "40Mi" }
        limits   = { memory = "256Mi" }
      }
    }
    node = {
      resources = {
        requests = { cpu = "10m", memory = "40Mi" }
        limits   = { memory = "256Mi" }
      }
    }
    defaultStorageClass = {
      # The driver's own default class is left off: the gp3 class is applied
      # from infra/k8s/sandbox/09-storage.yaml so its parameters and
      # reclaimPolicy stay reviewable in Git.
      enabled = false
    }
  })

  resolve_conflicts_on_create = "OVERWRITE"
  resolve_conflicts_on_update = "PRESERVE"

  tags = var.tags

  # The controller is a normal Deployment, so nodes must exist first.
  depends_on = [
    aws_eks_node_group.this,
    aws_iam_role_policy_attachment.ebs_csi,
  ]
}

output "ebs_csi_role_arn" {
  value = aws_iam_role.ebs_csi.arn
}

output "cluster_name" {
  value = aws_eks_cluster.this.name
}

output "cluster_endpoint" {
  value = aws_eks_cluster.this.endpoint
}

output "cluster_certificate_authority_data" {
  value = aws_eks_cluster.this.certificate_authority[0].data
}

output "cluster_security_group_id" {
  value = aws_eks_cluster.this.vpc_config[0].cluster_security_group_id
}

output "workload_role_arns" {
  value = { for service, role in aws_iam_role.workload : service => role.arn }
}

output "db_bootstrap_role_arn" {
  value = aws_iam_role.db_bootstrap.arn
}

# Empty in port-forward mode, where no ALB controller is installed.
output "alb_controller_role_arn" {
  value = try(aws_iam_role.alb_controller[0].arn, "")
}
