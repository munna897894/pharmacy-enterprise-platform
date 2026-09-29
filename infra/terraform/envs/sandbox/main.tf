locals {
  common_tags = {
    project     = "pharmacy-enterprise-platform"
    owner       = var.owner
    environment = var.environment
    expiration  = var.expiration
    cost-center = var.cost_center
  }

  service_account_namespace = "pharmacy"
}

module "network" {
  source      = "../../modules/network"
  name_prefix = var.name_prefix
  azs         = var.azs
  tags        = local.common_tags
}

module "ecr" {
  source = "../../modules/ecr"
  repository_names = [
    "${var.name_prefix}-auth-service",
    "${var.name_prefix}-api-gateway",
    "${var.name_prefix}-product-service",
  ]
  tags = local.common_tags
}

module "secrets" {
  source      = "../../modules/secrets"
  name_prefix = var.name_prefix
  tags        = local.common_tags
}

module "rds" {
  source            = "../../modules/rds"
  name_prefix       = var.name_prefix
  db_subnet_ids     = module.network.db_subnet_ids
  security_group_id = module.network.rds_security_group_id
  tags              = local.common_tags
}

module "eks" {
  source                    = "../../modules/eks"
  name_prefix               = var.name_prefix
  vpc_id                    = module.network.vpc_id
  subnet_ids                = module.network.public_subnet_ids
  node_security_group_id    = module.network.eks_nodes_security_group_id
  kubernetes_version        = var.kubernetes_version
  node_instance_type        = var.node_instance_type
  node_desired_size         = var.node_desired_size
  service_account_namespace = local.service_account_namespace

  auth_service_secret_arns = [
    module.secrets.auth_db_secret_arn,
    module.secrets.jwt_keypair_secret_arn,
  ]
  product_service_secret_arns = [
    module.secrets.product_db_secret_arn,
  ]

  tags = local.common_tags
}

# --- Kubernetes namespace + ServiceAccounts (IRSA bindings) ------------------

resource "kubernetes_namespace" "pharmacy" {
  metadata {
    name = local.service_account_namespace
  }

  depends_on = [module.eks]
}

resource "kubernetes_service_account" "auth_service" {
  metadata {
    name      = "auth-service"
    namespace = kubernetes_namespace.pharmacy.metadata[0].name
    annotations = {
      "eks.amazonaws.com/role-arn" = module.eks.auth_service_role_arn
    }
  }
}

resource "kubernetes_service_account" "product_service" {
  metadata {
    name      = "product-service"
    namespace = kubernetes_namespace.pharmacy.metadata[0].name
    annotations = {
      "eks.amazonaws.com/role-arn" = module.eks.product_service_role_arn
    }
  }
}

resource "kubernetes_service_account" "api_gateway" {
  metadata {
    name      = "api-gateway"
    namespace = kubernetes_namespace.pharmacy.metadata[0].name
  }
}

# kube-system already exists by default in every EKS cluster, so the ALB
# controller's ServiceAccount is created directly inside it below (no
# namespace resource needed for kube-system itself).
resource "kubernetes_service_account" "alb_controller" {
  metadata {
    name      = "aws-load-balancer-controller"
    namespace = "kube-system"
    annotations = {
      "eks.amazonaws.com/role-arn" = module.eks.alb_controller_role_arn
    }
    labels = {
      "app.kubernetes.io/name"      = "aws-load-balancer-controller"
      "app.kubernetes.io/component" = "controller"
    }
  }

  depends_on = [module.eks]
}

# --- AWS Load Balancer Controller (Helm) -------------------------------------

resource "helm_release" "alb_controller" {
  name       = "aws-load-balancer-controller"
  repository = "https://aws.github.io/eks-charts"
  chart      = "aws-load-balancer-controller"
  namespace  = "kube-system"
  version    = "1.8.1"

  set {
    name  = "clusterName"
    value = module.eks.cluster_name
  }

  set {
    name  = "serviceAccount.create"
    value = "false"
  }

  set {
    name  = "serviceAccount.name"
    value = kubernetes_service_account.alb_controller.metadata[0].name
  }

  set {
    name  = "region"
    value = var.aws_region
  }

  set {
    name  = "vpcId"
    value = module.network.vpc_id
  }

  depends_on = [kubernetes_service_account.alb_controller]
}
