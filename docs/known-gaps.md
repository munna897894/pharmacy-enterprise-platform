# Known gaps

Tracked follow-ups after Prompts 01–14. Gaps 1–6 are closed. Gap 7 is prepared
but intentionally deferred; gaps 8–15 document implementation limitations found
during the repository-wide documentation reconciliation.

| # | Area | Gap | Impact | Suggested fix | Status |
|---|---|---|---|---|---|
| 1 | notification-service | Did not publish `NotificationSent`/`NotificationFailed` to `pharmacy.notification.events.v1`. | Audit's notification consumer receives nothing; delivery outcomes are not audited. | Add a transactional outbox to notification-service and publish per `docs/07-event-contracts.md`. | Done |
| 2 | order/inventory/payment outbox relays | Events are marked published without awaiting the `KafkaTemplate.send` result. | A failed send can drop an event silently and stall the saga. | Await the send future with a timeout; mark published only on success, otherwise increment attempts and retry. | Done |
| 3 | notification-service bindings | `startOffset: latest` was set on the order, payment and prescription consumer groups. | A new consumer group skips events published before it first started. | Use `earliest`; consumers are already idempotent via `processed_events`. | Done |
| 4 | Kafka DLT (local) | Six historic payment events sat in `pharmacy.payment.events.v1.dlt` from before the notification redesign. | DLT alert history and noise. | Drained with the Kafka volume; all five DLTs verified empty after a full `e2e-test.sh` run. | Done |
| 5 | Service actuator health | `/actuator/health` showed component details (db, disk) on internal service ports. | Minor information disclosure inside the cluster. | `show-details: when-authorized` now set on auth, customer, inventory, pharmacy and prescription. | Done |
| 6 | Build | customer-, inventory- and pharmacy-service lacked a Failsafe execution. | Their `*IT` Testcontainers tests would not run in `mvn verify` or CI. | Failsafe execution added to all three poms. | Done |
| 7 | Prompt 14 | Dynatrace and Splunk require vendor accounts/trials for live validation. | No impact on the open-source baseline. | Disabled-by-default Compose/Helm hooks, secure activation/removal guidance, SPL examples and cross-signal triage are prepared; activate only during an approved trial. | Prepared — live vendor validation deferred |
| 8 | prescription-service events | `pharmacy.prescription.events.v1` is provisioned and consumed, but prescription-service does not publish `PrescriptionVerified`/`PrescriptionRejected`. | Prescription lifecycle notifications and audit entries are absent; the order saga is unaffected because it validates prescriptions synchronously. | Add a prescription transactional outbox and publish the reserved contracts in `docs/07-event-contracts.md`, with duplicate-delivery and DLT tests. | Open |
| 9 | resilience controls | The standard calls for circuit breakers and bulkheads around external dependencies, but current clients rely on bounded timeouts and explicit failure handling. | Sustained dependency latency can consume request capacity even though calls eventually fail safely. | Add measured circuit-breaker/bulkhead policies to the payment provider, notification provider and ownership clients without retrying unsafe operations. | Open |
| 10 | prescription authorization | Prescription read/list endpoints require authentication but do not verify that a customer owns the requested prescription/customer ID. | Any authenticated caller reaching the service route can read another fictional customer's prescription data. | Add owner-or-staff authorization using JWT `sub` and customer-service ownership verification, with forbidden and dependency-failure tests. | Open |
| 11 | order authorization | Order read/status paths are permitted internally for payment-service, while controller ownership checks are placeholders; list/cancel/ready/complete also lack complete owner/role enforcement. | A caller that passes gateway authentication can request or mutate orders outside its intended ownership/role boundary. | Split internal lookup from public APIs, forward service credentials/JWT as appropriate, and enforce owner-or-staff/role checks with controller tests. | Open |
| 12 | gateway rate limiting | The gateway JWT filter forwards user headers but does not populate the exchange `userId` attribute consumed by the rate-limit filter; Redis errors also fail open. | The configured per-user limit is ineffective, so requests are limited only by source IP; a Redis outage temporarily removes that protection. | Derive the rate-limit key from the verified principal or set the attribute explicitly, then choose and test an intentional Redis-outage policy. | Open |
| 13 | audit payload sanitization | Audit removes envelope metadata for legacy flat events but has no general-purpose sensitive-field redaction layer. | A producer regression could persist prohibited fields in the audit schema. | Enforce allowlisted/redacted audit payload mapping and add contract tests with password, token, prescription, address and payment-field fixtures. | Open |
| 14 | order outcome events | Event contracts define `OrderConfirmed`, `OrderCancelled` and `OrderReadyForPickup`, but order-service currently publishes only `OrderCreated`. | Consumers cannot react to final order lifecycle outcomes through the documented order topic. | Persist each outcome through the existing order outbox and add compatibility, duplicate-delivery and end-to-end tests. | Open |
| 15 | raw Kafka DLT routing | Order, inventory and payment listener factories use `DefaultErrorHandler` without a `DeadLetterPublishingRecoverer`. | Records that exhaust the raw-listener retry policy are not guaranteed to reach the provisioned `.dlt` topics. | Configure explicit recoverers/backoff for each raw listener and test poison-message routing, headers and replay. | Open |

## Verification reference

- Canonical local runtime check: `./scripts/local-k8s-test.sh`. The
  Compose-specific alternatives are `./scripts/e2e-test.sh` and
  `./scripts/resilience-lab.sh {slow-payment|hikari}`.
- Docker Desktop Kubernetes: `sh scripts/local-k8s-test.sh` passed with 13/13
  ready Deployments, 164 requests and 193 assertions on 2026-09-29. A second
  run with the in-cluster Collector enabled passed 193 assertions; all 12 app
  metrics targets, Collector and Kafka exporter were healthy (14/14), Tempo
  showed a seven-service order trace and Loki ingested order-service logs.
- On-demand full-fleet AWS EKS is not yet live-verified: the
  `pharmacy-sandbox` SSO session is expired, so no AWS create, smoke test,
  destroy/recreate or chargeable-resource inventory has been executed.
  Offline Terraform and manifest validation are not evidence of a working
  cluster; obtain a fresh session and review the billable plan before any
  AWS deployment.
- AWS currently deploys its rendered `infra/k8s/sandbox/` manifests rather
  than installing the shared application Helm chart. The AWS values overlay
  renders for all twelve workloads but is not the deployment source until
  its per-service Secrets Manager fetches can be represented without copying
  credentials into Kubernetes Secrets. Keep the AWS render/validation guard
  in place to catch drift.
- Observability details: `docs/11-observability.md`.
- E2E history and resolved issues: `docs/e2e-runbook.md`.
- Deployment views: `docs/architecture-local.md` and
  `docs/architecture-cloud.md`.
