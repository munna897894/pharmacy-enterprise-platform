#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$ROOT_DIR/scripts/aws-common.sh"
terraform_command
aws_sandbox_guard

ENV_DIR="$ROOT_DIR/infra/terraform/envs/sandbox"
PLAN_FILE="$ENV_DIR/aws-full-fleet.tfplan"
PLAN_MARKER="$ENV_DIR/.aws-full-fleet-plan-created.tfplan"
: "${STATE_BUCKET:?Set STATE_BUCKET to the bucket created by infra/terraform/bootstrap.}"
: "${OPERATOR_CIDR:?Set OPERATOR_CIDR to your current public IPv4 address in /32 notation.}"
# Gateway exposure is fail-closed. The default creates no public entry point:
# the gateway is reached with `kubectl port-forward` over the authenticated EKS
# API. alb-https is an explicit opt-in that needs an ISSUED ACM certificate, a
# hostname it covers, and a /32 allowlist (registration still accepts
# caller-supplied roles). Plain-HTTP public exposure is not offered at all:
# login passwords and JWTs must never cross the internet in plaintext.
GATEWAY_EXPOSURE="${GATEWAY_EXPOSURE:-port-forward}"
case "$GATEWAY_EXPOSURE" in
  port-forward)
    if [[ -n "${ALB_INGRESS_CIDRS:-}${GATEWAY_CERTIFICATE_ARN:-}${GATEWAY_HOSTNAME:-}" ]]; then
      echo "ALB_INGRESS_CIDRS/GATEWAY_CERTIFICATE_ARN/GATEWAY_HOSTNAME are set but GATEWAY_EXPOSURE=port-forward. Unset them or set GATEWAY_EXPOSURE=alb-https." >&2
      exit 1
    fi
    ALB_CIDR_JSON='[]'
    GATEWAY_CERTIFICATE_ARN=""
    GATEWAY_HOSTNAME=""
    ;;
  alb-https)
    : "${ALB_INGRESS_CIDRS:?alb-https requires ALB_INGRESS_CIDRS: comma-separated /32 addresses allowed to reach the HTTPS gateway.}"
    : "${GATEWAY_CERTIFICATE_ARN:?alb-https requires GATEWAY_CERTIFICATE_ARN: an ISSUED ACM certificate in the sandbox region.}"
    : "${GATEWAY_HOSTNAME:?alb-https requires GATEWAY_HOSTNAME: the DNS name covered by that certificate.}"
    ALB_CIDR_JSON=$(python3 "$ROOT_DIR/scripts/aws-parse-allowlist.py" "$ALB_INGRESS_CIDRS")
    bash "$ROOT_DIR/scripts/aws-check-gateway-certificate.sh" "$GATEWAY_CERTIFICATE_ARN" "$GATEWAY_HOSTNAME"
    ;;
  *)
    echo "GATEWAY_EXPOSURE must be port-forward (default) or alb-https. Plain HTTP exposure is not supported." >&2
    exit 1
    ;;
esac
if [[ ! -f "$ENV_DIR/terraform.tfvars" ]]; then
  echo "Missing $ENV_DIR/terraform.tfvars. Copy the example and set owner and expiration; pass operator_cidr through OPERATOR_CIDR." >&2
  exit 1
fi
if [[ -e "$PLAN_FILE" || -e "$PLAN_MARKER" ]]; then
  echo "A full-fleet plan artifact already exists at $PLAN_FILE. Refusing to overwrite or reuse it." >&2
  exit 1
fi

umask 077
trap 'rm -f "$PLAN_FILE" "$PLAN_MARKER"' EXIT
# Fail closed on extended-support cost before doing any planning work.
bash "$ROOT_DIR/scripts/aws-check-eks-version.sh" "${KUBERNETES_VERSION:-1.35}"

cd "$ENV_DIR"
terraform init -input=false -backend-config="bucket=$STATE_BUCKET" -reconfigure
require_default_workspace
terraform plan \
  -input=false \
  -var="aws_profile=$AWS_PROFILE" \
  -var="aws_region=$AWS_REGION" \
  -var="name_prefix=$NAME_PREFIX" \
  -var="operator_cidr=$OPERATOR_CIDR" \
  -var="kubernetes_version=${KUBERNETES_VERSION:-1.35}" \
  -var="gateway_exposure=$GATEWAY_EXPOSURE" \
  -var="gateway_certificate_arn=$GATEWAY_CERTIFICATE_ARN" \
  -var="gateway_hostname=$GATEWAY_HOSTNAME" \
  -var='alb_ingress_cidrs='"$ALB_CIDR_JSON" \
  -var="node_architecture=${NODE_ARCH:-arm64}" \
  -var="node_instance_type=${NODE_INSTANCE_TYPE:-t4g.large}" \
  -var="node_desired_size=2" \
  -var="node_max_size=2" \
  -out="$PLAN_FILE"
printf '%s\n' "created-by-aws-plan" >"$PLAN_MARKER"
trap - EXIT

echo
GATEWAY_EXPOSURE="$GATEWAY_EXPOSURE" bash "$ROOT_DIR/scripts/aws-cost-estimate.sh" "${SESSION_HOURS:-4}"
echo
echo "Saved a mode-restricted plan to $PLAN_FILE. It contains sensitive Terraform values; do not copy or upload it."
echo "Review it with 'terraform show $PLAN_FILE', then run scripts/aws-apply.sh. The apply script deletes the plan afterward."
