# Self-run guide: Docker Desktop Kubernetes and AWS sandbox

This guide shows how to deploy, test and stop the full platform on Docker
Desktop Kubernetes, and separately how to operate the temporary full-fleet AWS sandbox.

Architecture diagrams: [local Docker Desktop Kubernetes](architecture-local.md)
and [temporary AWS EKS sandbox](architecture-cloud.md).

## Deployment topology (independent environments)

| Where | What runs | Entry point |
|---|---|---|
| Local (Docker Desktop Kubernetes) | All 10 business services, API gateway, external mock and Redis in namespace `pharmacy`; MySQL and Kafka run natively on the host | `http://localhost:18080` via `kubectl port-forward` |
| AWS sandbox (EKS) | All 12 JVM workloads, Redis, single-broker Kafka and observability in EKS; ten isolated schemas on private RDS | Private port-forward by default; optional HTTPS-only, `/32`-restricted gateway ALB with an ACM certificate |

**These are independent deployments, not a hybrid application.** Each has
its own gateway, business services, Redis, database and Kafka; neither
environment calls the other. AWS resources are ephemeral and have not yet
been live-tested with the current credentials.

---

## Part A — Docker Desktop Kubernetes (full local platform)

### A1. Prerequisites

- Docker Desktop Kubernetes enabled, with enough memory for 12 JVM workloads
  plus Redis and host-installed MySQL/Kafka (start with at least 12 GB).
- `kubectl` connected to the `docker-desktop` context; Docker Desktop's
  Kubernetes must use the same local Docker image store as your `docker` CLI.
- Host-installed MySQL 8.4 (`brew install mysql@8.4` on macOS) and Kafka
  4.x in KRaft mode, Docker CLI, curl and Java 21/Maven Wrapper for local
  builds. MySQL and Kafka must not bind only to an address unavailable to
  Docker Desktop pods. The MySQL scripts use the explicit keg-only 8.4 binary,
  not whichever `mysql` happens to be first on `PATH`.
- `python3` (used by scripts for JSON parsing).
- Node.js + Newman: `npm install -g newman` (or let `scripts/newman.sh` use `npx`).

### A2. Check your context and prepare host dependencies

```bash
kubectl config current-context           # must say docker-desktop
docker context show                       # typically desktop-linux
if [ ! -f .env ]; then cp .env.example .env; fi
"$(brew --prefix mysql@8.4)/bin/mysql" --version
kafka-topics --version
```

If the old Compose fleet is still running, stop it before starting the
Kubernetes copy to free Docker Desktop memory. This preserves Compose volumes
and does not remove its data:

```bash
docker compose --env-file .env -f infra/compose/compose.yml \
  -f infra/compose/compose-observability.yml stop
```

Use the project-scoped native MySQL 8.4 instance on port `3308`, separate from
your existing host server on `3306` and the Compose server on `3307`. It binds
only to `127.0.0.1`; Docker Desktop's host gateway can reach it without making
it available on your external network. Initialize the ten isolated service
schemas and users before deploying the application:

Do not run the Homebrew default MySQL service for this guide; use the
project-scoped initialization/start scripts, which keep their state under the
gitignored `.local/` directory instead of Homebrew's default data directory.

```bash
sh scripts/local-mysql-init.sh
sh scripts/local-mysql-verify.sh
```

The local initialization defaults to the **same `.env.example` values** as the
local Kubernetes Secret below; rerunning it preserves data and checks that
each of the ten schema-scoped users can access only its own schema. MySQL's
random local root password is stored in `.local/mysql/admin.cnf` (owner-only).
Keep that file if you intend to retain the local database; it is never
committed or printed. To use custom DB passwords instead, add every
`*_DB_PASSWORD` (including `NOTIFICATION_DB_PASSWORD`) to your gitignored
`.env`, then run `K8S_ENV_FILE=.env sh scripts/local-mysql-init.sh` and create
the Kubernetes Secret with the **same** `K8S_ENV_FILE=.env`. The example's
`MYSQL_PORT=3307` is for legacy Compose; the Kubernetes chart uses `3308`.

