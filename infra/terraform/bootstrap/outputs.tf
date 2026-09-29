output "state_bucket_name" {
  description = "Name of the S3 bucket to use as the remote state backend for infra/terraform/envs/sandbox."
  value       = aws_s3_bucket.tf_state.id
}

output "state_bucket_arn" {
  value = aws_s3_bucket.tf_state.arn
}
