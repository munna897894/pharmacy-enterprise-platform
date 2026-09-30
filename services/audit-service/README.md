# Audit Service

The audit service is a Kafka consumer and ADMIN-only query API. It owns the
`pharmacy_audit` MySQL schema and listens on port `8090` by default. Its
deployed consumer topics, payload compatibility and transaction behavior are
documented in [`ARCHITECTURE.md`](ARCHITECTURE.md).

Current bindings consume the order, inventory, payment, prescription and
notification event topics. Processed event IDs and audit rows are committed in
one database transaction. The service does not publish events or own an
outbox. The consumer accepts the documented envelope and adapts the flat
order/inventory/payment payloads already present in this repository.

The API exposes paginated audit trail and search endpoints plus a compliance
report endpoint under `/api/v1/audit`; all require ADMIN authorization. Event
payload details are stored as received after envelope metadata removal for
legacy flat events. There is no general-purpose redaction layer, so producers
must not send sensitive data.

See [`QUICKSTART.md`](QUICKSTART.md) to build, test and run the service.
