# 07 — Resilience and Failure Controls (Prompt 07)

## Summary

The platform implements several *real* resilience patterns — HTTP client timeouts on every synchronous call, the transactional outbox pattern (order/payment-service), the idempotent-consumer/inbox pattern (order, payment, notification, audit), and a graceful cache-degradation handler (product-service, fixed during this learning session). It does **not** implement the two patterns most commonly associated with "enterprise resilience": there is no circuit breaker anywhere in actual use (despite the dependency being declared in api-gateway), and there is no dead-letter topic (DLT) — failed Kafka messages are retried a fixed number of times and then silently dropped. This guide is grounded entirely in what's actually there.

## Diagram — resilience mechanisms mapped onto the real request/event flows

```
Synchronous calls (all have explicit timeouts, NONE have a circuit breaker):
  order-service    --RestTemplate(connect=5s,read=10s)--> inventory-service
  payment-service  --RestTemplate(connect=5s,read=3s)--> order-service, external-mock-service
  notification-service --RestTemplate(connect=5s,read=10s)--> (external SMTP-like calls)
  prescription-service --RestTemplate(NO timeout config found)--> (unused today, see 04 guide)

Transactional Outbox (real, both sides):
  order-service:   Order + OutboxEvent saved in 1 DB tx --> @Scheduled(5s) poller --> Kafka
  payment-service: Payment + OutboxEvent saved in 1 DB tx --> poller --> Kafka
  Outbox poller failure handling: catch(Exception) -> log only -> retry automatically
                                  on the NEXT scheduled run (no backoff, no max-attempts,
                                  no dead-lettering of a permanently-failing row)

Idempotent Consumer / Inbox (real, four independent implementations):
  order-service:        ProcessedEvent(orderId, eventId) unique constraint
  payment-service:      idempotencyKey lookup on the SYNCHRONOUS API (different problem, same pattern family)
  notification-service: ProcessedEvent(eventId) via existsById before handling
  audit-service:         ProcessedEvent(eventId) via BaseEventConsumer.isEventProcessed()

Kafka consumer error handling (real, but no DLT):
  order-service:        DefaultErrorHandler() registered on the container factory — default
                         behavior is a fixed number of retries then log-and-skip, NO dead-letter
                         topic configured
  notification/audit:   Spring Cloud Stream binder config sets max-attempts: 3 (audit-service only;
                         notification-service sets none, meaning binder defaults apply) — again,
                         no dead-letter destination is configured anywhere

Cache resilience (real, fixed during a prior learning session):
  product-service: CachingConfigurer.errorHandler() swallows Redis GET/PUT/EVICT/CLEAR failures
                   and logs instead of propagating -> requests succeed even if Redis is down
                   (this is the ONE genuine graceful-degradation example in the whole platform)

Circuit breaker (declared, NEVER used):
  api-gateway/pom.xml declares spring-cloud-starter-circuitbreaker-resilience4j AND
  resilience4j-core as dependencies, but a full source search finds zero @CircuitBreaker
  annotations, zero CircuitBreakerFactory usage, and zero resilience4j config in
  application.yml anywhere in the codebase. This is a dependency that does nothing.
```

## Key files