In a separate terminal, run `sh scripts/local-kafka-start.sh` from the repository
root and leave it running. The script formats a **separate** single-node KRaft
data directory at `.local/kafka-data` on first use; it does not modify any
existing host broker or Compose broker. Kafka uses the host-only listener
`localhost:19092` and advertises `host.docker.internal:29092` to pods as set
in `infra/local/kafka/server.properties`. Both listeners bind only to host
loopback; Docker Desktop forwards pod-to-host connections to the external
listener. Keep this unencrypted local-only broker off public networks.
Initialize and verify event, retry and DLT topics:

```bash
sh scripts/local-kafka-init.sh
kafka-topics --bootstrap-server localhost:19092 --list
```

The pods use `host.docker.internal:3308` for MySQL and
`host.docker.internal:29092` for Kafka. A running host Kafka with only a
`localhost:19092` advertised endpoint is **not** sufficient for pods.
Do not also start the legacy Compose application or backing services against
these same ports and data.

### A3. Build local images, create the local Secret and deploy

If you need startup traces, install the private observability release in A7
first, then return here to install the application chart.

```bash
set -e
for svc in api-gateway auth-service product-service customer-service \
  pharmacy-service inventory-service prescription-service order-service \
  payment-service notification-service audit-service external-mock-service; do
  docker build -t "pharmacy/$svc:local" -f "services/$svc/Dockerfile" .
done

kubectl apply -f k8s/namespace.yaml
# Generates a local Secret with only the ten demo DB passwords and fixture RSA keys.
# Use K8S_ENV_FILE=.env for separately provisioned custom local DB users.
K8S_ENV_FILE=.env.example sh scripts/k8s-local-secret.sh

./scripts/k8s-validate.sh
helm upgrade --install pharmacy infra/helm/pharmacy-platform \
  --kube-context docker-desktop \
  --namespace pharmacy -f infra/helm/pharmacy-platform/values-local.yaml \
  --wait --timeout 10m
for svc in api-gateway auth-service product-service customer-service \
  pharmacy-service inventory-service prescription-service order-service \
  payment-service notification-service audit-service external-mock-service redis; do
  kubectl -n pharmacy rollout status "deploy/$svc" --timeout=5m
done
kubectl -n pharmacy get deploy,svc,pods
```

Only use the checked-in RSA fixture keys for this local learning environment.
`k8s/pharmacy-secrets.yaml` is an older, ignored local sample with fixed
credentials and no RSA files; **do not apply it** and do not use
`kubectl apply -f k8s/` to install the stack. The Helm chart is the deployment
source; `scripts/k8s-validate.sh` checks its rendered resources. Docker
Desktop uses local images
tagged `pharmacy/<service>:local`; if a pod reports `ImagePullBackOff`, confirm
`docker image inspect pharmacy/<service>:local` works in Docker Desktop's image
store. If changing a service, rebuild its image, then run
`kubectl -n pharmacy rollout restart deploy/<service>` to load it.

### A4. Health checks and API access

In a **second terminal**, leave this command running:

```bash
kubectl -n pharmacy port-forward svc/api-gateway 18080:8080
```

In the first terminal:

```bash
kubectl -n pharmacy get pods                  # all application pods should be Ready
kubectl -n pharmacy get endpoints api-gateway auth-service product-service
curl -fsS http://localhost:18080/api/v1/auth/.well-known/jwks.json
```

Check each service with `kubectl -n pharmacy rollout status deploy/<service>
--timeout=5m` when troubleshooting. To inspect a health endpoint without
exposing it through the gateway, port-forward that service (for example
`kubectl -n pharmacy port-forward svc/order-service 18087:8087`, then curl
`http://localhost:18087/actuator/health/readiness`). `scripts/smoke-test.sh`
and `scripts/event-smoke.sh` are written for Compose, not Kubernetes.

### A5. Full automated E2E

Stop the port-forward from A4 if it is still running; the test script opens its
own temporary port-forward on port 18080.

```bash
sh scripts/local-k8s-test.sh
```

Expect the Newman summary to show `0` failed assertions. It covers happy path,
insufficient stock, payment failure, duplicate idempotency key, slow and
failing payment gateway, RBAC matrix, and negative 400/401/403/404/409/429
cases. The first run after a fresh start can be slower.

### A6. Manual walkthrough (curl)

For this separate walkthrough, restart the A4 gateway port-forward in its
second terminal.
Run these in one terminal session; each step reuses variables from the
previous ones. Test users and data are fictional.

