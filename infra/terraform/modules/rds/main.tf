terraform {
  required_version = ">= 1.9, < 2.0"
}

variable "name_prefix" {
  type = string
}

variable "db_subnet_ids" {
  type = list(string)
}

variable "security_group_id" {
  type = string
}

variable "instance_class" {
  type    = string
  default = "db.t4g.micro"
}

variable "allocated_storage_gb" {
  type    = number
  default = 20
}

variable "master_username" {
  type    = string
  default = "admin_master"
}

variable "tags" {
  type    = map(string)
  default = {}
}

resource "random_password" "master" {
  length  = 24
  special = false
}

resource "aws_db_subnet_group" "this" {
  name       = "${var.name_prefix}-db-subnet-group"
  subnet_ids = var.db_subnet_ids
  tags       = var.tags
}

# Single small MySQL instance shared by auth-service and product-service, each
# with its own schema and DB user created post-provisioning (see
# scripts/aws-apply.sh step "create per-service schemas/users") — mirrors the
# local one-schema/one-user-per-service rule; Multi-AZ is disabled and
# deletion protection is disabled because this is a temporary, non-production
# learning exercise (confirmed in docs/aws-plan-review.md section 13).
resource "aws_db_instance" "this" {
  identifier     = "${var.name_prefix}-mysql"
  engine         = "mysql"
  engine_version = "8.0"
  instance_class = var.instance_class

  allocated_storage     = var.allocated_storage_gb
  max_allocated_storage = var.allocated_storage_gb * 2
  storage_type          = "gp3"
  storage_encrypted     = true

  db_subnet_group_name   = aws_db_subnet_group.this.name
  vpc_security_group_ids = [var.security_group_id]
  multi_az               = false
  publicly_accessible    = false

  username = var.master_username
  password = random_password.master.result
  port     = 3306

  backup_retention_period = 0 # no automated backups — temporary exercise, no data worth restoring
  deletion_protection     = false
  skip_final_snapshot     = true
  apply_immediately       = true

  tags = var.tags
}

output "endpoint" {
  value = aws_db_instance.this.address
}

output "port" {
  value = aws_db_instance.this.port
}

output "master_username" {
  value = var.master_username
}

output "master_password" {
  value     = random_password.master.result
  sensitive = true
}
