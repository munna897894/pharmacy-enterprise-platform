# Audit Service Architecture

The audit service consumes selected Kafka event streams, writes audit records
and processed-event IDs to its own MySQL schema, and exposes an ADMIN-only
read API. The service listens on port `8090` and uses schema
`pharmacy_audit` by default. Its runtime values are configurable; in the
canonical full-fleet local Kubernetes deployment, MySQL and Kafka are host
services and Redis/external-mock are in-cluster. Audit does not depend on
Redis. See [`../../docs/architecture-local.md`](../../docs/architecture-local.md).

## Event ingestion

Spring Cloud Function binds these consumers:

| Consumer | Topic | Consumer group |
|---|---|---|
| `orderEventConsumer` | `pharmacy.order.events.v1` | `audit-order-events-v1` |
| `inventoryEventConsumer` | `pharmacy.inventory.events.v1` | `audit-inventory-events-v1` |
| `paymentEventConsumer` | `pharmacy.payment.events.v1` | `audit-payment-events-v1` |
| `prescriptionEventConsumer` | `pharmacy.prescription.events.v1` | `audit-prescription-events-v1` |
| `notificationEventConsumer` | `pharmacy.notification.events.v1` | `audit-notification-events-v1` |

Each binding allows three processing attempts and configures its source
topic's DLT. `AuditEventProcessor.process` runs transactionally: it maps known
event types to audit actions, creates the audit row, and inserts the event ID
in `processed_events`. A repeated ID is ignored. Invalid JSON or invalid
required identifiers fail processing so the configured retry/DLT policy can
handle the record; an unrecognized event type is ignored.

The processor accepts the documented event envelope and also adapts the flat
order, inventory and payment payloads currently produced by this repository.
That compatibility adapter is consumer-side only; new producers should follow
[`../../docs/07-event-contracts.md`](../../docs/07-event-contracts.md). The
processor handles event types for additional resources, but the active
bindings above are the subscriptions currently configured.

Audit service is a consumer, not an event publisher: it has no outbox relay.
Critical producer services own their outbox records and wait for Kafka send
acknowledgement before marking an event published. The database transaction
and Kafka publish are not atomic.

## Persistence and payload handling

Flyway creates `audit_logs` and `processed_events` in the service-owned
schema. `audit_logs` has indexes for aggregate/time, user/time, and
resource/time/action queries. `processed_events.event_id` is the primary key.
Hibernate validates the schema at startup.

When the event has a `payload`, the processor stores that payload as audit
details. For a flat legacy event it stores the event fields after removing
known envelope metadata. The processor does not provide a general-purpose
redaction layer; event producers must not include credentials, payment
secrets, prescription text, addresses or other prohibited sensitive data.

Records are created through the application service and there are no update
or delete API routes. No seven-year retention or database-enforced
immutability policy is implemented by this service.

## API and security

All routes require an authenticated ADMIN role:

| Method and path | Behavior |
|---|---|
| `GET /api/v1/audit/{aggregateId}` | Paginated audit trail for a UUID aggregate |
| `GET /api/v1/audit/search` | Filter by resource type, action, user, service and time range |
| `GET /api/v1/audit/compliance/report` | Aggregate counts for a resource type and required time range |

Search defaults to the prior 90 days through the current time when either
search boundary is omitted. Compliance report requests require both
`startDate` and `endDate`. Responses are DTOs; persistence entities are not
returned directly. Compliance reports use Spring's process-local simple cache.

## Tests and runtime configuration

The service tests include event processor compatibility/duplicate handling,
Kafka consumer, API, repository and integration coverage. Run the suite from
the repository root:

```bash
./mvnw -pl services/audit-service -am test
./mvnw -pl services/audit-service -am verify
```

The runtime reads `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`,
`KAFKA_BOOTSTRAP_SERVERS`, `JWT_ISSUER_URI`,
`JWT_JWK_SET_URI` and `OTEL_EXPORTER_OTLP_ENDPOINT`. The code defaults include
port `8090`, schema `pharmacy_audit`, and host-based local dependency values;
deployment manifests may override them. Do not copy development credentials
into committed configuration.
