#!/usr/bin/env bash
# Tears down the sandbox exercise. Requires explicit interactive confirmation
# of account/region/workspace before destroying anything — per the hard
# safety rule "Never destroy a target until account, region, workspace and
# plan are verified."
set -euo pipefail

if [ -x "$HOME/bin/terraform" ]; then
  export PATH="$HOME/bin:$PATH"
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_DIR="$ROOT_DIR/infra/terraform/envs/sandbox"

: "${AWS_PROFILE:=pharmacy-sandbox}"
: "${AWS_REGION:=us-east-1}"
: "${STATE_BUCKET:?Set STATE_BUCKET to the bucket name output by infra/terraform/bootstrap}"

echo "== Verifying identity, account, and region before doing anything destructive =="
aws sts get-caller-identity --profile "$AWS_PROFILE"
echo "AWS_PROFILE=$AWS_PROFILE  AWS_REGION=$AWS_REGION"

cd "$ENV_DIR"
terraform init -input=false -backend-config="bucket=$STATE_BUCKET" -reconfigure
echo
echo "== Current Terraform workspace =="
terraform workspace show

echo
echo "== Resources currently tracked in state (review this list) =="
terraform state list

echo
echo "This will DESTROY all resources above in account $(aws sts get-caller-identity --profile "$AWS_PROFILE" --query Account --output text), region $AWS_REGION."
read -r -p "Type the exact text 'destroy pharmacy-sandbox' to continue: " CONFIRM
if [ "$CONFIRM" != "destroy pharmacy-sandbox" ]; then
  echo "Aborted."
  exit 1
fi

echo
echo "RDS deletion-protection/snapshot confirmation: this module sets"
echo "deletion_protection=false and skip_final_snapshot=true (no production"
echo "data — confirmed acceptable in docs/aws-plan-review.md section 9)."
read -r -p "Confirm you accept destroying RDS with no final snapshot [yes/NO]: " RDS_CONFIRM
if [ "$RDS_CONFIRM" != "yes" ]; then
  echo "Aborted."
  exit 1
fi

# Remove Kubernetes-managed load balancer objects first so the ALB controller
# cleanly deletes the ALB/target-groups it created (kubectl-managed, not
# Terraform-managed) before the underlying cluster/VPC disappear.
if kubectl get ingress pharmacy-ingress -n pharmacy >/dev/null 2>&1; then
  echo "== Deleting Kubernetes Ingress/Deployments so the ALB controller cleans up the ALB =="
  kubectl delete -f "$ROOT_DIR/infra/k8s/sandbox/04-ingress.yaml" --ignore-not-found
  sleep 20
  kubectl delete namespace pharmacy --ignore-not-found --timeout=120s
fi

terraform destroy -input=false
echo
echo "Terraform destroy complete. Now run scripts/aws-post-destroy-check.sh to verify no leftovers."