| File | What it shows |
|---|---|
| `order-service/.../infrastructure/config/RestTemplateConfig.java` | `setConnectTimeout(Duration.ofSeconds(5))`, `setReadTimeout(Duration.ofSeconds(10))` — the platform-wide timeout pattern, repeated per service rather than shared. |
| `payment-service/.../infrastructure/RestTemplateConfig.java` | Same pattern but `readTimeout=3s` (tighter, since payment-gateway calls should fail fast) — a deliberate per-client timeout tuning decision. |
| `order-service/.../infrastructure/kafka/OutboxPublisher.java` | `@Scheduled(fixedDelay = 5000)` — the entire retry mechanism for a failed publish is "try again in 5 seconds," forever, with only a `System.err.println` on failure. |
| `order-service/.../infrastructure/kafka/KafkaConfig.java` | `factory.setCommonErrorHandler(new DefaultErrorHandler())` — Spring Kafka's out-of-the-box error handler (fixed backoff retries, then logs and moves on); no `DeadLetterPublishingRecoverer` is wired in, so there's no DLT. |
| `audit-service/.../application.yml` | `consumer.max-attempts: 3` on every binding — Spring Cloud Stream's binder-level retry count, again with no `dlq-name`/`enableDlq` configured. |
| `product-service/.../infrastructure/config/RedisConfig.java` | `CachingConfigurer.errorHandler()` — the platform's one real graceful-degradation implementation (added during this learning session; see 02 guide). |
| `payment-service/.../application/PaymentService.java` | `processPayment(request, idempotencyKey)` — idempotent-write pattern via `findByIdempotencyKey` lookup before charging. |
| `*/domain/ProcessedEvent*.java` (order, notification, audit) | Three independent implementations of the same inbox-pattern table, each scoped to its own service's database (no shared table, per the platform's data-ownership rule). |
| `services/api-gateway/pom.xml` | Declares `resilience4j-core` and `spring-cloud-starter-circuitbreaker-resilience4j` as dependencies — confirmed via full source search to be entirely unused (no annotations, no config, no factory calls anywhere in the gateway). |

## Core concepts

