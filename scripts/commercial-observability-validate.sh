#!/usr/bin/env bash
set -euo pipefail

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
TMP_DIR=$(mktemp -d)
CREATED_ENV=false

cleanup() {
  rm -rf "$TMP_DIR"
  if [ "$CREATED_ENV" = true ]; then
    rm -f "$ROOT/.env"
  fi
}
trap cleanup EXIT

require() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "ERROR: required command not found: $1" >&2
    exit 1
  }
}

require docker
require helm
require grep
require python3

if [ ! -f "$ROOT/.env" ]; then
  cp "$ROOT/.env.example" "$ROOT/.env"
  CREATED_ENV=true
fi

printf '%s' 'offline-placeholder-not-a-token' > "$TMP_DIR/dynatrace-token"
printf '%s' 'offline-placeholder-not-a-token' > "$TMP_DIR/splunk-token"
chmod 444 "$TMP_DIR/dynatrace-token" "$TMP_DIR/splunk-token"

echo "== Baseline Compose has no commercial integration =="
docker compose \
  -f "$ROOT/infra/compose/compose.yml" \
  -f "$ROOT/infra/compose/compose-observability.yml" \
  --env-file "$ROOT/.env" config > "$TMP_DIR/baseline-compose.yaml"
if grep -Eiq 'dynatrace|splunk' "$TMP_DIR/baseline-compose.yaml"; then
  echo "ERROR: baseline Compose unexpectedly contains a commercial integration" >&2
  exit 1
fi

echo "== Dynatrace Compose override renders =="
DYNATRACE_OTLP_ENDPOINT=https://example.invalid/api/v2/otlp \
DYNATRACE_API_TOKEN_FILE="$TMP_DIR/dynatrace-token" \
docker compose \
  -f "$ROOT/infra/compose/compose.yml" \
  -f "$ROOT/infra/compose/compose-observability.yml" \
  -f "$ROOT/infra/compose/compose-dynatrace-observability.yml" \
  --env-file "$ROOT/.env" config > "$TMP_DIR/dynatrace-compose.yaml"
grep -q 'otel-collector-dynatrace.yaml' "$TMP_DIR/dynatrace-compose.yaml"
grep -q 'target: dynatrace-api-token' "$TMP_DIR/dynatrace-compose.yaml"
grep -q '^DYNATRACE_API_TOKEN_FILE=../../.local/secrets/dynatrace-api-token$' \
  "$ROOT/.env.example"
grep -q '^SPLUNK_HEC_TOKEN_FILE=../../.local/secrets/splunk-hec-token$' \
  "$ROOT/.env.example"

echo "== Splunk Compose profile renders with loopback-only ports =="
SPLUNK_ADMIN_PASSWORD=offline-placeholder-password \
SPLUNK_HEC_TOKEN=offline-placeholder-not-a-token \
SPLUNK_HEC_TOKEN_FILE="$TMP_DIR/splunk-token" \
docker compose --profile splunk \
  -f "$ROOT/infra/compose/compose.yml" \
  -f "$ROOT/infra/compose/compose-observability.yml" \
  -f "$ROOT/infra/compose/compose-splunk-observability.yml" \
  --env-file "$ROOT/.env" config --format json > "$TMP_DIR/splunk-compose.json"
python3 - "$TMP_DIR/splunk-compose.json" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as stream:
    services = json.load(stream)["services"]
    splunk = services["splunk"]

ports = {
    (binding.get("host_ip"), int(binding["target"]), int(binding["published"]))
    for binding in splunk.get("ports", [])
}
expected = {("127.0.0.1", 8000, 8000), ("127.0.0.1", 8088, 8188)}
if ports != expected:
    raise SystemExit(f"unexpected Splunk port bindings: {sorted(ports)}")

applications = {
    "api-gateway", "auth-service", "product-service", "customer-service",
    "pharmacy-service", "inventory-service", "prescription-service",
    "order-service", "payment-service", "notification-service",
    "audit-service", "external-mock-service",
}
for name in applications:
    environment = services[name]["environment"]
    if environment.get("SPRING_PROFILES_ACTIVE") != "commercial":
        raise SystemExit(f"{name} does not enable structured commercial-test logging")
PY
test "$(grep -c '<springProfile name=\"local\">' \
  "$ROOT/services/api-gateway/src/main/resources/logback-spring.xml")" -eq 2
