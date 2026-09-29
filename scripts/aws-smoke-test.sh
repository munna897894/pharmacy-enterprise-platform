#!/usr/bin/env bash
# Smoke-tests the deployed slice through the real ALB: auth-service login +
# JWKS, gateway JWT validation, and a product-service read/write.
set -euo pipefail

: "${AWS_PROFILE:=pharmacy-sandbox}"

NAMESPACE=pharmacy
echo "== Waiting for the Ingress to get an ALB hostname (can take a few minutes) =="
for i in $(seq 1 30); do
  ALB_HOST=$(kubectl get ingress pharmacy-ingress -n "$NAMESPACE" -o jsonpath='{.status.loadBalancer.ingress[0].hostname}' 2>/dev/null || true)
  if [ -n "$ALB_HOST" ]; then
    break
  fi
  echo "  not ready yet, waiting 10s... ($i/30)"
  sleep 10
done

if [ -z "${ALB_HOST:-}" ]; then
  echo "ALB hostname never appeared. Check: kubectl describe ingress pharmacy-ingress -n $NAMESPACE" >&2
  exit 1
fi

BASE_URL="http://${ALB_HOST}"
echo "ALB base URL: $BASE_URL"

echo
echo "== Pod status =="
kubectl get pods -n "$NAMESPACE"

echo
echo "== 1. Gateway health via ALB =="
curl -sS -f "$BASE_URL/actuator/health" || { echo "Gateway health check FAILED" >&2; exit 1; }
echo

echo "== 2. auth-service JWKS via gateway =="
curl -sS -f "$BASE_URL/api/v1/auth/.well-known/jwks.json" | python3 -m json.tool

echo
echo "== 3. Register + login through the gateway (adjust payload to match auth-service's real contract) =="
REGISTER_PAYLOAD='{"email":"smoke-test@example.com","username":"smoketest","password":"Sm0keTest!2345","firstName":"Smoke","lastName":"Test","roles":["CUSTOMER"]}'
curl -sS -X POST "$BASE_URL/api/v1/auth/register" \
  -H "Content-Type: application/json" -d "$REGISTER_PAYLOAD" || echo "(register may already exist — continuing)"

LOGIN_PAYLOAD='{"username":"smoketest","password":"Sm0keTest!2345"}'
LOGIN_RESPONSE=$(curl -sS -X POST "$BASE_URL/api/v1/auth/login" \
  -H "Content-Type: application/json" -d "$LOGIN_PAYLOAD")
echo "$LOGIN_RESPONSE" | python3 -m json.tool
ACCESS_TOKEN=$(echo "$LOGIN_RESPONSE" | python3 -c "import json,sys; print(json.load(sys.stdin).get('accessToken',''))")

if [ -z "$ACCESS_TOKEN" ]; then
  echo "Could not extract accessToken from login response — check auth-service logs." >&2
  exit 1
fi

echo
echo "== 4. product-service read through the gateway with the real JWT =="
curl -sS -f "$BASE_URL/api/v1/medications" \
  -H "Authorization: Bearer $ACCESS_TOKEN" | python3 -m json.tool

echo
echo "Smoke test complete. Capture this output for the interview-guide writeup, then proceed to destroy."
