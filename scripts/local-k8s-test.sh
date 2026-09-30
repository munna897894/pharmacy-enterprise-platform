#!/usr/bin/env sh
set -eu

if [ "$(kubectl config current-context)" != "docker-desktop" ]; then
  echo "Expected Docker Desktop Kubernetes context." >&2
  exit 1
fi

for name in redis external-mock-service api-gateway auth-service product-service \
  customer-service pharmacy-service inventory-service prescription-service \
  order-service payment-service notification-service audit-service; do
  kubectl -n pharmacy rollout status "deployment/$name" --timeout=10m
done

sh scripts/local-kafka-init.sh >/dev/null

if curl --fail --silent --max-time 1 \
  http://localhost:18080/api/v1/auth/.well-known/jwks.json >/dev/null 2>&1; then
  echo "Port 18080 is already serving the gateway; stop its port-forward before running this script." >&2
  exit 1
fi
kubectl -n pharmacy port-forward svc/api-gateway 18080:8080 >/dev/null 2>&1 &
forward_pid=$!
trap 'kill "$forward_pid" 2>/dev/null || true' EXIT INT TERM
for attempt in $(seq 1 30); do
  if curl --fail --silent --show-error \
    http://localhost:18080/api/v1/auth/.well-known/jwks.json >/dev/null 2>&1; then
    ./scripts/newman.sh --env-var baseUrl=http://localhost:18080
    exit 0
  fi
  if ! kill -0 "$forward_pid" 2>/dev/null; then
    echo "Gateway port-forward exited before becoming ready." >&2
    exit 1
  fi
  sleep 2
done
echo "Gateway JWKS endpoint did not become ready on localhost:18080." >&2
exit 1
