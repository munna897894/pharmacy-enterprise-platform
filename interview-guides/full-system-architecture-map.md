# Full System Architecture Map (Current Code and Deployment)

Use this map for the present full-fleet topology. Local Kubernetes has runtime evidence recorded in `docs/known-gaps.md` and `docs/e2e-runbook.md`; the current full-fleet AWS design is prepared **but not live-verified**. Older guides describing orphaned audit topics, absent inventory producers or a partial Compose fleet reflect an earlier implementation.

## 1. Workloads and ports

| Workload | App port | Responsibility |
|---|---:|---|
| api-gateway | 8080; management 9081 | WebFlux edge, JWT validation, routing, Redis rate limiting |
| auth-service | 8081 | RSA JWT/JWKS, users and refresh tokens |
| product-service | 8082 | Medications/pricing, Redis cache-aside |
| customer-service | 8083 | Customer profile and ownership |
| pharmacy-service | 8084 | Stores and locations |
| inventory-service | 8085 | Stock, reservations, rejection/release |
| prescription-service | 8086 | Prescription activation/fill lifecycle |
| order-service | 8087 | Order aggregate, saga state and outbox |
| payment-service | 8088 | Simulated gateway, payment/refund and outbox |
| notification-service | 8089 | Simulated delivery and outcome outbox |
| audit-service | 8090 | Event audit read model |
| external-mock-service | 8080 internal | Simulated payment dependency and controllable lab endpoints; no DB |

Ten durable services each own their schema/user and migrations on MySQL. No shared JPA entities/tables; Redis serves product cache and gateway rate limiting. The mock and gateway share a **number**, not a Service address. Internal services are ClusterIP; public application calls enter through the gateway.

## 2. Deployment map

```text
Local (canonical):
  client -> localhost:18080 port-forward -> gateway in pharmacy namespace
  pharmacy: twelve JVM workloads + Redis
  pods -> host.docker.internal:3308 (project MySQL 8.4)
       -> host.docker.internal:29092 (project Kafka 4.x KRaft)
  host tools -> localhost:19092 (Kafka)
  pharmacy-observability: Prometheus, Grafana, Loki, Tempo, Alloy,
                          OpenTelemetry Collector, Kafka exporter

Compose (legacy alternative):
  infra/compose/compose.yml runs the full fleet with its own MySQL/Kafka/Redis;
  do not conflate Compose ports/DNS with Kubernetes dependencies.

AWS (prepared, not live-verified as a full fleet):
  temporary EKS: twelve JVM workloads, in-cluster Redis/private Kafka,
                 separate observability namespace
  private RDS: ten isolated schema/user pairs; ECR: twelve images
  default private gateway port-forward; optional restricted HTTPS ALB
```

Use the [local deployment diagram](../docs/architecture-local.md) and
[AWS deployment diagram](../docs/architecture-cloud.md) for the detailed
environment boundaries. Local run commands and evidence live in
`docs/self-run-guide.md`. AWS security/cost and status live in
`docs/aws-plan-review.md`; Prompt 14 vendor observability hooks are prepared
**disabled**, not running integrations.

## 3. Request and synchronous calls

```text
client -> gateway -> auth/product/customer/pharmacy/inventory/prescription/
                     order/payment/notification/audit (by route)
  auth issues RS256 JWTs; gateway and downstream resource servers validate them
  gateway propagates X-Correlation-ID; trace context crosses HTTP and Kafka

order -> inventory GET /api/v1/inventory/availability (early pre-check)
payment -> order lookup (amount/currency/context)
payment -> external mock (simulated payment)
prescription -> no active external verification call in the current service
```

The pre-check is not an inventory hold. Inventory's subsequent event-consumer reservation is authoritative. Services use configured HTTP timeouts; no service reads a different service's MySQL tables. Avoid inferring every theoretical domain relationship is a synchronous call.

## 4. Kafka choreography and side effects

| Topic | Producer | Active consumer paths |
|---|---|---|
| `pharmacy.order.events.v1` | order outbox (`OrderCreated`; no current `OrderConfirmed`/`OrderCancelled` publish path) | inventory reserve, notification projection, audit |
| `pharmacy.inventory.events.v1` | inventory outbox (`InventoryReserved`, `InventoryRejected`, `InventoryReleased`) | order state, payment trigger on reserve, audit |
| `pharmacy.payment.events.v1` | payment outbox (completion/failure/refund outcomes) | order confirmation/cancellation, inventory commit/release, notification, audit |
| `pharmacy.prescription.events.v1` | No current prescription-service publisher | notification, audit bindings are ready, but receive no prescription-service events |
| `pharmacy.notification.events.v1` | notification outbox (`NotificationSent`, `NotificationFailed`) | audit |

```text
OrderCreated -> InventoryReserved -> PaymentCompleted -> CONFIRMED
       |                |                   |
       |                |                   +-> inventory commits reservation
       |                +-> payment triggered with order-derived idempotency key
       +-> InventoryRejected -> CANCELLED_INVENTORY (no payment)
PaymentFailed -> CANCELLED_PAYMENT + inventory releases stock (InventoryReleased)
OrderCreated -> notification order/customer projection
PaymentCompleted/Failed -> simulated notifications -> NotificationSent/Failed -> audit
```

Order/inventory/payment use Spring Kafka listeners; notification/audit use Spring Cloud Stream functions bound to the **actual** `pharmacy.*.events.v1` destinations. Each consumer's own processed-event record guards duplicate delivery. Outbox relays wait for broker acknowledgements before marking published; at-least-once delivery still permits duplicates. Binder stream consumers use bounded retries and explicit one-partition DLTs. Raw Spring Kafka listener factories use `DefaultErrorHandler` without an explicit DLT recoverer; existing retry/DLT *topics* do not establish identical recovery for every raw listener. Prescription publication and order confirmation/cancellation event publication remain contract-versus-code gaps. Compare actual flat producer payloads with the target common envelope in `docs/07-event-contracts.md`.

## 5. Security, observability and limits

- Gateway application port `8080`, management `9081`; Actuator metrics remain internal. RSA signing keys stay with auth, validators use JWKS; logs must not contain tokens, payment details, addresses or prescription text.
- Local observability is a separate Kubernetes Helm release, not the Compose scraper. Correlation and trace headers are stored through outbox hops; metrics tags remain low cardinality.
- Tests and local run history are evidence of **local** behavior, not AWS deployment. The earlier three-service AWS ALB smoke/destroy run is historical evidence only; the current full-fleet EKS path has not been live exercised.
- No claims of production HA: local Docker Desktop and temporary single-broker/two-worker AWS sandbox are educational. Resource requests/probes and private ingress do not turn a single-replica fleet into a production architecture.

## Interview prompts

**Why outbox plus inbox?** A local DB commit cannot atomically publish to Kafka; the acknowledged relay can re-send on a crash, so consumers deduplicate by `eventId` in their own DB transactions.

**What happens when stock or payment fails?** Inventory rejection cancels without charging. Payment failure cancels after reservation, and inventory compensates by releasing it.

**Does audit receive real traffic?** Yes. Its five function bindings use the same versioned topics as current publishers, including notification outcome events; verify a concrete audit record in E2E rather than trusting configuration alone.

**What is the local/cloud boundary?** Local Kubernetes reaches project-scoped host MySQL/Kafka; the AWS sandbox is independently provisioned with private RDS and in-cluster Kafka/Redis, and remains pending full-fleet live verification.
