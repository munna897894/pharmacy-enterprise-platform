#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$ROOT_DIR/scripts/aws-common.sh"
terraform_command
aws_sandbox_guard

ENV_DIR="$ROOT_DIR/infra/terraform/envs/sandbox"
K8S_DIR="$ROOT_DIR/infra/k8s/sandbox"
PLAN_FILE="$ENV_DIR/aws-full-fleet.tfplan"
PLAN_MARKER="$ENV_DIR/.aws-full-fleet-plan-created.tfplan"
: "${STATE_BUCKET:?Set STATE_BUCKET to the bucket created by infra/terraform/bootstrap.}"
if [[ ! -f "$PLAN_FILE" || ! -f "$PLAN_MARKER" ]] || \
  [[ "$(cat "$PLAN_MARKER" 2>/dev/null || true)" != "created-by-aws-plan" ]]; then
  echo "No plan owned by scripts/aws-plan.sh was found. Run that script first; existing plan files are never reused." >&2
  exit 1
fi

umask 077
RENDER_DIR="$(mktemp -d)"
OUTPUTS_FILE="$(mktemp)"
KUBE_CONTEXT_READY=0
cleanup() {
  if [[ "$KUBE_CONTEXT_READY" == 1 ]] && kubectl --context "$AWS_KUBE_CONTEXT" get job database-bootstrap -n pharmacy >/dev/null 2>&1; then
    kubectl --context "$AWS_KUBE_CONTEXT" delete job database-bootstrap -n pharmacy --wait=false >/dev/null 2>&1 || true
  fi
  rm -rf "$RENDER_DIR"
  rm -f "$OUTPUTS_FILE"
  if [[ -f "$PLAN_MARKER" ]] && [[ "$(cat "$PLAN_MARKER" 2>/dev/null || true)" == "created-by-aws-plan" ]]; then
    rm -f "$PLAN_FILE" "$PLAN_MARKER"
  fi
}
trap cleanup EXIT

cd "$ENV_DIR"
terraform init -input=false -backend-config="bucket=$STATE_BUCKET" -reconfigure
require_default_workspace
echo "Reviewing the saved plan; Terraform keeps sensitive values redacted:"
terraform show -no-color "$PLAN_FILE"
echo
GATEWAY_EXPOSURE="${GATEWAY_EXPOSURE:-port-forward}" bash "$ROOT_DIR/scripts/aws-cost-estimate.sh" "${SESSION_HOURS:-4}"
echo
echo "Budgets alert only; they never stop spend. Destroy this environment when the session ends."
read -r -p "Type 'apply pharmacy sandbox' to apply this plan and create billable AWS resources: " CONFIRM
if [[ "$CONFIRM" != "apply pharmacy sandbox" ]]; then
  echo "Aborted."
  exit 1
fi

aws_sandbox_guard
terraform apply -input=false "$PLAN_FILE"
# Never write the full output set to disk: pipe it straight into an allowlist
# filter so only identifiers, endpoints, ARNs and tags are persisted. The
# filter fails closed if any allowlisted output is marked sensitive. Database
# and JWT secrets stay in Secrets Manager and are read at runtime by
# IRSA-scoped pods; they are never rendered, echoed or stored here.
terraform output -json \
  | python3 "$ROOT_DIR/scripts/aws-filter-outputs.py" >"$OUTPUTS_FILE"
# Terraform creates the Secrets Manager containers empty; the values are
# generated here so they never enter the versioned state bucket.
bash "$ROOT_DIR/scripts/aws-seed-secrets.sh" "$OUTPUTS_FILE"

IMAGE_TAG="sha-$(git -C "$ROOT_DIR" rev-parse --short=12 HEAD)-$(date +%Y%m%d%H%M%S)"
bash "$ROOT_DIR/scripts/aws-render-manifests.sh" \
  "$OUTPUTS_FILE" "$K8S_DIR" "$RENDER_DIR" "$AWS_REGION" "$NAME_PREFIX" "$IMAGE_TAG"

AWS_ACCOUNT_ID=$(aws sts get-caller-identity --profile "$AWS_PROFILE" --query Account --output text)
ECR_REGISTRY="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
echo "== Logging in to sandbox ECR =="
aws ecr get-login-password --region "$AWS_REGION" --profile "$AWS_PROFILE" \
  | docker login --username AWS --password-stdin "$ECR_REGISTRY" >/dev/null

