#!/usr/bin/env bash
# Plan-only: shows what Terraform WOULD do. Never applies. Requires
# terraform.tfvars to already exist in infra/terraform/envs/sandbox (copy
# from terraform.tfvars.example) and AWS SSO login to be active.
set -euo pipefail

if [ -x "$HOME/bin/terraform" ]; then
  export PATH="$HOME/bin:$PATH"
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_DIR="$ROOT_DIR/infra/terraform/envs/sandbox"

: "${AWS_PROFILE:=pharmacy-sandbox}"
: "${STATE_BUCKET:?Set STATE_BUCKET to the bucket name output by infra/terraform/bootstrap}"

echo "Using AWS profile: $AWS_PROFILE"
echo "Confirming caller identity (must match your sandbox account before any plan/apply):"
aws sts get-caller-identity --profile "$AWS_PROFILE"

cd "$ENV_DIR"
terraform init -input=false -backend-config="bucket=$STATE_BUCKET" -reconfigure
terraform plan -input=false -out=tfplan
echo
echo "Plan saved to $ENV_DIR/tfplan. Review it carefully, then run:"
echo "  STATE_BUCKET=$STATE_BUCKET ./scripts/aws-apply.sh"
