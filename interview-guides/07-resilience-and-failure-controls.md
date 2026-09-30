# 07 — Resilience and Failure Controls (Prompt 07)

## Summary

The current platform has HTTP timeouts on cross-service clients, idempotent saga consumers, order/inventory/payment/notification transactional outboxes, bounded Kafka stream-consumer retries/DLQs, and Redis cache fallback in product. Do not describe retries or DLT as absent platform-wide. Equally, do not infer that every raw `@KafkaListener` has a DLT just because the topic exists.

## Failure boundaries

| Failure | Current mechanism | Operational question |
|---|---|---|
| Kafka unavailable during a business write | Commit business data plus outbox row locally; relay waits up to 10 s for send acknowledgement and leaves failed rows pending. | Are unpublished count and oldest age rising? |
| Duplicate event | Inbox/processed-event uniqueness and payment's order-derived idempotency key. | Was the side effect applied only once? |
| Stream consumer poison message | Notification/audit use bounded attempts and binder `enableDlq` with explicit one-partition `*.dlt`. | Which group failed, and what is in the DLT? |
| Raw Spring Kafka listener failure | Order/inventory/payment container factories currently register `DefaultErrorHandler` without `DeadLetterPublishingRecoverer`. | Verify recovery semantics; do not promise a raw-listener DLT. |
| Redis unavailable to product cache | `CacheErrorHandler` logs cache errors and allows a DB-backed read. | Does DB load grow while cache is unavailable? |
| External mock payment delayed/failed | Payment client has bounded HTTP timeouts and simulated failure outcomes. | Did the order cancel and release its reservation? |
| Inventory fails before order creation | Synchronous availability check has HTTP timeouts; request fails without a new order. | Did any partial state commit? |

The local topic initialization creates the five versioned domain topics and corresponding retry/DLT topics. Topic creation alone does not configure every listener's retry/DLT handling. The stream binder's DLQ is concrete; the raw listener `DefaultErrorHandler` configuration deserves separate review if poison-message recovery for those consumers is required.

## Key files

| File | What to study |
|---|---|
| `services/{order,inventory,payment}-service/.../infrastructure/kafka/KafkaConfig.java` | Raw-listener error handlers and limits. |
| `services/notification-service/src/main/resources/application.yml`, `services/audit-service/src/main/resources/application.yml` | Stream binder retry/DLQ settings and distinct consumer groups. |
| `services/{order,inventory,payment}-service/.../OutboxPublisher.java` and `services/notification-service/.../NotificationOutboxService.java` | Send-future acknowledgement before marking published, failure attempts/backlog. |
| `services/product-service/.../infrastructure/config/RedisConfig.java` | Cache failure handling without falsely reporting a cached hit. |
| `scripts/resilience-lab.sh`, `scripts/local-kafka-init.sh` | Repeatable local drills and topic initialization. |

## Core concepts

| Concept | Interview explanation |
|---|---|
| Timeout | Bounds a single wait; it does not stop repeated requests from overloading a failing dependency. |
| Retry/DLT | Retry only where effects are safe/idempotent; after bounded stream retries, route failures to a DLT for investigation. A DLT needs monitoring and a deliberate replay procedure. |
| Outbox + inbox | Prevent a committed business change from losing publication intent; redelivery remains possible and requires deduplication. |
| Compensation | Payment failure releases inventory; a cross-service DB rollback is not available. |
| Circuit breaker/bulkhead | Explain these as separate protections; do not assert blanket circuit-breaker or bulkhead coverage without checking the specific call path. |
| Graceful degradation | Cache-aside product reads can use MySQL when Redis fails; payment/inventory failures are not safely interchangeable with cached data. |

## Common interview Q&A

**Q: What if Kafka is down just after an order commits?**
A: The order and outbox row committed together. The relay's send fails or times out, records a failed attempt and retries on a future sweep. It only marks published after broker acknowledgement. Watch backlog age; once Kafka recovers, consumers may still see duplicates.

**Q: Are Kafka poison messages silently discarded?**
A: Not for configured notification/audit stream bindings: they use bounded retries and DLQs. Raw order/inventory/payment listeners use `DefaultErrorHandler` without an explicit dead-letter recoverer, so do not claim uniform DLT coverage across listener types.

**Q: Is the workflow exactly once?**
A: No. Broker acknowledgements prevent false publication success, but a relay can crash after an acknowledged send and before marking its row published. Inbox records and payment idempotency make repeated delivery safe.

**Q: Why use both timeout and a circuit breaker?**
A: A timeout caps each slow request; a breaker can avoid repeatedly paying that cost during sustained failure. Evaluate the actual client before claiming a breaker is active.

**Q: What should operators inspect when notification is missing?**
A: Check order/payment status, notification's consumer group lag, retry/DLT, processed-event record, notification row and notification outbox backlog before replaying anything.

## Drill: Kafka outage

Stop the **project-scoped** Kafka broker only during a controlled local lab, create an order and observe pending outbox rows. Restore the broker, watch publication catch up, then verify exactly one business outcome per event despite possible replay. Use `scripts/resilience-lab.sh` for its documented slow-payment and Hikari drills; do not present those lab results as AWS validation.