# Read the architecture actually provisioned for the nodes rather than assuming
# the workstation's. An arm64 image on x86_64 nodes (or the reverse) crash-loops
# every pod with "exec format error", so the platform is always explicit.
NODE_ARCH=$(python3 - "$OUTPUTS_FILE" <<'PY'
import json
import pathlib
import sys
print(json.loads(pathlib.Path(sys.argv[1]).read_text())["node_architecture"]["value"])
PY
)
BUILD_PLATFORM="linux/${NODE_ARCH}"
HOST_ARCH=$(uname -m)
case "$HOST_ARCH" in
  arm64 | aarch64) HOST_ARCH="arm64" ;;
  x86_64 | amd64) HOST_ARCH="amd64" ;;
esac

echo "== Building and pushing all 12 JVM workload images ($BUILD_PLATFORM) =="
if [[ "$HOST_ARCH" != "$NODE_ARCH" ]]; then
  echo "NOTE: host is $HOST_ARCH and nodes are $NODE_ARCH; buildx will emulate." >&2
  echo "      Builds are correct but slower. Set node_architecture=$HOST_ARCH to build natively." >&2
  docker run --privileged --rm tonistiigi/binfmt --install "$NODE_ARCH" >/dev/null 2>&1 || true
fi

# A dedicated builder keeps cross-platform state out of the host's default
# builder, which may be shared with local development.
if ! docker buildx inspect pharmacy-sandbox >/dev/null 2>&1; then
  docker buildx create --name pharmacy-sandbox --driver docker-container >/dev/null
fi
for service in auth-service api-gateway product-service customer-service pharmacy-service \
  inventory-service prescription-service order-service payment-service notification-service \
  audit-service external-mock-service; do
  repository=$(python3 - "$OUTPUTS_FILE" "$NAME_PREFIX" "$service" <<'PY'
import json
import pathlib
import sys
data = json.loads(pathlib.Path(sys.argv[1]).read_text())
print(data["ecr_repository_urls"]["value"][f"{sys.argv[2]}-{sys.argv[3]}"])
PY
)
  docker buildx build \
    --builder pharmacy-sandbox \
    --platform "$BUILD_PLATFORM" \
    --provenance=false \
    -t "$repository:$IMAGE_TAG" \
    -f "$ROOT_DIR/services/$service/Dockerfile" \
    --push \
    "$ROOT_DIR"
  verify_image_architecture "$repository:$IMAGE_TAG" "$NODE_ARCH"
done

CLUSTER_NAME=$(python3 - "$OUTPUTS_FILE" <<'PY'
import json
import pathlib
import sys
print(json.loads(pathlib.Path(sys.argv[1]).read_text())["eks_cluster_name"]["value"])
PY
)
select_sandbox_kube_context "$CLUSTER_NAME"
KUBE_CONTEXT_READY=1
kubectl --context "$AWS_KUBE_CONTEXT" get nodes
verify_node_architecture "$NODE_ARCH"

echo "== Creating the namespace and IRSA service accounts =="
kubectl --context "$AWS_KUBE_CONTEXT" apply -f "$RENDER_DIR/00-service-accounts.yaml"
GATEWAY_EXPOSURE=$(output_value "$OUTPUTS_FILE" gateway_exposure)
case "$GATEWAY_EXPOSURE" in
  port-forward)
    echo "== Gateway exposure: port-forward (no public load balancer, no ALB controller) =="
    ;;
  alb-https)
    VPC_ID=$(output_value "$OUTPUTS_FILE" vpc_id)
    echo "== Installing the ALB controller under its dedicated IRSA role (HTTPS gateway opt-in) =="
    kubectl --context "$AWS_KUBE_CONTEXT" apply -f "$RENDER_DIR/04-alb-controller-service-account.yaml"
    helm upgrade --install aws-load-balancer-controller \
      --repo https://aws.github.io/eks-charts aws-load-balancer-controller \
      --version 1.8.1 \
      --namespace kube-system \
      --kube-context "$AWS_KUBE_CONTEXT" \
      --set clusterName="$CLUSTER_NAME" \
      --set serviceAccount.create=false \
      --set serviceAccount.name=aws-load-balancer-controller \
      --set region="$AWS_REGION" \
      --set vpcId="$VPC_ID"
    kubectl --context "$AWS_KUBE_CONTEXT" rollout status deployment/aws-load-balancer-controller -n kube-system --timeout=240s
    ;;
  *)
    echo "Unsupported gateway_exposure output; refusing to continue." >&2
    exit 1
    ;;
