# Resilience Matrix

This table describes the implemented paths. Circuit breakers and bulkheads are
target standards in `08-cross-cutting-standards.md`, not claims about paths
that do not currently use them.

| Dependency | Caller | Timeout | Retry | Circuit/Bulkhead | Fallback | Idempotency | User-visible result |
|---|---|---|---|---|---|---|---|
| Inventory lookup | Order service | 5s/10s | None | Caller-side failure | Fail order creation | request-level validation | order rejected |
| Order lookup | Payment service | 5s/10s | None | Caller-side failure | Fail payment processing | idempotency key | payment rejected |
| Payment provider simulation | Payment service | 3s/5s | None | Caller-side failure classification | Mark payment failed and emit result | idempotency key | payment failed; saga compensates inventory |
| SMS provider simulation | Notification service | 5s/10s | None | Caller-side failure | Record failed notification and publish `NotificationFailed` | eventId inbox + notification outbox | order remains committed; notification failure is observable |
| Redis cache | Product service | fast fail | none | N/A | MySQL fallback | N/A | slower read, correct data |
| Kafka publish | Order/inventory/payment/notification | Broker acknowledgement awaited | scheduled relay retries unpublished rows | outbox backlog | retry later | eventId | committed business state preserved |