```bash
BASE=http://localhost:18080
json() { python3 -c "import json,sys; print(json.load(sys.stdin)$1)"; }
uuid() { uuidgen | tr 'A-Z' 'a-z'; }     # services expect lowercase UUIDs
RUN=$(date +%s)
PW='Password123!'
MED_ID=550e8400-e29b-41d4-a716-446655440001   # seeded medication
```

**1. Register and log in a customer and a staff user (pharmacist + admin)**

```bash
curl -fsS -X POST $BASE/api/v1/auth/register -H 'Content-Type: application/json' \
  -d "{\"email\":\"cust_$RUN@example.test\",\"username\":\"cust_$RUN\",\"password\":\"$PW\",\"firstName\":\"Ada\",\"lastName\":\"Customer\",\"roles\":[\"CUSTOMER\"]}"
curl -fsS -X POST $BASE/api/v1/auth/register -H 'Content-Type: application/json' \
  -d "{\"email\":\"staff_$RUN@example.test\",\"username\":\"staff_$RUN\",\"password\":\"$PW\",\"firstName\":\"Sam\",\"lastName\":\"Staff\",\"roles\":[\"PHARMACIST\",\"ADMIN\"]}"

CUST_TOKEN=$(curl -fsS -X POST $BASE/api/v1/auth/login -H 'Content-Type: application/json' \
  -d "{\"username\":\"cust_$RUN\",\"password\":\"$PW\"}" | json '["accessToken"]')
STAFF_TOKEN=$(curl -fsS -X POST $BASE/api/v1/auth/login -H 'Content-Type: application/json' \
  -d "{\"username\":\"staff_$RUN\",\"password\":\"$PW\"}" | json '["accessToken"]')
```

Tokens stay in shell variables; don't paste them into tickets or chat.

**2. Customer profile (as customer)**

```bash
CUSTOMER_ID=$(curl -fsS -X POST $BASE/api/v1/customers \
  -H "Authorization: Bearer $CUST_TOKEN" -H 'Content-Type: application/json' \
  -d "{\"firstName\":\"Ada\",\"lastName\":\"Customer\",\"email\":\"cust_$RUN@example.test\",\"phone\":\"5555550100\",\"dateOfBirth\":\"1990-01-01\"}" \
  | json '["id"]'); echo "customer $CUSTOMER_ID"
```

**3. Pharmacy and stock (as staff)**

```bash
PHARMACY_ID=$(curl -fsS -X POST $BASE/api/v1/pharmacies \
  -H "Authorization: Bearer $STAFF_TOKEN" -H 'Content-Type: application/json' \
  -d "{\"name\":\"Walkthrough Pharmacy\",\"licenseNumber\":\"LIC-$RUN\",\"phone\":\"(555) 000-1111\",\"timezone\":\"America/New_York\",\"address\":{\"line1\":\"1 Test St\",\"city\":\"Testville\",\"state\":\"NY\",\"postalCode\":\"10001\",\"latitude\":40.0,\"longitude\":-73.0}}" \
  | json '["id"]'); echo "pharmacy $PHARMACY_ID"

curl -fsS -X POST $BASE/api/v1/inventory/stock-levels \
  -H "Authorization: Bearer $STAFF_TOKEN" -H 'Content-Type: application/json' \
  -d "{\"pharmacyId\":\"$PHARMACY_ID\",\"productId\":\"$MED_ID\",\"quantityOnHand\":100,\"reorderLevel\":10,\"reorderQuantity\":50}"
```

**4. Prescription (as staff), then activate it**

```bash
PRESCRIBED_AT=$(python3 -c 'import datetime as d; print((d.datetime.now(d.timezone.utc)+d.timedelta(days=2)).strftime("%Y-%m-%dT%H:%M:%SZ"))')
PRESCRIPTION_ID=$(curl -fsS -X POST $BASE/api/v1/prescriptions \
  -H "Authorization: Bearer $STAFF_TOKEN" -H 'Content-Type: application/json' \
  -d "{\"customerId\":\"$CUSTOMER_ID\",\"prescriberId\":\"$(uuid)\",\"prescribedAt\":\"$PRESCRIBED_AT\",\"expiresAt\":\"2035-01-01T00:00:00Z\",\"lines\":[{\"productId\":\"$MED_ID\",\"quantity\":2,\"instructions\":\"Take one tablet twice daily\"}]}" \
  | json '["id"]'); echo "prescription $PRESCRIPTION_ID"

curl -fsS -X POST $BASE/api/v1/prescriptions/$PRESCRIPTION_ID/activate -H "Authorization: Bearer $STAFF_TOKEN"
```

