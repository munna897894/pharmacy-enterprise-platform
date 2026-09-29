#!/usr/bin/env bash
# Applies the Terraform plan produced by aws-plan.sh (interactive confirmation
# — NEVER -auto-approve, per the hard safety rules in
# prompts/12-aws-terraform.md), then builds/pushes the 3 service images to
# ECR, points kubectl at the new cluster, substitutes real values into the
# Kubernetes manifests, and applies them.
#
# Every apply here is paired with scripts/aws-destroy.sh +
# scripts/aws-post-destroy-check.sh — do not run this without knowing how
# you will tear it back down.
set -euo pipefail

if [ -x "$HOME/bin/terraform" ]; then
  export PATH="$HOME/bin:$PATH"
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_DIR="$ROOT_DIR/infra/terraform/envs/sandbox"
K8S_DIR="$ROOT_DIR/infra/k8s/sandbox"

: "${AWS_PROFILE:=pharmacy-sandbox}"
: "${AWS_REGION:=us-east-1}"

echo "Using AWS profile: $AWS_PROFILE / region: $AWS_REGION"
aws sts get-caller-identity --profile "$AWS_PROFILE"

cd "$ENV_DIR"
if [ ! -f tfplan ]; then
  echo "No saved plan found. Run scripts/aws-plan.sh first." >&2
  exit 1
fi

echo
echo "About to APPLY the plan above (real, billable AWS resources)."
read -r -p "Type 'apply' to continue: " CONFIRM
if [ "$CONFIRM" != "apply" ]; then
  echo "Aborted."
  exit 1
fi

terraform apply -input=false tfplan
rm -f tfplan

echo "== Terraform outputs =="
terraform output -json > /tmp/pharmacy-sandbox-tf-outputs.json
cat /tmp/pharmacy-sandbox-tf-outputs.json

AWS_ACCOUNT_ID=$(aws sts get-caller-identity --profile "$AWS_PROFILE" --query Account --output text)
ECR_REGISTRY="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
AUTH_REPO=$(terraform output -json ecr_repository_urls | python3 -c "import json,sys; d=json.load(sys.stdin); print([v for k,v in d.items() if 'auth-service' in k][0])")
GATEWAY_REPO=$(terraform output -json ecr_repository_urls | python3 -c "import json,sys; d=json.load(sys.stdin); print([v for k,v in d.items() if 'api-gateway' in k][0])")
PRODUCT_REPO=$(terraform output -json ecr_repository_urls | python3 -c "import json,sys; d=json.load(sys.stdin); print([v for k,v in d.items() if 'product-service' in k][0])")

IMAGE_TAG="$(date +%Y%m%d%H%M%S)"

echo "== Logging in to ECR =="
aws ecr get-login-password --region "$AWS_REGION" --profile "$AWS_PROFILE" \
  | docker login --username AWS --password-stdin "$ECR_REGISTRY"

echo "== Building and pushing images (tag: $IMAGE_TAG) =="
for svc_repo in "auth-service:$AUTH_REPO" "api-gateway:$GATEWAY_REPO" "product-service:$PRODUCT_REPO"; do
  SVC="${svc_repo%%:*}"
  REPO="${svc_repo#*:}"
  docker build -t "$REPO:$IMAGE_TAG" -f "$ROOT_DIR/services/$SVC/Dockerfile" "$ROOT_DIR"
  docker push "$REPO:$IMAGE_TAG"
done

echo "== Pointing kubectl at the new cluster =="
eval "$(terraform output -raw configure_kubectl_command)"
kubectl get nodes

echo "== Waiting for the AWS Load Balancer Controller to become ready =="
kubectl rollout status deployment/aws-load-balancer-controller -n kube-system --timeout=180s

RDS_ENDPOINT=$(terraform output -raw rds_endpoint)
AUTH_DB_SECRET_ARN=$(terraform output -raw auth_db_secret_arn)
PRODUCT_DB_SECRET_ARN=$(terraform output -raw product_db_secret_arn)
JWT_SECRET_ARN=$(terraform output -raw jwt_keypair_secret_arn)

echo "== Rendering Kubernetes manifests with real values =="
RENDER_DIR="$(mktemp -d)"
cp "$K8S_DIR"/*.yaml "$RENDER_DIR"/

sed -i.bak \
  -e "s#__AUTH_SERVICE_IMAGE__#${AUTH_REPO}:${IMAGE_TAG}#g" \
  -e "s#__PRODUCT_SERVICE_IMAGE__#${PRODUCT_REPO}:${IMAGE_TAG}#g" \
  -e "s#__API_GATEWAY_IMAGE__#${GATEWAY_REPO}:${IMAGE_TAG}#g" \
  -e "s#__RDS_ENDPOINT__#${RDS_ENDPOINT}#g" \
  -e "s#__AWS_REGION__#${AWS_REGION}#g" \
  -e "s#__AUTH_DB_SECRET_ARN__#${AUTH_DB_SECRET_ARN}#g" \
  -e "s#__PRODUCT_DB_SECRET_ARN__#${PRODUCT_DB_SECRET_ARN}#g" \
  -e "s#__JWT_KEYPAIR_SECRET_ARN__#${JWT_SECRET_ARN}#g" \
  "$RENDER_DIR"/*.yaml

echo
echo "IMPORTANT — one-time manual step before applying manifests:"
echo "Create the per-service MySQL schemas/users on the new RDS instance"
echo "(master creds: terraform output -raw rds_master_password), e.g.:"
echo "  CREATE DATABASE auth_service; CREATE USER 'auth_user'@'%' IDENTIFIED BY '<from Secrets Manager>'; GRANT ALL ON auth_service.* TO 'auth_user'@'%';"
echo "  CREATE DATABASE pharmacy_product; CREATE USER 'product_user'@'%' IDENTIFIED BY '<from Secrets Manager>'; GRANT ALL ON pharmacy_product.* TO 'product_user'@'%';"
echo "(Run this from a machine/bastion with network access to the RDS instance's security group, or a temporary port-forward via an EKS pod.)"
read -r -p "Press Enter once the schemas/users exist to continue applying Kubernetes manifests..."

echo "== Applying Kubernetes manifests =="
kubectl apply -f "$RENDER_DIR/00-redis.yaml"
kubectl apply -f "$RENDER_DIR/01-auth-service.yaml"
kubectl apply -f "$RENDER_DIR/02-product-service.yaml"
kubectl apply -f "$RENDER_DIR/03-api-gateway.yaml"
kubectl apply -f "$RENDER_DIR/04-ingress.yaml"

rm -rf "$RENDER_DIR"

echo
echo "Apply complete. Run scripts/aws-smoke-test.sh next to validate the deployment."
echo "When done learning, run scripts/aws-destroy.sh followed by scripts/aws-post-destroy-check.sh."
