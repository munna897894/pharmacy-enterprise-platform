provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      project     = var.project
      owner       = var.owner
      environment = var.environment
      cost-center = var.cost_center
      purpose     = "terraform-remote-state"
    }
  }
}

# One-off bootstrap: S3 bucket to hold Terraform remote state for the main
# sandbox environment. No DynamoDB lock table is created — this is a
# single-operator, sequential (never concurrent) learning exercise, as
# documented in docs/aws-plan-review.md section 8.
resource "aws_s3_bucket" "tf_state" {
  bucket = var.state_bucket_name

  # Prevent accidental `terraform destroy` of the bucket that holds the main
  # environment's state. Remove this manually (and only after confirming the
  # main environment's state is empty / fully destroyed) at final cleanup.
  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_s3_bucket_versioning" "tf_state" {
  bucket = aws_s3_bucket.tf_state.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "tf_state" {
  bucket = aws_s3_bucket.tf_state.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "aws:kms"
    }
    bucket_key_enabled = true
  }
}

resource "aws_s3_bucket_public_access_block" "tf_state" {
  bucket = aws_s3_bucket.tf_state.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}
