#!/usr/bin/env sh
# Fast, no-auth-required smoke test: confirms every service container is up and
# answering its Actuator health endpoint, and that the gateway can route to
# auth-service's public JWKS endpoint. Intended to run right after
# `docker compose up` and before the slower Newman E2E suite.
set -eu
base_url="${1:-http://localhost:8080}"

# name:port:container triples for every business service (internal ports,
# bypassing the gateway, since Actuator's /health is not proxied by
# api-gateway for every service). Container name prefix matches
# infra/compose/compose.yml's default project name ("compose").
compose_prefix="${COMPOSE_PROJECT_NAME:-compose}"
services="auth-service:8081 product-service:8082 customer-service:8083 pharmacy-service:8084 \
inventory-service:8085 prescription-service:8086 order-service:8087 payment-service:8088 \
notification-service:8089:8090 audit-service:8090:8091"

fail=0
for entry in $services; do
  name=$(echo "$entry" | cut -d: -f1)
  container_port=$(echo "$entry" | cut -d: -f2)
  # host_port defaults to container_port unless a third field overrides it
  # (notification-service/audit-service map to a different host port).
  if [ "$(echo "$entry" | tr -cd ':' | wc -c)" -eq 2 ]; then
    host_port=$(echo "$entry" | cut -d: -f3)
  else
    host_port="$container_port"
  fi
  url="http://localhost:${host_port}/actuator/health"
  status=$(curl -sS -o /dev/null -w "%{http_code}" --max-time 3 "$url" 2>/dev/null || echo "000")
  if [ "$status" != "200" ]; then
    # Host-side Docker port-forwarding can occasionally desync after a
    # container restart even though the service itself is healthy; fall back
    # to an in-container check via docker exec before declaring failure.
    status=$(docker exec "${compose_prefix}-${name}-1" curl -sS -o /dev/null -w "%{http_code}" --max-time 3 "http://localhost:${container_port}/actuator/health" 2>/dev/null || echo "000")
    [ "$status" = "200" ] && url="(docker exec, host port-forward was unresponsive)"
  fi
  if [ "$status" = "200" ]; then
    echo "OK   $name ($url)"
  else
    echo "FAIL $name ($url) -> HTTP $status"
    fail=1
  fi
done

echo "Checking gateway routing to auth-service JWKS endpoint..."
if curl -fsS "${base_url}/api/v1/auth/.well-known/jwks.json" >/dev/null; then
  echo "OK   api-gateway -> auth-service JWKS"
else
  echo "FAIL api-gateway -> auth-service JWKS"
  fail=1
fi

if [ "$fail" -ne 0 ]; then
  echo "Smoke test FAILED: one or more services are not healthy." >&2
  exit 1
fi
echo "Smoke test PASSED: all services healthy."
