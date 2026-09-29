output "ecr_repository_urls" {
  value = module.ecr.repository_urls
}

output "eks_cluster_name" {
  value = module.eks.cluster_name
}

output "eks_cluster_endpoint" {
  value = module.eks.cluster_endpoint
}

output "rds_endpoint" {
  value = module.rds.endpoint
}

output "rds_port" {
  value = module.rds.port
}

output "auth_db_secret_arn" {
  value = module.secrets.auth_db_secret_arn
}

output "product_db_secret_arn" {
  value = module.secrets.product_db_secret_arn
}

output "jwt_keypair_secret_arn" {
  value = module.secrets.jwt_keypair_secret_arn
}

output "rds_master_password" {
  value     = module.rds.master_password
  sensitive = true
}

output "configure_kubectl_command" {
  value = "aws eks update-kubeconfig --name ${module.eks.cluster_name} --region ${var.aws_region} --profile ${var.aws_profile}"
}
