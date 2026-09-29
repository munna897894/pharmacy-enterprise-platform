terraform {
  required_version = ">= 1.9, < 2.0"
}

variable "repository_names" {
  description = "List of ECR repository names to create (one per deployed service)."
  type        = list(string)
}

variable "tags" {
  type    = map(string)
  default = {}
}

resource "aws_ecr_repository" "this" {
  for_each = toset(var.repository_names)

  name                 = each.value
  image_tag_mutability = "IMMUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  # Force-deletable so `terraform destroy` doesn't get stuck on a repo that
  # still has images pushed to it during the exercise.
  force_delete = true

  tags = var.tags
}

# Keep repositories from growing unbounded during iterative pushes; expires
# untagged images after 3 days and keeps only the last 10 tagged images.
resource "aws_ecr_lifecycle_policy" "this" {
  for_each   = aws_ecr_repository.this
  repository = each.value.name

  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Expire untagged images after 3 days"
        selection = {
          tagStatus   = "untagged"
          countType   = "sinceImagePushed"
          countUnit   = "days"
          countNumber = 3
        }
        action = { type = "expire" }
      },
      {
        rulePriority = 2
        description  = "Keep only the last 10 tagged images"
        selection = {
          tagStatus     = "tagged"
          tagPrefixList = ["v", "sha-"]
          countType     = "imageCountMoreThan"
          countNumber   = 10
        }
        action = { type = "expire" }
      }
    ]
  })
}

output "repository_urls" {
  value = { for name, repo in aws_ecr_repository.this : name => repo.repository_url }
}