| Concept | Explanation | Why it matters |
|---|---|---|
| Timeout pattern | Every `RestTemplate` used for a cross-service call has an explicit connect + read timeout. | Without an explicit timeout, a synchronous HTTP client defaults to "wait forever," which turns one slow downstream service into a thread-pool exhaustion cascade across every caller — this is the single most important resilience primitive and the platform gets it right everywhere except prescription-service's (unused) client. |
| Circuit breaker pattern (closed/open/half-open) | Not implemented here, but a standard companion to timeouts: after N consecutive failures, the breaker "opens" and fails fast without even attempting the call for a cooldown period, then "half-opens" to test if the dependency recovered. | Worth being able to explain conceptually even though this codebase doesn't use it — a very likely interview follow-up given the dependency is declared but dead: "why declare it if you don't use it?" is itself a great discussion point about incomplete migrations. |
| Retry with backoff | Spring Kafka's `DefaultErrorHandler` and Spring Cloud Stream's `max-attempts` both retry failed message processing a bounded number of times before giving up — using a default fixed/exponential backoff internally. | Retries only make sense for transient, idempotent-safe failures — which is exactly why the idempotent-consumer pattern (inbox) has to exist alongside retries: a redelivered message after a retry must not double-apply a business effect. |
| Dead Letter Topic/Queue (DLT/DLQ) pattern | **Not implemented anywhere in this codebase.** The standard pattern is: after retries are exhausted, route the poison message to a separate topic for manual inspection/replay instead of silently dropping it. | This is a genuine, concrete gap: today, a message that fails processing 3 times in audit-service or triggers `DefaultErrorHandler`'s retry ceiling in order-service is simply logged and discarded — there is no operational path to recover it. |
| Transactional Outbox pattern | Business row + outbox row committed in one local DB transaction; a separate poller publishes to Kafka afterward. | Solves the "dual write" problem: a DB commit and a Kafka publish are two different systems and can't be wrapped in one atomic distributed transaction without something like two-phase commit (which Kafka doesn't support cleanly) — the outbox guarantees the event is never lost even if the process crashes right after the DB commit. |
| Idempotent consumer / inbox pattern | Every Kafka consumer checks a `ProcessedEvent`-style table (keyed by `eventId`) before acting, and records it after acting, in the same transaction as the business mutation. | Required because Kafka (and the outbox pattern's poller) both provide **at-least-once** delivery, never exactly-once — duplicate deliveries are a normal, expected occurrence, not an edge case. |
| Idempotency key on synchronous APIs | payment-service's `processPayment` looks up an existing payment by `idempotencyKey` before charging. | A different flavor of idempotency than the Kafka inbox pattern — this protects a client-initiated retry (e.g., after an HTTP timeout) from double-charging, whereas the inbox pattern protects against duplicate *event delivery*. |
| Bulkhead pattern | Not implemented — there's no evidence of per-dependency thread-pool isolation (e.g., a dedicated executor for calls to external-mock-service separate from calls to inventory-service). | Worth naming as a "not present" pattern: today a slow inventory-service could still exhaust order-service's shared HTTP client thread pool even with a 10s read timeout, because nothing isolates that dependency's failure blast radius from others. |
| Graceful degradation | product-service's `CacheErrorHandler` (fixed during this learning session — see 02 guide) is the platform's only genuine example: a Redis outage now logs and continues instead of failing the request. | This is the pattern every other synchronous dependency in the platform lacks — if inventory-service, order-service, or payment-service's downstream calls fail today, the caller gets a hard exception, not a degraded-but-functional response. |
| Eventual consistency in a choreography saga | The order↔payment event loop (05 guide) means the *system* is consistent only after all events settle, not instantaneously. | A standard CAP-theorem-adjacent tradeoff of async choreography: you gain availability and decoupling, but callers must tolerate a window where, e.g., an order shows `PAYMENT_PENDING` for some seconds before a Kafka round-trip confirms it. |

## Common interview Q&A

**Q: What resilience patterns does this platform actually implement, end to end?**
A: Explicit connect/read timeouts on every synchronous REST client (except prescription-service's unused one), the transactional outbox pattern on order-service and payment-service, the idempotent-consumer/inbox pattern independently implemented in four services, an idempotency-key pattern on payment-service's synchronous API, and a `CacheErrorHandler` for graceful Redis-failure degradation in product-service. It does **not** implement circuit breakers, bulkheads, or dead-letter topics, despite Resilience4j being a declared (but unused) dependency in api-gateway.

**Q: Why is a circuit breaker useful on top of a timeout, and why might it matter that this platform lacks one?**
A: A timeout bounds how long you wait for *one* call to fail, but if a downstream service is completely down, every single caller still pays that full timeout on every single request, which can exhaust connection pools/threads under load. A circuit breaker detects the failure pattern and starts failing fast (no network call at all) until the dependency shows signs of recovery. Without one here, e.g. if inventory-service goes down, every order-creation request will still individually wait out its full 5s connect / 10s read timeout before failing — a real, if bounded, cascading-latency risk under load.

**Q: Explain the transactional outbox pattern using this codebase's actual implementation.**
A: When `OrderService.createOrder()` runs, it persists the `Order` row and an `OutboxEvent` row (containing the serialized `OrderCreatedEvent` payload and target topic) inside the same `@Transactional` method — so both commit atomically or neither does. A separate `@Scheduled(fixedDelay=5000)` `OutboxPublisher` component polls for unpublished outbox rows and calls `KafkaTemplate.send(...)`, decoupling the Kafka publish (which could be slow or fail) from the original request's latency and correctness.

**Q: What happens today if the outbox publisher's Kafka send permanently fails (e.g., Kafka is down for an hour)?**
A: The `OutboxPublisher.publishPendingEvents()` catches the exception, prints it to `System.err`, and simply tries again on the next 5-second tick — indefinitely. There's no exponential backoff, no alerting threshold, and no way for an operator to be notified that events are piling up unpublished — a real operational gap worth naming if asked "how would you improve this."

**Q: Why do four different services independently implement the same idempotent-consumer pattern instead of sharing one implementation?**
A: Each service owns its own database exclusively (a stated platform rule: no service reads another service's tables), so the `ProcessedEvent` table itself can't be shared — but the *code* implementing the check-then-act-then-record logic could reasonably be extracted into a small shared library, since right now it's duplicated with slightly different shapes (`ProcessedEvent(orderId, eventId)` in order-service vs a bare `ProcessedEvent(eventId)` elsewhere).

**Q: Is retrying always safe? What's the precondition?**
A: Only for operations that are idempotent (or wrapped in idempotency machinery). Retrying a non-idempotent operation (e.g., "charge the card" without an idempotency key) risks duplicating the side effect. This is exactly why payment-service's `processPayment` checks `idempotencyKey` first — it makes the whole operation safely retryable from the client's perspective.

**Q: What's a dead-letter topic, and does this system have one?**
A: A DLT is a separate Kafka topic where messages that fail processing after exhausting retries are routed for manual inspection, alerting, or replay — instead of being silently dropped. This system does **not** have one: `order-service`'s `DefaultErrorHandler()` and `audit-service`'s `max-attempts: 3` binder config both only bound the retry count; neither configures a `DeadLetterPublishingRecoverer` (Spring Kafka) or `enableDlq`/`dlq-name` (Spring Cloud Stream). A permanently-malformed or poison message is retried a few times, logged, and then effectively lost.

**Q: If asked to add exactly one resilience improvement to this platform, what would you pick and why?**
A: A dead-letter topic for order-service's and payment-service's Kafka consumers, since a poison/malformed event today just disappears with no recovery path — that's a correctness and observability gap more severe than the missing circuit breaker (which is a performance/cascading-failure concern, not a silent-data-loss one).

## Gotchas / real findings

- **Resilience4j is a dead dependency.** `api-gateway/pom.xml` declares both `spring-cloud-starter-circuitbreaker-resilience4j` and `resilience4j-core`, but zero `@CircuitBreaker` annotations, zero `CircuitBreakerFactory` usage, and zero resilience4j configuration exist anywhere in the codebase (verified via full source search). This strongly suggests circuit breaking was planned but never implemented.
- **No dead-letter topic/queue anywhere.** Neither the raw-Kafka services (order, payment) nor the Spring Cloud Stream services (notification, audit) configure a DLT — failed messages are retried a bounded number of times and then silently dropped.
- **Outbox publish failures have no backoff, alerting, or dead-lettering.** `OutboxPublisher` retries via its fixed 5-second schedule forever, logging only to stderr — an indefinitely-stuck outbox row (e.g., a permanently malformed payload) would retry forever with no operator visibility.
- **No bulkhead / thread-pool isolation between dependencies.** A slow or hung inventory-service could still consume order-service's shared HTTP client capacity, since there's no per-dependency execution isolation.
- **`prescription-service`'s injected `RestTemplate` has no timeout configuration** (confirmed absent in the 04 guide's investigation) — combined with the fact that it's currently unused for any real outbound call, this is low-risk today but would be a real bug the moment prescription-service's external verification is actually implemented.
- **The only genuine graceful-degradation implementation in the platform is product-service's `CacheErrorHandler`** (fixed during a prior session in this learning series) — every other synchronous dependency across the fleet fails hard on error rather than degrading gracefully.

## Trace-through: what happens when inventory-service is completely down during order creation

1. Client calls `POST /api/v1/orders`.
2. `OrderService.createOrder()` loops through items and calls `InventoryAvailabilityClient.checkAvailability(...)`.
3. The underlying `RestTemplate` attempts to connect to `inventory-service:8085`; since it's down, the connection attempt fails — but only after up to 5 seconds (the configured `connectTimeout`), not instantly.
4. `RestClientException` is thrown and wrapped as `InventoryAvailabilityException`.
5. `OrderService.createOrder()` has no try/catch around this call, so the exception propagates up to the controller, which (via `GlobalExceptionHandler`) turns it into a 5xx `ProblemDetail` response — the order is never persisted, so no partial/inconsistent state is left behind (a small win: the DB transaction never opened until after all availability checks succeed).
6. Because there is no circuit breaker, every single concurrent order-creation request during the outage independently pays the same up-to-5-second timeout cost — under load, this can exhaust order-service's HTTP client connection pool even though each individual failure is correctly bounded.
7. Because there is no graceful degradation path (unlike product-service's Redis handling), there is no fallback behavior (e.g., "assume available and let a later async check reconcile") — the order is simply rejected outright.
