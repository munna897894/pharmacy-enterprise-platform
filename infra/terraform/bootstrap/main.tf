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

# Versioning is required for state recovery, but every historical state object
# is a full copy of the state. Even after credentials were removed from state
# (see modules/secrets/main.tf), noncurrent versions are the main place where
# old state could linger, so they are expired automatically rather than kept
# forever. This bounds retention; it does not replace the deliberate prune in
# scripts/aws-prune-state-history.sh when retiring the exercise.
resource "aws_s3_bucket_lifecycle_configuration" "tf_state" {
  bucket = aws_s3_bucket.tf_state.id

  rule {
    id     = "expire-noncurrent-state"
    status = "Enabled"

    filter {}

    noncurrent_version_expiration {
      noncurrent_days = var.state_history_retention_days
    }

    abort_incomplete_multipart_upload {
      days_after_initiation = 1
    }
  }

  depends_on = [aws_s3_bucket_versioning.tf_state]
}

resource "aws_s3_bucket_public_access_block" "tf_state" {
  bucket = aws_s3_bucket.tf_state.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}
