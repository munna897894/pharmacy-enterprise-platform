# Known gaps

Tracked follow-ups after Prompts 01–13. None block the platform; each is a hardening item.

| # | Area | Gap | Impact | Suggested fix | Status |
|---|---|---|---|---|---|
| 1 | notification-service | Does not publish `NotificationSent`/`NotificationFailed` to `pharmacy.notification.events.v1`. | Audit's notification consumer receives nothing; delivery outcomes are not audited. | Add a transactional outbox to notification-service and publish per `docs/07-event-contracts.md`. | Open |
| 2 | order/inventory/payment outbox relays | Events are marked published without awaiting the `KafkaTemplate.send` result. | A failed send can drop an event silently and stall the saga. | Await the send future with a timeout; mark published only on success, otherwise increment attempts and retry. | Open |
| 3 | notification-service bindings | `startOffset: latest` on the order, payment and prescription consumer groups. | A new consumer group skips events published before it first started. | Use `earliest`; consumers are already idempotent via `processed_events`. | Open |
| 4 | Kafka DLT (local) | Six historic payment events sit in `pharmacy.payment.events.v1.dlt` from before the notification redesign. | DLT alert history and noise. | Inspect, then replay to the source topic or discard. | Open |
| 5 | Service actuator health | `/actuator/health` shows component details (db, disk) on internal service ports. | Minor information disclosure inside the cluster. | Set `management.endpoint.health.show-details: never` (or `when-authorized`) on all services. | Open |
| 6 | Build | customer-, inventory- and pharmacy-service lack a Failsafe execution. | Their `*IT` Testcontainers tests do not run in `mvn verify` or CI. | Add the Failsafe plugin execution used in the other services. | Open |
| 7 | Prompt 14 | Dynatrace and Splunk integration docs are not written. | Optional scope. | Implement when Prompt 14 is picked up. | Optional |

## Verification reference

- Runtime checks: `./scripts/e2e-test.sh` and `./scripts/resilience-lab.sh {slow-payment|hikari}`.
- Observability details: `docs/11-observability.md`.
- E2E history and resolved issues: `docs/e2e-runbook.md`.
