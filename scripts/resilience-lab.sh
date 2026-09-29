#!/usr/bin/env sh
# Local observability/resilience drills against the compose stack (Prompt 10).
#
#   ./scripts/resilience-lab.sh slow-payment [delayMs]   slow gateway -> find the slow span in Tempo
#   ./scripts/resilience-lab.sh hikari [durationSeconds] [workers]
#                                                         DB pool contention -> Hikari pending/acquire metrics
#
# Requires the compose stack + observability overlay, python3, and a Newman-exported environment
# (tokens and seeded IDs). The environment is created on first use by running the Postman suite.
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
GATEWAY_URL=${GATEWAY_URL:-http://localhost:8080}
ORDER_SERVICE_URL=${ORDER_SERVICE_URL:-http://localhost:8087}
PROMETHEUS_URL=${PROMETHEUS_URL:-http://localhost:9090}
GRAFANA_URL=${GRAFANA_URL:-http://localhost:3000}
LAB_ENV_FILE=${LAB_ENV_FILE:-${TMPDIR:-/tmp}/pharmacy-lab-environment.json}

if [ -f "$ROOT_DIR/.env" ]; then
  set -a
  # shellcheck disable=SC1091
  . "$ROOT_DIR/.env"
  set +a
fi

env_value() {
  python3 - "$LAB_ENV_FILE" "$1" <<'PY'
import json, sys
values = {v["key"]: v.get("value", "") for v in json.load(open(sys.argv[1]))["values"]}
print(values.get(sys.argv[2], ""))
PY
}

ensure_environment() {
  if [ ! -s "$LAB_ENV_FILE" ] || [ -n "$(find "$LAB_ENV_FILE" -mmin +10 2>/dev/null)" ]; then
    echo "Refreshing tokens and seed data via the Postman suite..."
    (cd "$ROOT_DIR" && ./scripts/newman.sh --silent --export-environment "$LAB_ENV_FILE" >/dev/null)
  fi
}

grafana_get() {
  path=$1
  shift
  curl -fsS -u "${GRAFANA_ADMIN_USER:?set in .env}:${GRAFANA_ADMIN_PASSWORD:?set in .env}" -G "$GRAFANA_URL$path" "$@"
}

place_order() {
  token=$(env_value accessToken)
  body=$(python3 - "$(env_value customerId)" "$(env_value prescriptionId)" "$(env_value pharmacyId)" "$(env_value medicationId)" <<'PY'
import json, sys
customer, prescription, pharmacy, medication = sys.argv[1:5]
print(json.dumps({
    "customerId": customer, "prescriptionId": prescription, "pharmacyId": pharmacy,
    "currency": "USD", "total": 12.50,
    "items": [{"medicationId": medication, "quantity": 1, "unitPrice": 12.50}],
}))
PY
)
  correlation_id=$(python3 -c 'import uuid; print(uuid.uuid4())')
  order_id=$(curl -fsS -X POST "$GATEWAY_URL/api/v1/orders" \
    -H "Authorization: Bearer $token" -H "Content-Type: application/json" \
    -H "Idempotency-Key: $(python3 -c 'import uuid; print(uuid.uuid4())')" \
    -H "X-Correlation-ID: $correlation_id" -d "$body" \
    | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])')
  echo "$order_id $correlation_id"
}

wait_for_terminal_status() {
  token=$(env_value accessToken)
  attempt=0
  while [ "$attempt" -lt 40 ]; do
    status=$(curl -fsS "$GATEWAY_URL/api/v1/orders/$1/status" -H "Authorization: Bearer $token" \
      | python3 -c 'import json,sys; print(json.load(sys.stdin)["status"])')
    case "$status" in
      CONFIRMED|CANCELLED*) echo "$status"; return 0 ;;
    esac
    attempt=$((attempt + 1))
    sleep 1
  done
  echo "TIMEOUT($status)"
}

trace_id_for_correlation() {
  end_ns="$(date +%s)000000000"
  start_ns="$(( $(date +%s) - 600 ))000000000"
  grafana_get "/api/datasources/proxy/uid/loki/loki/api/v1/query_range" \
    --data-urlencode "query={service_name=~\".+\"} |= \"$1\"" \
    --data-urlencode "start=$start_ns" --data-urlencode "end=$end_ns" --data-urlencode "limit=20" \
    | python3 -c '
import json, re, sys
for stream in json.load(sys.stdin)["data"]["result"]:
    for _, line in stream["values"]:
        match = re.search(r"\[[0-9a-f-]{36},([0-9a-f]{32}),", line)
        if match:
            print(match.group(1)); sys.exit(0)
'
}

slow_payment() {
  delay_ms=${1:-1500}
  ensure_environment
  staff_token=$(env_value staffAccessToken)
  echo "Setting mock payment gateway to DELAY ${delay_ms}ms"
  curl -fsS -X PUT "$GATEWAY_URL/api/v1/mock/payment?mode=DELAY&delayMs=$delay_ms" \
    -H "Authorization: Bearer $staff_token" >/dev/null
  trap 'curl -fsS -X POST "$GATEWAY_URL/api/v1/mock/reset" -H "Authorization: Bearer $staff_token" >/dev/null || true' EXIT
  set -- $(place_order)
  order_id=$1; correlation_id=$2
  echo "orderId=$order_id correlationId=$correlation_id"
  echo "terminal status: $(wait_for_terminal_status "$order_id")"
  sleep 10
  trace_id=$(trace_id_for_correlation "$correlation_id")
  if [ -z "$trace_id" ]; then
    echo "No log line carrying a trace ID for the correlation ID yet; search Loki for $correlation_id" >&2
    return 1
  fi
  echo "traceId=$trace_id  (Grafana > Explore > Tempo)"
  grafana_get "/api/datasources/proxy/uid/tempo/api/traces/$trace_id" | python3 -c '
import json, sys
spans = []
for batch in json.load(sys.stdin).get("batches", []):
    service = next(a["value"].get("stringValue") for a in batch["resource"]["attributes"] if a["key"] == "service.name")
    for scope in batch.get("scopeSpans", []):
        for span in scope["spans"]:
            duration_ms = (int(span["endTimeUnixNano"]) - int(span["startTimeUnixNano"])) / 1e6
            spans.append((duration_ms, service, span["name"]))
print(f"{len(spans)} spans across {len({s[1] for s in spans})} services; slowest:")
for duration_ms, service, name in sorted(spans, reverse=True)[:6]:
    print(f"  {duration_ms:8.1f} ms  {service:24} {name}")
'
}

hikari() {
  duration_s=${1:-45}
  parallelism=${2:-120}
  ensure_environment
  echo "Sustaining ${duration_s}s of reads with ${parallelism} workers directly on order-service (bypasses gateway rate limiting)"
  python3 - "$ORDER_SERVICE_URL/api/v1/orders/$(env_value orderId)" "$(env_value accessToken)" "$duration_s" "$parallelism" <<'PY'
import collections, sys, threading, time, urllib.error, urllib.request
url, token, duration, workers = sys.argv[1], sys.argv[2], float(sys.argv[3]), int(sys.argv[4])
deadline = time.monotonic() + duration
codes = collections.Counter()
lock = threading.Lock()
def worker():
    while time.monotonic() < deadline:
        request = urllib.request.Request(url, headers={"Authorization": f"Bearer {token}"})
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                code = response.status
        except urllib.error.HTTPError as error:
            code = error.code
        except Exception:
            code = "error"
        with lock:
            codes[code] += 1
threads = [threading.Thread(target=worker) for _ in range(workers)]
for thread in threads: thread.start()
for thread in threads: thread.join()
total = sum(codes.values())
print(f"  {total} requests ({total / duration:.0f} req/s), responses: {dict(codes)}")
PY
  sleep 16
  for query in \
    'max_over_time(hikaricp_connections_pending{service="order-service"}[2m])' \
    'max_over_time(hikaricp_connections_active{service="order-service"}[2m])' \
    'max_over_time(hikaricp_connections_acquire_seconds_max{service="order-service"}[2m])' \
    'hikaricp_connections_max{service="order-service"}'; do
    value=$(curl -fsS -G "$PROMETHEUS_URL/api/v1/query" --data-urlencode "query=$query" \
      | python3 -c 'import json,sys; r=json.load(sys.stdin)["data"]["result"]; print(max((float(x["value"][1]) for x in r), default=0))')
    echo "  $query = $value"
  done
}

case "${1:-}" in
  slow-payment) shift; slow_payment "$@" ;;
  hikari) shift; hikari "$@" ;;
  *) sed -n '2,9p' "$0"; exit 2 ;;
esac
