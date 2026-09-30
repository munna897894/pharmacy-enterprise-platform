#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$ROOT_DIR/scripts/aws-common.sh"
terraform_command
aws_sandbox_guard

ENV_DIR="$ROOT_DIR/infra/terraform/envs/sandbox"
: "${STATE_BUCKET:?Set STATE_BUCKET to the bucket created by infra/terraform/bootstrap.}"
: "${OPERATOR_CIDR:?Set OPERATOR_CIDR to a valid public IPv4 /32 CIDR.}"
cd "$ENV_DIR"
terraform init -input=false -backend-config="bucket=$STATE_BUCKET" -reconfigure
require_default_workspace

# Destroy must pass the same gateway mode the stack was applied with so the
# variable validations accept it. Read it from state (non-secret outputs);
# an empty/absent state falls back to the port-forward default.
GATEWAY_EXPOSURE=$(terraform output -raw gateway_exposure 2>/dev/null || true)
GATEWAY_EXPOSURE="${GATEWAY_EXPOSURE:-port-forward}"
case "$GATEWAY_EXPOSURE" in
  port-forward)
    ALB_CIDR_JSON='[]'
    GATEWAY_CERTIFICATE_ARN=""
    GATEWAY_HOSTNAME=""
    ;;
  alb-https)
    : "${ALB_INGRESS_CIDRS:?This stack was applied with alb-https; set ALB_INGRESS_CIDRS to the same /32 list to destroy it.}"
    ALB_CIDR_JSON=$(python3 "$ROOT_DIR/scripts/aws-parse-allowlist.py" "$ALB_INGRESS_CIDRS")
    GATEWAY_CERTIFICATE_ARN=$(terraform output -raw gateway_certificate_arn)
    GATEWAY_HOSTNAME=$(terraform output -raw gateway_hostname)
    ;;
  *)
    echo "Unrecognised gateway_exposure in state; refusing to guess destroy inputs." >&2
    exit 1
    ;;
esac

CLUSTER_NAME=$(terraform output -raw eks_cluster_name 2>/dev/null || true)
if [[ -n "$CLUSTER_NAME" ]]; then
  select_sandbox_kube_context "$CLUSTER_NAME"
fi

echo
echo "Terraform workspace: default"
echo "AWS profile: $AWS_PROFILE / account: $EXPECTED_AWS_ACCOUNT_ID / region: $AWS_REGION"
echo "State bucket: $STATE_BUCKET"
echo "Resources in Terraform state:"
terraform state list
echo
echo "RDS is non-production and will be destroyed with no final snapshot."
read -r -p "Type 'destroy $EXPECTED_AWS_ACCOUNT_ID $AWS_REGION pharmacy-sandbox' to continue: " CONFIRM
if [[ "$CONFIRM" != "destroy $EXPECTED_AWS_ACCOUNT_ID $AWS_REGION pharmacy-sandbox" ]]; then
  echo "Aborted."
  exit 1
fi
read -r -p "Confirm RDS deletion without a final snapshot [yes/NO]: " RDS_CONFIRM
if [[ "$RDS_CONFIRM" != "yes" ]]; then
  echo "Aborted."
  exit 1
fi

aws_sandbox_guard
if [[ -n "$CLUSTER_NAME" ]]; then
  # Release EBS-backed claims before the namespace goes away, while the CSI
  # controller is still running and able to delete the underlying volumes.
  release_persistent_volumes pharmacy

  # Observability goes first: its Kafka exporter lives in namespace pharmacy
  # and its release must be uninstalled while the cluster still exists, so
  # nothing (including EBS-backed or ENI-holding pods) outlives Terraform.
  if helm --kube-context "$AWS_KUBE_CONTEXT" status pharmacy-observability -n pharmacy-observability >/dev/null 2>&1; then
    helm --kube-context "$AWS_KUBE_CONTEXT" uninstall pharmacy-observability -n pharmacy-observability --wait --timeout 10m
  fi
  release_persistent_volumes pharmacy-observability
  kubectl --context "$AWS_KUBE_CONTEXT" delete namespace pharmacy-observability --ignore-not-found --wait=true --timeout=600s

  echo "Deleting the namespace (in alb-https mode this lets the ALB controller remove its gateway ALB)."
  kubectl --context "$AWS_KUBE_CONTEXT" delete namespace pharmacy --ignore-not-found --wait=true --timeout=600s
  if helm --kube-context "$AWS_KUBE_CONTEXT" status aws-load-balancer-controller -n kube-system >/dev/null 2>&1; then
    helm --kube-context "$AWS_KUBE_CONTEXT" uninstall aws-load-balancer-controller -n kube-system
  fi
fi
terraform destroy \
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
  -var="node_max_size=2"
echo "Destroy finished. Run scripts/aws-post-destroy-check.sh to verify cleanup."
