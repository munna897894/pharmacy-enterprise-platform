variable "aws_profile" {
  description = "Local AWS CLI profile to use (SSO profile configured via `aws configure sso`)."
  type        = string
  default     = "pharmacy-sandbox"
}

variable "aws_region" {
  type    = string
  default = "us-east-1"
}

variable "name_prefix" {
  description = "Short prefix used for naming/tagging every resource in this exercise."
  type        = string
  default     = "pharmacy-sbx"
}

variable "azs" {
  description = "Availability zones for the two-subnet layout (EKS + RDS subnet groups require >= 2)."
  type        = list(string)
  default     = ["us-east-1a", "us-east-1b"]
}

variable "owner" {
  description = "Developer identifier for the `owner` tag."
  type        = string
}

variable "environment" {
  type    = string
  default = "sandbox-temp"
}

variable "expiration" {
  description = "Planned destroy date for the `expiration` tag (informational, not enforced by AWS)."
  type        = string
}

variable "cost_center" {
  type    = string
  default = "learning-exercise"
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