**5. Place an order (as customer) and watch the saga**

```bash
ORDER_KEY=$(uuid)
ORDER_ID=$(curl -fsS -X POST $BASE/api/v1/orders \
  -H "Authorization: Bearer $CUST_TOKEN" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $ORDER_KEY" \
  -d "{\"customerId\":\"$CUSTOMER_ID\",\"prescriptionId\":\"$PRESCRIPTION_ID\",\"pharmacyId\":\"$PHARMACY_ID\",\"currency\":\"USD\",\"total\":25.00,\"items\":[{\"medicationId\":\"$MED_ID\",\"quantity\":2,\"unitPrice\":12.50}]}" \
  | json '["id"]'); echo "order $ORDER_ID"

for i in $(seq 1 30); do
  S=$(curl -fsS $BASE/api/v1/orders/$ORDER_ID/status -H "Authorization: Bearer $CUST_TOKEN" | json '["status"]')
  echo "  $S"; case $S in CONFIRMED|CANCELLED*) break;; esac; sleep 2
done
```

Expected progression: `INVENTORY_PENDING` → `PAYMENT_PENDING` → `CONFIRMED`
(typically 10–15 s, since each of the three outbox hops relays every 5 s).

**6. Verify side effects**

```bash
# Payment (customer)
curl -fsS $BASE/api/v1/payments/by-order/$ORDER_ID -H "Authorization: Bearer $CUST_TOKEN" | json '["status"]'
# -> SUCCESS

# Notifications (customer; customerId filter is required)
curl -fsS "$BASE/api/v1/notifications?customerId=$CUSTOMER_ID" -H "Authorization: Bearer $CUST_TOKEN" \
  | python3 -c 'import json,sys; [print(n["type"], n["channel"], n["status"]) for n in json.load(sys.stdin)["content"]]'
# -> ORDER_CONFIRMATION EMAIL SENT / ORDER_CONFIRMATION IN_APP SENT  (email is simulated locally)

# Audit trail (admin)
curl -fsS $BASE/api/v1/audit/$ORDER_ID -H "Authorization: Bearer $STAFF_TOKEN" \
  | python3 -c 'import json,sys; [print(e["service"], e["action"], e["resourceType"]) for e in json.load(sys.stdin)["content"]]'
# -> order-service CREATE ORDER / inventory-service CREATE INVENTORY
```

**7. Failure path: payment rejected**

```bash
curl -fsS -X PUT "$BASE/api/v1/mock/payment?mode=REJECT" -H "Authorization: Bearer $STAFF_TOKEN"

ORDER2=$(curl -fsS -X POST $BASE/api/v1/orders \
  -H "Authorization: Bearer $CUST_TOKEN" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $(uuid)" \
  -d "{\"customerId\":\"$CUSTOMER_ID\",\"prescriptionId\":\"$PRESCRIPTION_ID\",\"pharmacyId\":\"$PHARMACY_ID\",\"currency\":\"USD\",\"total\":12.50,\"items\":[{\"medicationId\":\"$MED_ID\",\"quantity\":1,\"unitPrice\":12.50}]}" \
  | json '["id"]')
# Poll as in step 5 with $ORDER2 -> CANCELLED_PAYMENT (stock reservation is released)

curl -fsS -X POST $BASE/api/v1/mock/reset -H "Authorization: Bearer $STAFF_TOKEN"   # always reset
```

Other mock modes: `mode=DELAY&delayMs=2500` (slow but successful) and
`mode=ERROR` (payment gateway returns 500).

**8. Idempotency check**

Repeat step 5's `POST /api/v1/orders` with the *same* `$ORDER_KEY`
and payload; the response returns the original order ID instead of a new order.

### A7. Observability

Actuator health and Prometheus metrics remain internal to the cluster. For
example, port-forward `svc/order-service` to inspect
`http://localhost:18087/actuator/prometheus`. Search pod logs with
`kubectl -n pharmacy logs deploy/order-service --since=10m` and send a valid
`X-Correlation-ID` through the gateway to correlate requests.