test "$(grep -c '<springProfile name=\"!local\">' \
  "$ROOT/services/api-gateway/src/main/resources/logback-spring.xml")" -eq 2

echo "== Dynatrace Collector configuration validates =="
docker run --rm \
  -e DYNATRACE_OTLP_ENDPOINT=https://example.invalid/api/v2/otlp \
  -v "$ROOT/infra/compose/otel-collector-dynatrace.yaml:/etc/otelcol/config.yaml:ro" \
  -v "$TMP_DIR/dynatrace-token:/run/secrets/dynatrace-api-token:ro" \
  otel/opentelemetry-collector-contrib:0.161.0 \
  validate --config=/etc/otelcol/config.yaml

echo "== Splunk Alloy configuration validates =="
docker run --rm \
  -e SPLUNK_HEC_ENDPOINT=https://example.invalid:8088 \
  -e SPLUNK_INDEX=pharmacy_nonprod \
  -e SPLUNK_SOURCETYPE=pharmacy:service:json \
  -v "$ROOT/infra/compose/otel-collector-splunk-local.alloy:/etc/alloy/config.alloy:ro" \
  -v "$TMP_DIR/splunk-token:/run/secrets/splunk_hec_token:ro" \
  grafana/alloy:v1.20.0 \
  validate --feature.community-components.enabled /etc/alloy/config.alloy

echo "== Helm dependencies and optional overlays validate =="
helm dependency build --skip-refresh "$ROOT/infra/helm/observability" >/dev/null
helm template pharmacy-observability "$ROOT/infra/helm/observability" \
  --namespace pharmacy-observability \
  -f "$ROOT/infra/helm/observability/values-eks.yaml" \
  > "$TMP_DIR/baseline-helm.yaml"
if grep -Eiq 'dynatrace|splunk' "$TMP_DIR/baseline-helm.yaml"; then
  echo "ERROR: baseline Helm render unexpectedly contains a commercial integration" >&2
  exit 1
fi

for vendor in dynatrace splunk; do
  helm lint "$ROOT/infra/helm/observability" \
    -f "$ROOT/infra/helm/observability/values-eks.yaml" \
    -f "$ROOT/infra/helm/observability/values-${vendor}.example.yaml" >/dev/null
  helm template pharmacy-observability "$ROOT/infra/helm/observability" \
    --namespace pharmacy-observability \
    -f "$ROOT/infra/helm/observability/values-eks.yaml" \
    -f "$ROOT/infra/helm/observability/values-${vendor}.example.yaml" \
    > "$TMP_DIR/${vendor}-helm.yaml"

  if grep -Eq '^kind: Ingress$|^[[:space:]]*type: (LoadBalancer|NodePort)$' \
      "$TMP_DIR/${vendor}-helm.yaml"; then
    echo "ERROR: ${vendor} overlay rendered a public Kubernetes endpoint" >&2
    exit 1
  fi
done

echo "== Dynatrace examples are pinned and injection remains opt-in =="
grep -q 'apiVersion: dynatrace.com/v1beta5' \
  "$ROOT/infra/helm/dynatrace-operator/dynakube.example.yaml"
grep -q 'observability.pharmacy/dynatrace-injection' \
  "$ROOT/infra/helm/dynatrace-operator/dynakube.example.yaml"

if [ "${VALIDATE_VENDOR_CHARTS:-false}" = true ]; then
  helm template dynatrace-operator \
    oci://public.ecr.aws/dynatrace/dynatrace-operator \
    --version 1.10.2 \
    --namespace dynatrace \
    -f "$ROOT/infra/helm/dynatrace-operator/values.example.yaml" \
    > "$TMP_DIR/dynatrace-operator.yaml"
fi

echo "== Obvious committed token patterns are absent =="
if grep -RInE \
    --exclude='*.tgz' \
    --exclude-dir='.git' \
    'dt0c01\.[A-Za-z0-9._-]{20,}|Splunk [A-Fa-f0-9-]{24,}' \
    "$ROOT/.env.example" "$ROOT/README.md" "$ROOT/docs" \
    "$ROOT/infra/compose" "$ROOT/infra/helm" "$ROOT/scripts"; then
  echo "ERROR: possible commercial-observability credential found" >&2
  exit 1
fi

echo "Commercial observability validation PASSED (no vendor endpoint contacted)."
