terraform {
  required_version = ">= 1.9, < 2.0"
}

variable "name_prefix" {
  type = string
}

variable "vpc_id" {
  type = string
}

variable "subnet_ids" {
  description = "Public subnets used for both the EKS control plane ENIs and the managed node group."
  type        = list(string)
}

variable "node_security_group_id" {
  type = string
}

variable "kubernetes_version" {
  type    = string
  default = "1.30"
}

variable "node_instance_type" {
  type    = string
  default = "t3.small"
}

variable "node_desired_size" {
  type    = number
  default = 1
}

variable "node_max_size" {
  type    = number
  default = 2
}

variable "service_account_namespace" {
  type    = string
  default = "pharmacy"
}

variable "tags" {
  type    = map(string)
  default = {}
}

data "aws_caller_identity" "current" {}

# --- IAM roles for the EKS control plane and worker nodes -------------------

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

# --- EKS control plane -------------------------------------------------------

resource "aws_eks_cluster" "this" {
  name     = var.name_prefix
  role_arn = aws_iam_role.cluster.arn
  version  = var.kubernetes_version

  vpc_config {
    subnet_ids              = var.subnet_ids
    security_group_ids      = [var.node_security_group_id]
    endpoint_public_access  = true
    endpoint_private_access = false
  }

  tags = var.tags

  depends_on = [aws_iam_role_policy_attachment.cluster_policy]
}

# --- Minimal, temporary managed node group -----------------------------------
# Single small node by default (var.node_desired_size = 1), autoscaling capped
# at 2 — sized only for the 3 small Spring Boot Services in this exercise, per
# docs/aws-plan-review.md section 4.

resource "aws_eks_node_group" "this" {
  cluster_name    = aws_eks_cluster.this.name
  node_group_name = "${var.name_prefix}-ng"
  node_role_arn   = aws_iam_role.node.arn
  subnet_ids      = var.subnet_ids

  instance_types = [var.node_instance_type]
  capacity_type  = "ON_DEMAND"

  scaling_config {
    desired_size = var.node_desired_size
    min_size     = 1
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

# --- OIDC provider for IRSA ---------------------------------------------------

data "tls_certificate" "eks" {
  url = aws_eks_cluster.this.identity[0].oidc[0].issuer
}

resource "aws_iam_openid_connect_provider" "eks" {
  url             = aws_eks_cluster.this.identity[0].oidc[0].issuer
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = [data.tls_certificate.eks.certificates[0].sha1_fingerprint]

  tags = var.tags
}

locals {
  oidc_provider_url = replace(aws_iam_openid_connect_provider.eks.url, "https://", "")
  oidc_provider_arn = aws_iam_openid_connect_provider.eks.arn
}

# --- IRSA role: auth-service (reads its DB creds + JWT keypair secrets) ------

resource "aws_iam_role" "auth_service" {
  name = "${var.name_prefix}-irsa-auth-service"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Federated = local.oidc_provider_arn }
      Action    = "sts:AssumeRoleWithWebIdentity"
      Condition = {
        StringEquals = {
          "${local.oidc_provider_url}:sub" = "system:serviceaccount:${var.service_account_namespace}:auth-service"
          "${local.oidc_provider_url}:aud" = "sts.amazonaws.com"
        }
      }
    }]
  })

  tags = var.tags
}

resource "aws_iam_role_policy" "auth_service_secrets" {
  name = "secrets-read"
  role = aws_iam_role.auth_service.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["secretsmanager:GetSecretValue"]
      Resource = var.auth_service_secret_arns
    }]
  })
}

variable "auth_service_secret_arns" {
  type    = list(string)
  default = []
}

# --- IRSA role: product-service (reads its DB creds secret only) ------------

resource "aws_iam_role" "product_service" {
  name = "${var.name_prefix}-irsa-product-service"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Federated = local.oidc_provider_arn }
      Action    = "sts:AssumeRoleWithWebIdentity"
      Condition = {
        StringEquals = {
          "${local.oidc_provider_url}:sub" = "system:serviceaccount:${var.service_account_namespace}:product-service"
          "${local.oidc_provider_url}:aud" = "sts.amazonaws.com"
        }
      }
    }]
  })

  tags = var.tags
}

resource "aws_iam_role_policy" "product_service_secrets" {
  name = "secrets-read"
  role = aws_iam_role.product_service.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["secretsmanager:GetSecretValue"]
      Resource = var.product_service_secret_arns
    }]
  })
}

variable "product_service_secret_arns" {
  type    = list(string)
  default = []
}

# --- IRSA role: AWS Load Balancer Controller ---------------------------------
# Well-known upstream minimal policy for the ALB controller (ALB/ELBv2
# lifecycle, EC2 describe/tag, ACM/WAF describe). Not a custom broad grant.

resource "aws_iam_role" "alb_controller" {
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
  name   = "alb-controller-policy"
  role   = aws_iam_role.alb_controller.id
  policy = file("${path.module}/policies/alb-controller-policy.json")
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

output "oidc_provider_arn" {
  value = local.oidc_provider_arn
}

output "auth_service_role_arn" {
  value = aws_iam_role.auth_service.arn
}

output "product_service_role_arn" {
  value = aws_iam_role.product_service.arn
}

output "alb_controller_role_arn" {
  value = aws_iam_role.alb_controller.arn
}
