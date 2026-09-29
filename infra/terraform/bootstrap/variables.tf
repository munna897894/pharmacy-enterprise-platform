variable "aws_region" {
  description = "AWS region for the state bucket (must match the main environment's region)."
  type        = string
  default     = "us-east-1"
}

variable "state_bucket_name" {
  description = "Globally-unique S3 bucket name for Terraform remote state."
  type        = string
}

variable "project" {
  description = "Project tag applied to the state bucket."
  type        = string
  default     = "pharmacy-enterprise-platform"
}

variable "owner" {
  description = "Owner tag (developer identifier) applied to all resources."
  type        = string
}

variable "environment" {
  description = "Environment tag."
  type        = string
  default     = "sandbox-temp"
}

variable "cost_center" {
  description = "Cost center tag."
  type        = string
  default     = "learning-exercise"
}
