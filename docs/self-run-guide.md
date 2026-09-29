# Self-run guide: local stack and AWS sandbox

This guide shows how to start, test and stop the platform on your own machine.

## Deployment topology (hybrid)

| Where | What runs | Entry point |
|---|---|---|
| Local (Docker Compose) | All 11 business services + external-mock, Kafka, MySQL, Redis, Prometheus, Grafana, Loki, Tempo, Alloy, OTel Collector | `http://localhost:8080` |
| AWS sandbox (EKS) | **Only** auth-service, api-gateway, product-service + a Redis pod, RDS MySQL, ALB | ALB hostname printed by `scripts/aws-smoke-test.sh` |

The AWS slice is self-contained: its gateway routes only to auth and product,
with no Kafka and no calls back to local services. The order saga
(customer, pharmacy, inventory, prescription, order, payment, notification,
audit) runs only locally.

---

## Part A — Local stack

### A1. Prerequisites

- Docker Desktop with at least 8 GB RAM allocated.
- `python3` (used by scripts for JSON parsing).
- Node.js + Newman: `npm install -g newman` (or let `scripts/newman.sh` use `npx`).

### A2. One-time setup

```bash
cp .env.example .env
# Edit .env: set every *_PASSWORD value and a unique GRAFANA_ADMIN_PASSWORD.
```

`.env` is gitignored. Never commit it.

### A3. Start everything

```bash
docker compose \
  -f infra/compose/compose.yml \
  -f infra/compose/compose-observability.yml \
  --env-file .env up -d --build

# Wait ~90 seconds for Spring Boot services to start, then:
docker compose -f infra/compose/compose.yml -f infra/compose/compose-observability.yml \
  --env-file .env ps
```

### A4. Health checks

```bash
./scripts/smoke-test.sh     # every service /actuator/health + gateway -> JWKS
./scripts/event-smoke.sh    # every Kafka topic (+ .retry/.dlt) exists
```

Both must print `PASSED`. If a service fails right after startup, wait 30 s
and rerun.

### A5. Full automated E2E

```bash
./scripts/e2e-test.sh       # smoke test, then the whole Postman collection via Newman
```

Expect the Newman summary to show `0` failed assertions. It covers happy path,
insufficient stock, payment failure, duplicate idempotency key, slow and
failing payment gateway, RBAC matrix, and negative 400/401/403/404/409/429
cases. The first run after a fresh start can be slower.

### A6. Manual walkthrough (curl)

Run these in one terminal session; each step reuses variables from the
previous ones. Test users and data are fictional.

```bash
BASE=http://localhost:8080
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
ORDER_ID=$(curl -fsS -X POST $BASE/api/v1/orders \
  -H "Authorization: Bearer $CUST_TOKEN" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $(uuid)" \
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

Repeat step 5's `POST /api/v1/orders` with the *same* `Idempotency-Key`
value; the response returns the original order ID instead of a new order.

### A7. Observability

| UI | URL | Login |
|---|---|---|
| Grafana | `http://localhost:3000` | `GRAFANA_ADMIN_USER` / `GRAFANA_ADMIN_PASSWORD` from `.env` |
| Prometheus | `http://localhost:9090` | none (Status → Targets should be all UP) |

- Dashboard: Grafana → Dashboards → **Pharmacy Platform Observability**.
- Logs: Explore → Loki → `{service_name="order-service"} |= "<correlation-id>"`.
  Pass your own `-H "X-Correlation-ID: <uuid>"` on a request to search for it.
- Traces: copy the trace ID (second value in `[correlationId,traceId,spanId]`
  on a log line) → Explore → Tempo. One order shows as a single trace across
  7 services.
- Drills:

  ```bash
  ./scripts/resilience-lab.sh slow-payment 1500   # prints slowest spans of a slowed order
  ./scripts/resilience-lab.sh hikari 45 120       # pool contention; prints Hikari metrics
  ```

See `docs/11-observability.md` for details.

### A8. Stop, restart, reset

```bash
# Stop (keeps all data volumes)
docker compose -f infra/compose/compose.yml -f infra/compose/compose-observability.yml --env-file .env down

# Rebuild and restart a single service after code changes
docker compose -f infra/compose/compose.yml -f infra/compose/compose-observability.yml --env-file .env build order-service
docker compose -f infra/compose/compose.yml -f infra/compose/compose-observability.yml --env-file .env up -d order-service

# DESTRUCTIVE: delete all local application data volumes
./scripts/reset-demo-data.sh --yes-i-know
```

### A9. Troubleshooting

| Symptom | Check |
|---|---|
| Smoke test fails right after `up` | Services still starting; wait 30–60 s. `docker logs compose-<service>-1 --tail 100` |
| `curl` returns empty / 5xx on first call | Cold start; retry. |
| `400 Invalid ... ID format` | Use lowercase UUIDs (`uuidgen \| tr 'A-Z' 'a-z'`). |
| Order stays `INVENTORY_PENDING` | No stock for that pharmacy/medication, or Kafka unhealthy: `./scripts/event-smoke.sh`. |
| `429 Too Many Requests` | Gateway rate limiting; slow down. |
| Newman fails after many restarts | Rerun once; see "Known environment quirks" in `docs/e2e-runbook.md`. |

---

## Part B — AWS sandbox (billable)

**Cost warning:** the EKS control plane, worker node, RDS instance and ALB
bill every hour they exist (a few US dollars per day). Plan to destroy the
same day. Budget alerts at $5/$10/$15/$20 are configured on the account.

