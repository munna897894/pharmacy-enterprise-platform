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

variable "state_history_retention_days" {
  description = "Days to retain noncurrent Terraform state versions before automatic expiry."
  type        = number
  default     = 7

  validation {
    condition     = var.state_history_retention_days >= 1 && var.state_history_retention_days <= 90
    error_message = "state_history_retention_days must be between 1 and 90 for a temporary learning exercise."
  }
}
