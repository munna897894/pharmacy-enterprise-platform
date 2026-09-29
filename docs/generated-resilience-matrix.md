# Generated Resilience Matrix

| Dependency | Caller | Timeout | Retry | Circuit/Bulkhead | Fallback | Idempotency | User-visible result |
|---|---|---|---|---|---|---|---|
| Inventory lookup | Order service | 5s/10s | None | Caller-side failure | Fail order creation | request-level validation | order rejected |
| Order lookup | Payment service | 5s/10s | None | Caller-side failure | Fail payment processing | idempotency key | payment rejected |
| External prescription verification | Prescription service | bounded | Safe only | circuit + bulkhead | reject/pending | eventId | prescription rejected or delayed |
| Redis cache | Product service | fast fail | none | N/A | MySQL fallback | N/A | slower read, correct data |
| Kafka publish | Order/Payment | async | publisher retry | outbox backlog | retry later | eventId | committed order preserved |
