terraform {
  required_version = ">= 1.9, < 2.0"
}

variable "name_prefix" {
  type = string
}

variable "vpc_cidr" {
  type    = string
  default = "10.42.0.0/16"
}

variable "azs" {
  description = "Availability zones to spread the two public subnets across (EKS and RDS subnet groups need >= 2)."
  type        = list(string)
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
  name        = "${var.name_prefix}-alb-sg"
  description = "Internet-facing ALB: allow inbound 80/443 from the internet."
  vpc_id      = aws_vpc.this.id

  ingress {
    description = "HTTP"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(var.tags, { Name = "${var.name_prefix}-alb-sg" })
}

resource "aws_security_group" "eks_nodes" {
  name        = "${var.name_prefix}-eks-nodes-sg"
  description = "EKS worker nodes/pods: no inbound from the internet, only from the ALB and within the cluster."
  vpc_id      = aws_vpc.this.id

  ingress {
    description     = "Traffic from the ALB"
    from_port       = 0
    to_port         = 65535
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  ingress {
    description = "Intra-cluster traffic (node-to-node, node-to-control-plane)"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    self        = true
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(var.tags, { Name = "${var.name_prefix}-eks-nodes-sg" })
}

resource "aws_security_group" "rds" {
  name        = "${var.name_prefix}-rds-sg"
  description = "RDS MySQL: inbound 3306 only from the EKS node/pod security group."
  vpc_id      = aws_vpc.this.id

  ingress {
    description     = "MySQL from EKS nodes/pods only"
    from_port       = 3306
    to_port         = 3306
    protocol        = "tcp"
    security_groups = [aws_security_group.eks_nodes.id]
  }

  ingress {
    # EKS managed node groups (without a custom launch template) use the
    # cluster's auto-created security group on their primary ENI, not the
    # eks_nodes SG above. Allow MySQL from the whole VPC CIDR (private +
    # public subnets only, no internet exposure) so pods can reach RDS.
    description = "MySQL from within the VPC (covers EKS auto cluster SG)"
    from_port   = 3306
    to_port     = 3306
    protocol    = "tcp"
    cidr_blocks = [var.vpc_cidr]
  }

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

output "alb_security_group_id" {
  value = aws_security_group.alb.id
}

output "eks_nodes_security_group_id" {
  value = aws_security_group.eks_nodes.id
}

output "rds_security_group_id" {
  value = aws_security_group.rds.id
}