esac

echo "== Configuring dynamic block storage (gp3 via the EBS CSI driver) =="
# The addon is created by Terraform, but the controller Deployment must be
# Available before any PVC can bind, otherwise the Kafka StatefulSet below
# simply hangs Pending with no useful event.
kubectl --context "$AWS_KUBE_CONTEXT" rollout status deployment/ebs-csi-controller \
  -n kube-system --timeout=300s

# EKS ships a "gp2" class marked default whose in-tree provisioner no longer
# exists in this Kubernetes version. Leaving it default means any PVC that
# omits storageClassName silently never binds. Drop the annotation so gp3 wins.
if kubectl --context "$AWS_KUBE_CONTEXT" get storageclass gp2 >/dev/null 2>&1; then
  kubectl --context "$AWS_KUBE_CONTEXT" patch storageclass gp2 \
    -p '{"metadata":{"annotations":{"storageclass.kubernetes.io/is-default-class":"false"}}}' >/dev/null
fi
kubectl --context "$AWS_KUBE_CONTEXT" apply -f "$RENDER_DIR/09-storage.yaml"
verify_default_storage_class

echo "== Starting Redis and the private single-broker Kafka service =="
kubectl --context "$AWS_KUBE_CONTEXT" apply -f "$RENDER_DIR/00-redis.yaml"
kubectl --context "$AWS_KUBE_CONTEXT" apply -f "$RENDER_DIR/06-kafka.yaml"
kubectl --context "$AWS_KUBE_CONTEXT" rollout status deployment/redis -n pharmacy --timeout=180s
kubectl --context "$AWS_KUBE_CONTEXT" rollout status statefulset/kafka -n pharmacy --timeout=360s

echo "== Creating the 15 Kafka domain, retry and DLT topics (idempotent Job) =="
kubectl --context "$AWS_KUBE_CONTEXT" delete job kafka-topic-bootstrap -n pharmacy --ignore-not-found --wait=true
kubectl --context "$AWS_KUBE_CONTEXT" apply -f "$RENDER_DIR/06-kafka-topics.yaml"
if ! kubectl --context "$AWS_KUBE_CONTEXT" wait --for=condition=complete job/kafka-topic-bootstrap -n pharmacy --timeout=600s; then
  kubectl --context "$AWS_KUBE_CONTEXT" logs job/kafka-topic-bootstrap -n pharmacy --tail=50 || true
  echo "Kafka topic bootstrap failed; refusing to start the fleet." >&2
  exit 1
fi
kubectl --context "$AWS_KUBE_CONTEXT" logs job/kafka-topic-bootstrap -n pharmacy --tail=1
kubectl --context "$AWS_KUBE_CONTEXT" delete job kafka-topic-bootstrap -n pharmacy --wait=true

echo "== Creating the ten RDS schemas and isolated service users with a temporary IRSA Job =="
kubectl --context "$AWS_KUBE_CONTEXT" apply -f "$RENDER_DIR/05-db-bootstrap.yaml"
kubectl --context "$AWS_KUBE_CONTEXT" wait --for=condition=complete job/database-bootstrap -n pharmacy --timeout=600s
kubectl --context "$AWS_KUBE_CONTEXT" delete job database-bootstrap -n pharmacy --wait=true

echo "== Applying all 12 internal JVM workloads =="
for manifest in 01-auth-service.yaml 02-product-service.yaml 03-api-gateway.yaml \
  customer-service.yaml pharmacy-service.yaml inventory-service.yaml prescription-service.yaml \
  order-service.yaml payment-service.yaml notification-service.yaml audit-service.yaml \
  08-external-mock-service.yaml; do
  kubectl --context "$AWS_KUBE_CONTEXT" apply -f "$RENDER_DIR/$manifest"
done
for service in auth-service product-service api-gateway customer-service pharmacy-service \
  inventory-service prescription-service order-service payment-service notification-service \
  audit-service external-mock-service; do
  kubectl --context "$AWS_KUBE_CONTEXT" rollout status "deployment/$service" -n pharmacy --timeout=360s