The legacy Compose Grafana/Prometheus/Loki/Tempo overlay scrapes **Compose**
service DNS, not Kubernetes pods. Use the dedicated Kubernetes observability
release in `infra/helm/observability/` for the local cluster; the
application chart's local values export traces to its in-cluster Collector.
Access Grafana/Prometheus privately using the port-forward commands in
`docs/11-observability.md`; do not expose their Services to the internet.
Create a private Grafana admin Secret once before installing the observability
release; the helper generates a unique password without printing it and
preserves the Secret on reruns:

```bash
KUBE_CONTEXT=docker-desktop infra/helm/observability/scripts/ensure-grafana-secret.sh
helm dependency build infra/helm/observability
helm upgrade --install pharmacy-observability infra/helm/observability \
  --kube-context docker-desktop --namespace pharmacy-observability \
  -f infra/helm/observability/values-local.yaml --wait --timeout 10m
```

To sign in as `admin`, retrieve the password locally with
`kubectl --context docker-desktop -n pharmacy-observability get secret
grafana-admin -o jsonpath='{.data.admin-password}' | base64 --decode`; do not
paste it into logs or commit it. If this install predates the helper, your
existing `.local/grafana-admin.env` also holds the password.

### A8. Stop, restart, reset

```bash
# Pause just the Kubernetes application workloads; keeps MySQL/Kafka data.
kubectl -n pharmacy scale deploy/api-gateway deploy/auth-service deploy/product-service \
  deploy/customer-service deploy/pharmacy-service deploy/inventory-service \
  deploy/prescription-service deploy/order-service deploy/payment-service \
  deploy/notification-service deploy/audit-service deploy/external-mock-service \
  deploy/redis --replicas=0

# Rebuild and restart a single service (after scaling it back to 1 if paused).
docker build -t pharmacy/order-service:local -f services/order-service/Dockerfile .
kubectl -n pharmacy scale deploy/order-service --replicas=1
kubectl -n pharmacy rollout restart deploy/order-service
kubectl -n pharmacy rollout status deploy/order-service --timeout=5m
```

When finished, run `sh scripts/local-mysql-stop.sh` and interrupt the
`local-kafka-start.sh` terminal; this preserves their local data. On restart,
run `sh scripts/local-mysql-start.sh`,
`sh scripts/local-mysql-verify.sh`, then restart Kafka. Do not delete the
namespace or `.local/` unless you intend to discard local state.

### A9. Troubleshooting

| Symptom | Check |
|---|---|
| Pod not Ready | `kubectl -n pharmacy describe pod -l app=<service>` and `kubectl -n pharmacy logs deploy/<service> --tail=100`; check backing MySQL/Kafka health. |
| `ImagePullBackOff` | Build the image in Docker Desktop's image store; check the tag in `infra/helm/pharmacy-platform/values-local.yaml`. |
| Kafka consumers cannot connect | Check the native broker's `DOCKER_DESKTOP` listener advertises `host.docker.internal:29092` and its firewall permits Docker Desktop. `localhost:19092` alone is not reachable from pods. |
| MySQL access denied | The project-scoped MySQL instance must accept pod connections on port `3308`; users created by `scripts/local-mysql-init.sh` must match the generated Kubernetes Secret. |
| JWKS/auth pod fails to start | Check the two fixture-key files exist and are mounted from the generated Secret at `/etc/auth/`. |
| `curl` returns empty / 5xx on first call | Cold start; retry. |
| `400 Invalid ... ID format` | Use lowercase UUIDs (`uuidgen \| tr 'A-Z' 'a-z'`). |
| Order stays `INVENTORY_PENDING` | Check inventory stock, Kafka health, `sh scripts/local-kafka-init.sh` and `kubectl -n pharmacy logs deploy/inventory-service`. |
| `429 Too Many Requests` | Gateway rate limiting; slow down. |
| Newman fails after many restarts | Rerun once; see "Known environment quirks" in `docs/e2e-runbook.md`. |

---

## Part B — AWS sandbox (billable)

This is an **independent, disposable, non-production** copy of all 12 JVM
workloads, Redis, private single-broker Kafka and open-source observability.
It uses two public-subnet EKS workers, ten isolated RDS schemas/users and 12
ECR repositories. Gateway access defaults to a private port-forward; a
gateway-only HTTPS ALB is optional with an issued ACM certificate. No local host
dependencies are reused. Only synthetic data is permitted. EKS, EC2, RDS,
ALB, ECR, Secrets Manager, CloudWatch and storage can incur charges; budget
alerts are not spending caps. Review a time-boxed estimate with
`./scripts/aws-cost-estimate.sh 4` before planning; actual prices and usage
may differ. Destroy the environment at the end of each
session. Public-subnet workers and single-instance infrastructure are **not
production-standard**.

