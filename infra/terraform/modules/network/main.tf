terraform {
  required_version = ">= 1.9, < 2.0"
}

variable "name_prefix" {
  type = string
}

variable "azs" {
  description = "Availability zones to spread the two public subnets across (EKS and RDS subnet groups need >= 2)."
  type        = list(string)
}

variable "vpc_cidr" {
  type = string
}

variable "operator_cidr" {
  type = string
}

variable "create_gateway_alb" {
  description = "Create the HTTPS gateway ALB security group. False in port-forward mode, where no public entry point exists."
  type        = bool
  default     = false
}

variable "alb_ingress_cidrs" {
  description = "Explicit /32 sources allowed to reach the HTTPS gateway ALB."
  type        = list(string)
  default     = []

  validation {
    condition     = alltrue([for cidr in var.alb_ingress_cidrs : endswith(cidr, "/32") && !startswith(cidr, "0.0.0.0")])
    error_message = "alb_ingress_cidrs must contain only specific /32 addresses."
  }
}

variable "tags" {
  type    = map(string)
  default = {}
}

# --- VPC -------------------------------------------------------------------

resource "aws_vpc" "this" {
  cidr_block           = var.vpc_cidr
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = merge(var.tags, { Name = "${var.name_prefix}-vpc" })
}

resource "aws_internet_gateway" "this" {
  vpc_id = aws_vpc.this.id

  tags = merge(var.tags, { Name = "${var.name_prefix}-igw" })
}

# --- Public subnets only (no NAT Gateway) -----------------------------------
# Deliberate tradeoff documented in docs/aws-plan-review.md section 6, option A:
# EKS nodes/pods and the ALB both live in public subnets with restrictive
# security groups (no inbound from the internet except via the ALB). This
# avoids all NAT Gateway hourly + per-GB data processing charges for this
# temporary learning exercise. Not a production-recommended pattern.

resource "aws_subnet" "public" {
  count                   = length(var.azs)
  vpc_id                  = aws_vpc.this.id
  cidr_block              = cidrsubnet(var.vpc_cidr, 4, count.index)
  availability_zone       = var.azs[count.index]
  map_public_ip_on_launch = true

  tags = merge(var.tags, {
    Name                                       = "${var.name_prefix}-public-${var.azs[count.index]}"
    "kubernetes.io/role/elb"                   = "1"
    "kubernetes.io/cluster/${var.name_prefix}" = "shared"
  })
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.this.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.this.id
  }

  tags = merge(var.tags, { Name = "${var.name_prefix}-public-rt" })
}

resource "aws_route_table_association" "public" {
  count          = length(aws_subnet.public)
  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

# --- Private subnets for RDS only (no route to the internet at all) --------
# RDS does not need internet access; these subnets have no route table entry
# beyond the implicit local VPC route, so they are fully isolated. This keeps
# the database out of any public-facing subnet regardless of the compute
# tradeoff above.

resource "aws_subnet" "db" {
  count             = length(var.azs)
  vpc_id            = aws_vpc.this.id
  cidr_block        = cidrsubnet(var.vpc_cidr, 4, count.index + 8)
  availability_zone = var.azs[count.index]

  tags = merge(var.tags, { Name = "${var.name_prefix}-db-${var.azs[count.index]}" })
}

# --- Security groups ---------------------------------------------------------

resource "aws_security_group" "alb" {
  count = var.create_gateway_alb ? 1 : 0

  name        = "${var.name_prefix}-alb-sg"
  description = "Gateway ALB: inbound HTTPS only, from allowlisted operator addresses."
  vpc_id      = aws_vpc.this.id

  lifecycle {
    precondition {
      condition     = length(var.alb_ingress_cidrs) > 0
      error_message = "Refusing to create a gateway ALB security group without an explicit /32 allowlist."
    }
  }

  # HTTPS only. Port 80 is deliberately never opened: login requests carry
  # passwords and responses carry JWTs, and an HTTP listener (even one that
  # only redirects) lets a client send those in plaintext first. The platform
  # also must not be broadly reachable while auth-service registration accepts
  # caller-supplied roles, so sources are restricted to explicit /32s.
  ingress {
    description = "HTTPS from explicitly allowlisted operator addresses only"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = var.alb_ingress_cidrs
  }

  egress {
    description = "Only to targets inside the sandbox VPC"
    from_port   = 0
    to_port     = 65535
    protocol    = "tcp"
    cidr_blocks = [var.vpc_cidr]
  }

  tags = merge(var.tags, { Name = "${var.name_prefix}-alb-sg" })
}

resource "aws_security_group" "rds" {
  name        = "${var.name_prefix}-rds-sg"
  description = "RDS MySQL: inbound access is added only from the EKS cluster security group."
  vpc_id      = aws_vpc.this.id

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(var.tags, { Name = "${var.name_prefix}-rds-sg" })
}

output "vpc_id" {
  value = aws_vpc.this.id
}

output "public_subnet_ids" {
  value = aws_subnet.public[*].id
}

output "db_subnet_ids" {
  value = aws_subnet.db[*].id
}

# Empty in port-forward mode, where no ALB security group exists.
output "alb_security_group_id" {
  value = try(aws_security_group.alb[0].id, "")
}

output "rds_security_group_id" {
  value = aws_security_group.rds.id
}
