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
  default = "db.t4g.small"
}

# 20 GiB is the MySQL minimum. db.t4g.small (2 GiB) is retained rather than
# db.t4g.micro (1 GiB): ten schemas with ten JDBC pools exceed the connection
# and memory headroom a micro instance provides.
variable "allocated_storage_gb" {
  type    = number
  default = 20
}

variable "master_username" {
  type = string
}


variable "tags" {
  type    = map(string)
  default = {}
}

resource "aws_db_subnet_group" "this" {
  name       = "${var.name_prefix}-db-subnet-group"
  subnet_ids = var.db_subnet_ids
  tags       = var.tags
}

resource "aws_db_instance" "this" {
  identifier     = "${var.name_prefix}-mysql"
  engine         = "mysql"
  engine_version = "8.0"
  instance_class = var.instance_class

  allocated_storage = var.allocated_storage_gb
  # Storage autoscaling is disabled so a runaway workload cannot silently grow
  # billable storage; synthetic sandbox data never approaches 20 GiB.
  max_allocated_storage = 0
  storage_type          = "gp3"
  storage_encrypted     = true

  db_subnet_group_name   = aws_db_subnet_group.this.name
  vpc_security_group_ids = [var.security_group_id]
  multi_az               = false
  publicly_accessible    = false

  username = var.master_username

  # AWS generates, stores and owns the master password in Secrets Manager.
  # Using a Terraform-generated password would persist it in plaintext in the
  # versioned state bucket, where it would outlive `terraform destroy`.
  manage_master_user_password = true
  port                        = 3306

  backup_retention_period = 0
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

# The AWS-managed secret holding the master credentials. Consumed by the
# db-bootstrap Job's IAM policy; the value itself is never read by Terraform.
output "master_user_secret_arn" {
  value = aws_db_instance.this.master_user_secret[0].secret_arn
}