What gets deployed: 3 ECR repositories, 3 images (auth-service, api-gateway,
product-service), VPC with public subnets (no NAT gateway), EKS with one
`t3.small` node, RDS MySQL (private), Secrets Manager secrets, AWS Load
Balancer Controller, and the manifests in `infra/k8s/sandbox/00-04`.

### B1. Prerequisites

- `aws` CLI, `terraform` (native arm64 binary in `~/bin/terraform` is preferred
  on Apple Silicon; the scripts pick it up), `kubectl`, `helm`, Docker running.
- AWS SSO profile `pharmacy-sandbox` configured (`aws configure sso`).

### B2. Sign in and confirm the account

```bash
export AWS_PROFILE=pharmacy-sandbox AWS_REGION=us-east-1
aws sso login --profile pharmacy-sandbox
aws sts get-caller-identity      # Account must be the sandbox account
```

### B3. Point at the Terraform state bucket

The bucket was created once by `infra/terraform/bootstrap` (local state kept
in that folder, gitignored).

```bash
export STATE_BUCKET=$(terraform -chdir=infra/terraform/bootstrap output -raw state_bucket_name)
echo "$STATE_BUCKET"
```

If the bootstrap state is missing, follow "One-time: create the remote-state
bucket" in `infra/terraform/README.md`.

### B4. Review variables

`infra/terraform/envs/sandbox/terraform.tfvars` (gitignored) must exist. If it
doesn't, copy `terraform.tfvars.example`. Set `owner` and a real `expiration`
date (the planned destroy date).

### B5. Validate, plan, apply

```bash
./scripts/aws-validate.sh     # fmt/validate/security scan, no AWS changes
./scripts/aws-plan.sh         # saves tfplan; READ the plan output
./scripts/aws-apply.sh        # type 'apply' to confirm (takes ~15-20 min)
```

`aws-apply.sh` applies Terraform, builds and pushes the 3 images to ECR,
configures `kubectl`, then **pauses** before applying manifests.

### B6. During the pause: create the service databases on RDS

RDS is private, so connect from a temporary pod inside the cluster. In a
**second terminal**:

```bash
export AWS_PROFILE=pharmacy-sandbox AWS_REGION=us-east-1
cd infra/terraform/envs/sandbox
RDS_HOST=$(terraform output -raw rds_endpoint | cut -d: -f1)

# Passwords are read into variables; do not echo them.
MASTER_PW=$(terraform output -raw rds_master_password)
AUTH_PW=$(aws secretsmanager get-secret-value --secret-id pharmacy-sbx/auth-service/db-credentials \
  --query SecretString --output text | python3 -c 'import json,sys; print(json.load(sys.stdin)["password"])')
PRODUCT_PW=$(aws secretsmanager get-secret-value --secret-id pharmacy-sbx/product-service/db-credentials \
  --query SecretString --output text | python3 -c 'import json,sys; print(json.load(sys.stdin)["password"])')

kubectl run mysql-client -n pharmacy --rm -i --restart=Never --image=mysql:8.0 \
  --env="MYSQL_PWD=$MASTER_PW" -- mysql -h "$RDS_HOST" -u admin_master <<SQL
CREATE DATABASE IF NOT EXISTS auth_service;
CREATE USER IF NOT EXISTS 'auth_user'@'%' IDENTIFIED BY '$AUTH_PW';
GRANT ALL ON auth_service.* TO 'auth_user'@'%';
CREATE DATABASE IF NOT EXISTS pharmacy_product;
CREATE USER IF NOT EXISTS 'product_user'@'%' IDENTIFIED BY '$PRODUCT_PW';
GRANT ALL ON pharmacy_product.* TO 'product_user'@'%';
SQL

unset MASTER_PW AUTH_PW PRODUCT_PW
```

(`pharmacy-sbx` is the default `name_prefix`; adjust if you changed it.)
Then return to the first terminal and press **Enter**.

### B7. Smoke test through the ALB

```bash
./scripts/aws-smoke-test.sh
```

It waits for the ALB hostname, then checks gateway health, JWKS, register +
login, and a JWT-authenticated product read. Manual checks:

```bash
ALB=$(kubectl get ingress pharmacy-ingress -n pharmacy -o jsonpath='{.status.loadBalancer.ingress[0].hostname}')
curl -s http://$ALB/actuator/health                       # {"status":"UP"}
curl -s http://$ALB/api/v1/auth/.well-known/jwks.json      # public key set
kubectl get pods -n pharmacy
kubectl logs -n pharmacy deploy/api-gateway --tail 50
```

Only `/api/v1/auth/**` and product routes work here. Order, inventory and the
other saga endpoints are local-only.

### B8. Tear down (mandatory)

```bash
./scripts/aws-destroy.sh               # re-verifies identity before destroying
./scripts/aws-post-destroy-check.sh    # EKS/EC2/ELB/RDS/NAT/EIP/ECR lists must be empty
```

`aws-destroy.sh` asks you to type `destroy pharmacy-sandbox`, then `yes` to
accept deleting RDS without a final snapshot.

Afterward, check AWS Billing → Bills the next day for any unexpected charges.
Do not destroy the bootstrap state bucket unless you're retiring the
exercise entirely.

## Related docs

- `docs/e2e-runbook.md` — Newman details and fixed issues
- `docs/11-observability.md` — metrics, traces, logs, drills
- `infra/terraform/README.md`, `docs/aws-plan-review.md` — AWS design, cost and threat review
- `docs/known-gaps.md` — open follow-ups