### B1. Prepare and review

Install Terraform, AWS CLI, Docker, `kubectl`, Helm, Python 3 and `curl`.
Configure AWS SSO and sign in. Set `EXPECTED_AWS_ACCOUNT_ID` independently
from an account you have confirmed, not from the current CLI profile. Set
`OPERATOR_CIDR` to your current public IPv4 `/32` for EKS API access. Public
registration currently accepts requested staff roles, so do not expose the
gateway to untrusted clients. Review
`docs/aws-plan-review.md` and `infra/terraform/README.md` for the full
threat, resource and cost inventory. Bootstrap the encrypted remote-state
bucket only if it does not already exist:

```bash
export AWS_PROFILE=pharmacy-sandbox AWS_REGION=us-east-1
aws sso login --profile pharmacy-sandbox
aws sts get-caller-identity
export EXPECTED_AWS_ACCOUNT_ID='<independently-verified-12-digit-account-id>'
export STATE_BUCKET='pharmacy-sandbox-tfstate-<account-id>'
export NAME_PREFIX=pharmacy-sbx
export OPERATOR_CIDR='<your-current-public-ip>/32'
# Optional HTTPS ALB: issued ACM cert, DNS name it covers and explicit /32 list.
# export GATEWAY_EXPOSURE=alb-https
# export GATEWAY_CERTIFICATE_ARN='<issued-acm-certificate-arn>'
# export GATEWAY_HOSTNAME='api.sandbox.example.com'
# export ALB_INGRESS_CIDRS='<your-current-public-ip>/32'
# First session only: export OWNER='<you>'; ./scripts/aws-bootstrap.sh
```

Copy `infra/terraform/envs/sandbox/terraform.tfvars.example` to the
gitignored `terraform.tfvars` in the same directory. Set `owner` and an
upcoming `expiration`. The expiration tag is informational and will **not**
destroy the environment automatically.

### B2. Validate, deploy and test

```bash
./scripts/aws-validate.sh
./scripts/aws-plan.sh
# Inspect the saved, sensitive plan locally before explicitly approving spend.
./scripts/aws-apply.sh
./scripts/aws-smoke-test.sh     # includes full Newman suite via private port-forward
# Set RUN_NEWMAN=0 to run only the short smoke checks.
```

The apply script provisions the cluster, builds and pushes all twelve
architecture-compatible images, creates credentials outside Terraform state,
runs a temporary database-bootstrap Job to create all ten schemas/users, and
deploys private Kafka, Redis, the complete JVM fleet and observability. No
manual database password retrieval or SQL commands are required. Read the
script's account, region, plan and cost review prompts before confirming.
Keep Terraform state and plans private; they contain sensitive material.

The default mode creates **no public ALB**. Authenticated smoke and Newman
requests use `kubectl port-forward` through the authenticated EKS API.
Optional `alb-https` requires an issued ACM certificate, matching hostname
and explicit `/32` allowlist; it does not support an HTTP listener. Even in
ALB mode, credentialed requests and Newman use the private port-forward; the
ALB is checked with an unauthenticated HTTPS request only. The EKS
observability release uses `infra/helm/observability/values-eks.yaml` plus a
bounded AWS overlay; see `docs/11-observability.md` for port-forward access;
monitoring Services must not be exposed through the ALB. AWS observability
storage is ephemeral and loses history on pod/cluster restart.

### B3. Destroy and audit leftovers

```bash
./scripts/aws-destroy.sh
./scripts/aws-post-destroy-check.sh
```

Destroy removes the observability release/namespace and the application
namespace (including the optional ALB) before tearing down Terraform
resources including RDS and the Kafka gp3 volume
without a final snapshot. Verify the inventory command completes cleanly
and inspect AWS Billing for remaining charges. The separately bootstrapped
state bucket is retained; remove it only when retiring the exercise and the
sandbox state is empty.

## Related docs

- `docs/e2e-runbook.md` — Newman details and fixed issues
- `docs/11-observability.md` — metrics, traces, logs, drills
- `infra/terraform/README.md`, `docs/aws-plan-review.md` — AWS design, cost and threat review
- `docs/known-gaps.md` — open follow-ups
