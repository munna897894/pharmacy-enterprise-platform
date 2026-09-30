output "ecr_repository_urls" {
  value = module.ecr.repository_urls
}

output "eks_cluster_name" {
  value = module.eks.cluster_name
}

output "rds_endpoint" {
  value = module.rds.endpoint
}

output "rds_port" {
  value = module.rds.port
}

output "database_secret_arns" {
  value = module.secrets.database_secret_arns
}

# AWS-managed secret created by RDS itself (manage_master_user_password), so the
# master password never passes through Terraform state.
output "rds_master_secret_arn" {
  value = module.rds.master_user_secret_arn
}

output "jwt_keypair_secret_arn" {
  value = module.secrets.jwt_keypair_secret_arn
}

output "jwt_public_key_secret_arn" {
  value = module.secrets.jwt_public_key_secret_arn
}

output "rds_master_username" {
  value = module.secrets.master_username
}

output "workload_role_arns" {
  value = module.eks.workload_role_arns
}

output "db_bootstrap_role_arn" {
  value = module.eks.db_bootstrap_role_arn
}

output "alb_controller_role_arn" {
  value = module.eks.alb_controller_role_arn
}

output "alb_security_group_id" {
  value = module.network.alb_security_group_id
}

output "vpc_id" {
  value = module.network.vpc_id
}

output "resource_tags" {
  value = local.common_tags
}

# Consumed by scripts/aws-apply.sh so container images are built for the same
# platform as the nodes. Not sensitive: an architecture string only.
output "node_architecture" {
  description = "CPU architecture of the EKS worker nodes (amd64 or arm64)."
  value       = var.node_architecture
}

# Gateway transport mode, consumed by scripts/aws-apply.sh and
# scripts/aws-smoke-test.sh. None of these values are secret: a mode name, a
# public DNS name and a certificate ARN.
output "gateway_exposure" {
  description = "port-forward (no public ALB) or alb-https."
  value       = var.gateway_exposure
}

output "gateway_hostname" {
  description = "HTTPS hostname for the gateway ALB; empty in port-forward mode."
  value       = var.gateway_hostname
}

output "gateway_certificate_arn" {
  description = "ACM certificate on the HTTPS listener; empty in port-forward mode."
  value       = var.gateway_certificate_arn
}
