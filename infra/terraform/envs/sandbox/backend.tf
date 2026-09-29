# Remote state backend, per docs/aws-plan-review.md section 8.
# The bucket referenced here must already exist — created once via
# infra/terraform/bootstrap (see infra/terraform/bootstrap/README.md).
#
# No DynamoDB lock table: single-operator, sequential (never concurrent)
# apply/destroy for this temporary learning exercise.
#
# Values below are placeholders — override at `terraform init` time with:
#   terraform init -backend-config="bucket=<your-bucket-name>"
# so the actual bucket name (which contains your AWS account ID) is never
# hardcoded/committed here.

terraform {
  backend "s3" {
    key     = "pharmacy-sandbox/terraform.tfstate"
    region  = "us-east-1"
    encrypt = true
    # bucket = "<supplied at init time via -backend-config>"
  }
}
