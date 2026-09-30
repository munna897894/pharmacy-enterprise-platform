#!/usr/bin/env bash
# End-to-end smoke test of the AWS sandbox gateway.
#
# ALL authenticated traffic (register, login, bearer calls, full Newman suite)
# goes to 127.0.0.1 through `kubectl port-forward`, tunnelled over the TLS,
# IAM-authenticated EKS API. It never goes to any load balancer.
# With GATEWAY_EXPOSURE=alb-https the ALB receives exactly one UNAUTHENTICATED
# HTTPS request (public JWKS) with certificate verification. There is no HTTP
# ALB. Payloads go via stdin and the token via a mode-600 header file, so
# neither appears in argv or output. RUN_NEWMAN=0 skips the full suite.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$ROOT_DIR/scripts/aws-common.sh"
aws_sandbox_guard

GATEWAY_EXPOSURE="${GATEWAY_EXPOSURE:-port-forward}"
LOCAL_PORT="${SMOKE_LOCAL_PORT:-18080}"
NAMESPACE=pharmacy
CLUSTER_NAME="${NAME_PREFIX}"

umask 077
WORK_DIR="$(mktemp -d)"
PORT_FORWARD_PID=""
cleanup() {
  if [[ -n "$PORT_FORWARD_PID" ]]; then
    kill "$PORT_FORWARD_PID" >/dev/null 2>&1 || true
    wait "$PORT_FORWARD_PID" 2>/dev/null || true
  fi
  rm -rf "$WORK_DIR"
}
trap cleanup EXIT

# Only loopback plain HTTP (the port-forward tunnel) or HTTPS is acceptable.
assert_safe_base_url() {
  case "$1" in
    https://*) ;;
    "http://127.0.0.1:"*) ;;
    *)
      echo "Refusing to send credentials to a non-HTTPS, non-loopback URL." >&2
      exit 1
      ;;
  esac
}

select_sandbox_kube_context "$CLUSTER_NAME"
kube() { kubectl --context "$AWS_KUBE_CONTEXT" "$@"; }

if [[ ! "$LOCAL_PORT" =~ ^[0-9]{4,5}$ ]]; then
  echo "SMOKE_LOCAL_PORT must be a 4-5 digit port." >&2
  exit 1
fi

# 1) Optional ALB check: UNAUTHENTICATED only (public JWKS), HTTPS only.
#    No password, registration payload or bearer token is ever sent to a
#    load balancer, whatever its listener or allowlist.
case "$GATEWAY_EXPOSURE" in
  port-forward)
    if kube get ingress pharmacy-ingress -n "$NAMESPACE" >/dev/null 2>&1; then
      echo "An Ingress exists although GATEWAY_EXPOSURE=port-forward; the cluster does not match the expected mode." >&2
      exit 1
    fi
    ;;
  alb-https)
    : "${GATEWAY_HOSTNAME:?Set GATEWAY_HOSTNAME to the HTTPS hostname covered by the ACM certificate.}"
    if [[ ! "$GATEWAY_HOSTNAME" =~ ^([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,63}$ ]]; then
      echo "GATEWAY_HOSTNAME must be a lowercase FQDN." >&2
      exit 1
    fi
    LISTEN_PORTS=$(kube get ingress pharmacy-ingress -n "$NAMESPACE" \
      -o jsonpath='{.metadata.annotations.alb\.ingress\.kubernetes\.io/listen-ports}')
    if [[ "$LISTEN_PORTS" != '[{"HTTPS": 443}]' ]]; then
      echo "Ingress listeners are not exactly HTTPS:443; refusing to contact the ALB." >&2
      exit 1
    fi
    ALB_HOST=""
    for i in $(seq 1 30); do
      ALB_HOST=$(kube get ingress pharmacy-ingress -n "$NAMESPACE" \
        -o jsonpath='{.status.loadBalancer.ingress[0].hostname}' 2>/dev/null || true)
      [[ -n "$ALB_HOST" ]] && break
      echo "  waiting for the HTTPS ALB ($i/30)"
      sleep 10
    done
    [[ -n "$ALB_HOST" ]] || { echo "ALB hostname never appeared." >&2; exit 1; }
    ALB_URL="https://${GATEWAY_HOSTNAME}"
    assert_safe_base_url "$ALB_URL"
    echo "== Unauthenticated HTTPS check through the ALB (no credentials sent) =="
    curl -sS --max-time 20 --proto =https --tlsv1.2 \
      --connect-to "${GATEWAY_HOSTNAME}:443:${ALB_HOST}:443" \
      -f -o /dev/null "$ALB_URL/api/v1/auth/.well-known/jwks.json"
    ;;
  *)
    echo "GATEWAY_EXPOSURE must be port-forward or alb-https." >&2
    exit 1
    ;;