done

echo "== Installing private observability (Grafana, Prometheus, Loki, Tempo, OTel, Alloy, Kafka exporter) =="
# Chart: infra/helm/observability with its EKS profile plus the AWS capacity
# overlay. All Services are ClusterIP; access is kubectl port-forward only.
# Installed after the fleet because the Kafka exporter runs in namespace
# pharmacy (allowed by the kafka-private NetworkPolicy).
OBS_NAMESPACE=pharmacy-observability
OBS_CHART="$ROOT_DIR/infra/helm/observability"
if ! compgen -G "$OBS_CHART/charts/*.tgz" >/dev/null; then
  helm dependency build "$OBS_CHART"
fi
kubectl --context "$AWS_KUBE_CONTEXT" create namespace "$OBS_NAMESPACE" \
  --dry-run=client -o yaml | kubectl --context "$AWS_KUBE_CONTEXT" apply -f - >/dev/null
# Create the Grafana admin Secret without the password ever touching argv,
# stdout or disk: the manifest is generated in-process and piped to kubectl.
# The chart's helper then verifies the Secret shape and leaves it unchanged.
if ! kubectl --context "$AWS_KUBE_CONTEXT" -n "$OBS_NAMESPACE" get secret grafana-admin >/dev/null 2>&1; then
  python3 - "$OBS_NAMESPACE" <<'PY' | kubectl --context "$AWS_KUBE_CONTEXT" apply -f - >/dev/null
import base64
import json
import secrets
import sys

encode = lambda value: base64.b64encode(value.encode()).decode()
print(json.dumps({
    "apiVersion": "v1",
    "kind": "Secret",
    "type": "Opaque",
    "metadata": {
        "name": "grafana-admin",
        "namespace": sys.argv[1],
        "labels": {"app.kubernetes.io/part-of": "pharmacy-observability"},
    },
    "data": {"admin-user": encode("admin"), "admin-password": encode(secrets.token_urlsafe(24))},
}))
PY
fi
HELPER_OUTPUT=$(KUBE_CONTEXT="$AWS_KUBE_CONTEXT" OBSERVABILITY_NAMESPACE="$OBS_NAMESPACE" \
  GRAFANA_ADMIN_ROTATE=false bash "$OBS_CHART/scripts/ensure-grafana-secret.sh")
printf '%s\n' "$HELPER_OUTPUT" | grep -v "base64 --decode" || true
unset HELPER_OUTPUT
kubectl --context "$AWS_KUBE_CONTEXT" -n "$OBS_NAMESPACE" get secret grafana-admin >/dev/null
helm upgrade --install pharmacy-observability "$OBS_CHART" \
  --kube-context "$AWS_KUBE_CONTEXT" \
  --namespace "$OBS_NAMESPACE" \
  -f "$OBS_CHART/values-eks.yaml" \
  -f "$K8S_DIR/observability-values-aws.yaml" \
  --wait --timeout 15m >/dev/null
echo "Observability ready (ClusterIP only). Grafana:"
echo "  kubectl --context $AWS_KUBE_CONTEXT -n $OBS_NAMESPACE port-forward --address 127.0.0.1 svc/pharmacy-observability-grafana 3001:80"
echo "  (admin password lives only in Secret $OBS_NAMESPACE/grafana-admin; it is never printed here)"

if [[ "$GATEWAY_EXPOSURE" == "alb-https" ]]; then
  echo "== Applying the only public entry point: HTTPS-only API gateway Ingress =="
  kubectl --context "$AWS_KUBE_CONTEXT" apply -f "$RENDER_DIR/04-ingress.yaml"
  echo "Point a DNS CNAME for $(output_value "$OUTPUTS_FILE" gateway_hostname) at the ALB hostname shown by:"
  echo "  kubectl --context $AWS_KUBE_CONTEXT get ingress pharmacy-ingress -n pharmacy"
else
  echo "No public entry point was created. Reach the gateway only through the authenticated EKS API:"
  echo "  kubectl --context $AWS_KUBE_CONTEXT -n pharmacy port-forward --address 127.0.0.1 svc/api-gateway 18080:8080"
fi
echo "AWS fleet apply complete. Run scripts/aws-smoke-test.sh, then scripts/aws-destroy.sh and scripts/aws-post-destroy-check.sh."
