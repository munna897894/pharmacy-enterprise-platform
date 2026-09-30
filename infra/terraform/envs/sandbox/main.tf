locals {
  common_tags = {
    project     = "pharmacy-enterprise-platform"
    owner       = var.owner
    environment = var.environment
    expiration  = var.expiration
    cost-center = var.cost_center
  }

  service_account_namespace = "pharmacy"

  # One database/schema and one least-privilege login per data-owning service.
  database_users = {
    "auth-service"         = "auth_user"
    "product-service"      = "product_user"
    "customer-service"     = "customer_user"
    "pharmacy-service"     = "pharmacy_user"
    "inventory-service"    = "inventory_user"
    "prescription-service" = "prescription_user"
    "order-service"        = "order_user"
    "payment-service"      = "payment_user"
    "notification-service" = "notification_user"
    "audit-service"        = "audit_user"
  }

  ecr_services = [
    "auth-service",
    "api-gateway",
    "product-service",
    "customer-service",
    "pharmacy-service",
    "inventory-service",
    "prescription-service",
    "order-service",
    "payment-service",
    "notification-service",
    "audit-service",
    "external-mock-service",
  ]

  workload_secret_arns = {
    for service, secret_arn in module.secrets.database_secret_arns :
    service => service == "auth-service"
    ? [secret_arn, module.secrets.jwt_keypair_secret_arn, module.secrets.jwt_public_key_secret_arn]
    : [secret_arn]
  }
}

module "network" {
  source             = "../../modules/network"
  name_prefix        = var.name_prefix
  vpc_cidr           = var.vpc_cidr
  operator_cidr      = var.operator_cidr
  create_gateway_alb = var.gateway_exposure == "alb-https"
  alb_ingress_cidrs  = var.alb_ingress_cidrs
  azs                = var.azs
  tags               = local.common_tags
}

module "ecr" {
  source           = "../../modules/ecr"
  repository_names = [for service in local.ecr_services : "${var.name_prefix}-${service}"]
  tags             = local.common_tags
}

module "secrets" {
  source         = "../../modules/secrets"
  name_prefix    = var.name_prefix
  database_users = local.database_users
  tags           = local.common_tags
}

module "eks" {
  source                     = "../../modules/eks"
  name_prefix                = var.name_prefix
  subnet_ids                 = module.network.public_subnet_ids
  kubernetes_version         = var.kubernetes_version
  create_alb_controller_role = var.gateway_exposure == "alb-https"
  node_architecture          = var.node_architecture
  node_instance_type         = var.node_instance_type
  node_desired_size          = var.node_desired_size
  node_max_size              = var.node_max_size
  operator_cidr              = var.operator_cidr
  service_account_namespace  = local.service_account_namespace
  workload_secret_arns       = local.workload_secret_arns
  db_bootstrap_secret_arns   = concat(values(module.secrets.database_secret_arns), [module.rds.master_user_secret_arn])

  tags = local.common_tags
}

module "rds" {
  source            = "../../modules/rds"
  name_prefix       = var.name_prefix
  db_subnet_ids     = module.network.db_subnet_ids
  security_group_id = module.network.rds_security_group_id
  master_username   = module.secrets.master_username
  tags              = local.common_tags
}

# The RDS listener is reachable only by EKS-managed worker/control-plane
# security groups. It is never opened to the VPC CIDR or the public internet.
resource "aws_security_group_rule" "rds_from_eks" {
  type                     = "ingress"
  security_group_id        = module.network.rds_security_group_id
  source_security_group_id = module.eks.cluster_security_group_id
  from_port                = 3306
  to_port                  = 3306
  protocol                 = "tcp"
  description              = "MySQL from this EKS cluster only"
}
