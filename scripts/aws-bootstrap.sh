#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$ROOT_DIR/scripts/aws-common.sh"
terraform_command
aws_sandbox_guard

BOOTSTRAP_DIR="$ROOT_DIR/infra/terraform/bootstrap"
PLAN_FILE="$BOOTSTRAP_DIR/aws-bootstrap.tfplan"
PLAN_MARKER="$BOOTSTRAP_DIR/.aws-bootstrap-plan-created.tfplan"
: "${STATE_BUCKET:?Set STATE_BUCKET to a unique S3 bucket name containing the verified account ID.}"
: "${OWNER:?Set OWNER for the required resource tags.}"
if [[ "$STATE_BUCKET" != *"$EXPECTED_AWS_ACCOUNT_ID"* ]]; then
  echo "Refusing bootstrap: STATE_BUCKET must contain the verified account ID." >&2
  exit 1
fi
if [[ -e "$PLAN_FILE" || -e "$PLAN_MARKER" ]]; then
  echo "A bootstrap plan artifact already exists; refusing to overwrite it." >&2
  exit 1
fi

umask 077
trap 'rm -f "$PLAN_FILE" "$PLAN_MARKER"' EXIT
cd "$BOOTSTRAP_DIR"
terraform init -input=false
require_default_workspace
terraform plan \
  -input=false \
  -var="aws_region=$AWS_REGION" \
  -var="state_bucket_name=$STATE_BUCKET" \
  -var="owner=$OWNER" \
  -out="$PLAN_FILE"
printf '%s\n' "created-by-aws-bootstrap" >"$PLAN_MARKER"
echo
terraform show -no-color "$PLAN_FILE"
read -r -p "Type 'create state bucket $STATE_BUCKET in $EXPECTED_AWS_ACCOUNT_ID' to continue: " CONFIRM
if [[ "$CONFIRM" != "create state bucket $STATE_BUCKET in $EXPECTED_AWS_ACCOUNT_ID" ]]; then
  echo "Aborted."
  exit 1
fi

aws_sandbox_guard
terraform apply -input=false "$PLAN_FILE"
trap - EXIT
rm -f "$PLAN_FILE" "$PLAN_MARKER"
echo "State bucket bootstrap complete. Keep the local Terraform state safe; this bucket is not part of sandbox destroy."