esac

# 2) All authenticated traffic: private loopback tunnel through the
#    IAM-authenticated, TLS EKS API, in every exposure mode.
echo "== Tunnelling to api-gateway through the authenticated EKS API (127.0.0.1:$LOCAL_PORT) =="
kube port-forward --address 127.0.0.1 -n "$NAMESPACE" svc/api-gateway "$LOCAL_PORT:8080" \
  >"$WORK_DIR/port-forward.log" 2>&1 &
PORT_FORWARD_PID=$!
BASE_URL="http://127.0.0.1:${LOCAL_PORT}"
case "$BASE_URL" in
  "http://127.0.0.1:"*) ;;
  *) echo "Authenticated calls must use the loopback tunnel." >&2; exit 1 ;;
esac
CURL=(curl -sS --max-time 20 --proto =http)

echo "Authenticated base URL (private tunnel): $BASE_URL"
kube get pods -n "$NAMESPACE" -o wide

echo "== Waiting for the gateway-to-auth-service route =="
ready=0
for i in $(seq 1 30); do
  if ! kill -0 "$PORT_FORWARD_PID" 2>/dev/null; then
    echo "kubectl port-forward exited unexpectedly:" >&2
    cat "$WORK_DIR/port-forward.log" >&2
    exit 1
  fi
  if "${CURL[@]}" -f -o /dev/null "$BASE_URL/api/v1/auth/.well-known/jwks.json" 2>/dev/null; then
    ready=1
    break
  fi
  echo "  not ready yet ($i/30)"
  sleep 5
done
if [[ "$ready" != 1 ]]; then
  echo "Gateway never answered the JWKS route." >&2
  exit 1
fi

# A fresh synthetic identity per run: nothing reusable is committed and no
# real data is involved. The CUSTOMER role is requested explicitly; the
# registration endpoint's acceptance of privileged roles is a known risk that
# is why the gateway is never exposed broadly (docs/aws-plan-review.md).
python3 - "$WORK_DIR" <<'PY'
import json
import pathlib
import secrets
import sys

work = pathlib.Path(sys.argv[1])
suffix = secrets.token_hex(6)
password = "Sm0ke!" + secrets.token_urlsafe(18)
username = f"smoke_{suffix}"
(work / "register.json").write_text(json.dumps({
    "email": f"smoke-{suffix}@example.com",
    "username": username,
    "password": password,
    "firstName": "Smoke",
    "lastName": "Test",
    "roles": ["CUSTOMER"],
}))
(work / "login.json").write_text(json.dumps({"username": username, "password": password}))
PY

echo "== Register a synthetic smoke-test account =="
REGISTER_STATUS=$("${CURL[@]}" -o /dev/null -w '%{http_code}' -X POST "$BASE_URL/api/v1/auth/register" \
  -H "Content-Type: application/json" --data-binary @- <"$WORK_DIR/register.json")
rm -f "$WORK_DIR/register.json"
if [[ "$REGISTER_STATUS" != 2* ]]; then
  echo "Synthetic account registration returned HTTP $REGISTER_STATUS." >&2
  exit 1
fi

echo "== Login and validate a product read (token is never printed) =="
"${CURL[@]}" -f -o "$WORK_DIR/login-response.json" -X POST "$BASE_URL/api/v1/auth/login" \
  -H "Content-Type: application/json" --data-binary @- <"$WORK_DIR/login.json"
rm -f "$WORK_DIR/login.json"
python3 - "$WORK_DIR" <<'PY'
import json
import pathlib
import sys

work = pathlib.Path(sys.argv[1])
response = work / "login-response.json"
token = json.loads(response.read_text()).get("accessToken", "")
response.unlink()
if not token or any(ch in token for ch in "\r\n"):
    raise SystemExit("Login response did not contain a usable access token.")
(work / "auth-header").write_text(f"Authorization: Bearer {token}\n")
PY

"${CURL[@]}" -f -o /dev/null -H @"$WORK_DIR/auth-header" "$BASE_URL/api/v1/medications"
rm -f "$WORK_DIR/auth-header"

# Full Newman suite: on by default, always through the loopback tunnel.
if [[ "${RUN_NEWMAN:-1}" == 1 ]]; then
  echo "== Newman full suite through the private port-forward tunnel =="
  (cd "$ROOT_DIR" && ./scripts/newman.sh --env-var "baseUrl=$BASE_URL")
fi
echo "Smoke checks passed: authenticated traffic via private tunnel; ALB (if any) checked unauthenticated over HTTPS only."
