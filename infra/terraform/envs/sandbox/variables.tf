variable "aws_profile" {
  description = "Local AWS CLI profile to use (SSO profile configured via `aws configure sso`)."
  type        = string
  default     = "pharmacy-sandbox"
}

variable "aws_region" {
  type    = string
  default = "us-east-1"

  validation {
    condition     = var.aws_region == "us-east-1"
    error_message = "The temporary AWS environment is limited to us-east-1."
  }
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

  validation {
    condition     = length(var.azs) >= 2
    error_message = "At least two availability zones are required."
  }
}

variable "vpc_cidr" {
  description = "Non-overlapping sandbox VPC CIDR."
  type        = string
  default     = "10.42.0.0/16"
}

variable "operator_cidr" {
  description = "Public IPv4 CIDR allowed to reach the EKS Kubernetes API, in /32 form."
  type        = string

  validation {
    condition     = can(cidrnetmask(var.operator_cidr)) && endswith(var.operator_cidr, "/32")
    error_message = "operator_cidr must be a single public IPv4 address in /32 CIDR notation."
  }

  validation {
    condition     = !startswith(var.operator_cidr, "0.0.0.0")
    error_message = "operator_cidr must not be an unspecified or wildcard address."
  }
}

# Fail-closed allowlist for the only public entry point. There is no default:
# a plan cannot be produced without an explicit, narrow source list. The
# application must never be reachable from the open internet because
# auth-service registration currently accepts caller-supplied roles, which
# would allow anonymous privilege escalation. See docs/aws-plan-review.md.
# How the API gateway is reached from the operator workstation.
#
#   port-forward (default): NO public load balancer, security group or ALB
#     controller is created. The operator reaches the gateway with
#     `kubectl port-forward`, which tunnels over the TLS-protected,
#     IAM-authenticated EKS API endpoint (itself restricted to operator_cidr).
#
#   alb-https: an internet-facing ALB with an HTTPS:443 listener ONLY, using a
#     caller-supplied, already-issued ACM certificate for gateway_hostname, and
#     reachable only from alb_ingress_cidrs. There is no HTTP listener, not even
#     a redirect, because a redirect still lets a client send credentials in
#     plaintext on its first request.
#
# Plain-HTTP public exposure is intentionally not an option: /api/v1/auth/login
# carries passwords and returns JWTs, and "the data is synthetic" does not make
# credentials or bearer tokens on the public internet acceptable.
variable "gateway_exposure" {
  type    = string
  default = "port-forward"

  validation {
    condition     = contains(["port-forward", "alb-https"], var.gateway_exposure)
    error_message = "gateway_exposure must be port-forward (default, no public ALB) or alb-https. Plain HTTP exposure is not supported."
  }
}

variable "gateway_certificate_arn" {
  description = "ISSUED ACM certificate ARN in aws_region covering gateway_hostname. Required only for alb-https."
  type        = string
  default     = ""

  validation {
    condition = (
      var.gateway_exposure != "alb-https"
      ? var.gateway_certificate_arn == ""
      : can(regex("^arn:aws:acm:${var.aws_region}:[0-9]{12}:certificate/[0-9a-f-]{36}$", var.gateway_certificate_arn))
    )
    error_message = "alb-https requires gateway_certificate_arn to be an ACM certificate ARN in aws_region; it must be empty for port-forward."
  }
}

variable "gateway_hostname" {
  description = "DNS name clients use for the gateway; must be covered by the ACM certificate. Required only for alb-https."
  type        = string
  default     = ""

  validation {
    condition = (
      var.gateway_exposure != "alb-https"
      ? var.gateway_hostname == ""
      : can(regex("^([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}$", var.gateway_hostname)) && !endswith(var.gateway_hostname, ".elb.amazonaws.com")
    )
    error_message = "alb-https requires gateway_hostname to be a lowercase FQDN you control (not the ALB's own elb.amazonaws.com name, which no certificate can cover); it must be empty for port-forward."
  }
}

variable "alb_ingress_cidrs" {
  description = "Explicit /32 IPv4 sources allowed to reach the HTTPS gateway ALB. Required for alb-https; must be empty otherwise."
  type        = list(string)
  default     = []

  validation {
    condition = (
      var.gateway_exposure == "alb-https"
      ? length(var.alb_ingress_cidrs) > 0
      : length(var.alb_ingress_cidrs) == 0
    )
    error_message = "alb-https requires at least one /32 source in alb_ingress_cidrs (the ALB is never left open); port-forward creates no ALB, so the list must be empty."
  }

  validation {
    condition     = alltrue([for cidr in var.alb_ingress_cidrs : can(cidrnetmask(cidr)) && endswith(cidr, "/32")])
    error_message = "Every alb_ingress_cidrs entry must be a single IPv4 address in /32 notation."
  }

  validation {
    condition     = alltrue([for cidr in var.alb_ingress_cidrs : !startswith(cidr, "0.0.0.0")])
    error_message = "alb_ingress_cidrs must not contain unspecified or wildcard addresses."
  }
}

variable "owner" {
  description = "Developer identifier for the `owner` tag."
  type        = string

  validation {
    condition     = can(regex("^[A-Za-z0-9._@-]+$", var.owner))
    error_message = "owner must use only letters, digits, period, underscore, at-sign or hyphen."
  }
}

variable "environment" {
  type    = string
  default = "sandbox-temp"
}

variable "expiration" {
  description = "Planned destroy date for the `expiration` tag (informational, not enforced by AWS)."
  type        = string

  validation {
    condition     = can(formatdate("YYYY-MM-DD", var.expiration))
    error_message = "expiration must be a valid YYYY-MM-DD date."
  }
}

variable "cost_center" {
  type    = string
  default = "learning-exercise"
}

# EKS standard-support version only; see modules/eks/main.tf and
# docs/aws-plan-review.md. Extended-support versions cost far more per hour.
variable "kubernetes_version" {
  type    = string
  default = "1.35"

  validation {
    condition     = contains(["1.34", "1.35", "1.36"], var.kubernetes_version)
    error_message = "kubernetes_version must be an EKS standard-support version (1.34, 1.35 or 1.36 as of 2026-09-30)."
  }
}

# Worker CPU architecture. Drives both the node AMI and the platform that
# scripts/aws-apply.sh builds container images for; see modules/eks/main.tf.
variable "node_architecture" {
  type    = string
  default = "arm64"

  validation {
    condition     = contains(["amd64", "arm64"], var.node_architecture)
    error_message = "node_architecture must be amd64 or arm64."
  }
}

# Sized for the fleet's memory limits, not just its requests; see
# modules/eks/main.tf and docs/aws-plan-review.md.
variable "node_instance_type" {
  type    = string
  default = "t4g.large"
}

variable "node_desired_size" {
  type    = number
  default = 2
}

variable "node_max_size" {
  type    = number
  default = 2

  validation {
    condition     = var.node_max_size >= var.node_desired_size && var.node_desired_size >= 2
    error_message = "The full fleet requires at least two nodes and node_max_size must be >= node_desired_size."
  }
}
