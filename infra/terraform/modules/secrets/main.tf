terraform {
  required_version = ">= 1.9, < 2.0"
}

variable "name_prefix" {
  type = string
}

variable "database_users" {
  description = "Per-service database login names keyed by service name."
  type        = map(string)
}

variable "master_username" {
  type    = string
  default = "admin_master"
}

variable "tags" {
  type    = map(string)
  default = {}
}

# ---------------------------------------------------------------------------
# Secret CONTAINERS only.
#
# Terraform deliberately does not generate or write any credential value here.
# Anything a Terraform resource produces (random_password, tls_private_key) is
# stored in plaintext in the state file, and the state bucket is versioned, so
# `terraform destroy` would leave the old values readable in noncurrent object
# versions long after the environment is gone.
#
# Instead each secret is created empty and populated by scripts/aws-seed-secrets.sh
# using AWS-side generation. The values exist only in Secrets Manager, which is
# deleted with recovery_window_in_days = 0 on teardown.
#
# The RDS master password is not here at all: the RDS module uses
# manage_master_user_password, so AWS creates and owns that secret directly.
# ---------------------------------------------------------------------------

resource "aws_secretsmanager_secret" "database" {
  for_each = var.database_users

  name                    = "${var.name_prefix}/${each.key}/db-credentials"
  recovery_window_in_days = 0
  tags                    = var.tags
}

resource "aws_secretsmanager_secret" "jwt_private_key" {
  name                    = "${var.name_prefix}/auth-service/jwt-private-key"
  recovery_window_in_days = 0
  tags                    = var.tags
}

resource "aws_secretsmanager_secret" "jwt_public_key" {
  name                    = "${var.name_prefix}/auth-service/jwt-public-key"
  recovery_window_in_days = 0
  tags                    = var.tags
}

output "database_secret_arns" {
  value = { for service, secret in aws_secretsmanager_secret.database : service => secret.arn }
}

output "master_username" {
  value = var.master_username
}

output "jwt_keypair_secret_arn" {
  value = aws_secretsmanager_secret.jwt_private_key.arn
}

output "jwt_public_key_secret_arn" {
  value = aws_secretsmanager_secret.jwt_public_key.arn
}
