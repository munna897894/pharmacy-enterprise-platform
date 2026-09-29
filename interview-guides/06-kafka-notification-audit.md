# 06 — Kafka, Notification, and Audit (Prompt 06)

## Summary

Two consumer-only services (`notification-service`, `audit-service`) sit at the "read side" of the platform's events, both using **Spring Cloud Stream functional bindings** (`Consumer<T>` beans) rather than raw `@KafkaListener` annotations — a different style from order/payment-service (see the 05 guide). notification-service is fully wired and functional for the order/payment legs of the event stream; audit-service, despite being fully implemented internally, is **currently disconnected from real events** due to a topic-naming mismatch — the single most important finding in this guide.

## Diagram — the two consumer services' real wiring

```
                      Kafka topics actually published to:
                      pharmacy.order.events.v1      (order-service, real)
                      pharmacy.payment.events.v1    (payment-service, real)
                      pharmacy.inventory.events.v1  (nobody publishes — see 04/05 guides)
                      pharmacy.prescription.events.v1 (nobody publishes)

  notification-service bindings (application.yml, spring.cloud.stream.bindings):
  ┌───────────────────────────────┬─────────────────────────────────┬────────┐
  │ Function bean                 │ destination                     │ status │
  ├───────────────────────────────┼─────────────────────────────────┼────────┤
  │ orderCreatedConsumer-in-0      │ pharmacy.order.events.v1        │ ✅ real │
  │ orderShippedConsumer-in-0      │ pharmacy.order.events.v1        │ ✅ real (see gotcha: same topic+group as above) │
  │ paymentProcessedConsumer-in-0  │ pharmacy.payment.events.v1      │ ✅ real │
  │ prescriptionFilledConsumer-in-0│ pharmacy.prescription.events.v1 │ ⚠ orphaned (no producer) │
  │ prescriptionExpiringConsumer-in-0│ pharmacy.prescription.events.v1│ ⚠ orphaned (no producer) │
  │ lowStockConsumer-in-0          │ pharmacy.inventory.events.v1     │ ⚠ orphaned (no producer) │
  └───────────────────────────────┴─────────────────────────────────┴────────┘

  audit-service bindings (application.yml):
  ┌───────────────────────────────┬──────────────────────────────────┬────────┐
  │ Function bean                 │ destination                       │ status │
  ├───────────────────────────────┼──────────────────────────────────┼────────┤
  │ productEventConsumer-in-0      │ product-service.product.events    │ ⚠ ORPHANED │
  │ customerEventConsumer-in-0     │ customer-service.customer.events  │ ⚠ ORPHANED │
  │ orderEventConsumer-in-0        │ order-service.order.events         │ ⚠ ORPHANED (real orders publish to pharmacy.order.events.v1, NOT this name) │
  │ paymentEventConsumer-in-0      │ payment-service.payment.events     │ ⚠ ORPHANED (real payments publish to pharmacy.payment.events.v1) │
  │ prescriptionEventConsumer-in-0 │ prescription-service.prescription.events │ ⚠ ORPHANED │
  │ inventoryEventConsumer-in-0    │ inventory-service.inventory.events │ ⚠ ORPHANED │
  └───────────────────────────────┴──────────────────────────────────┴────────┘

  Every audit-service binding uses the naming pattern "<service>-service.<domain>.events".
  Every real producer in this codebase uses "pharmacy.<domain>.events.v1".
  These NEVER match — audit-service's Kafka consumers are fully implemented,
  correctly coded, unit-testable... and permanently silent in the real running
  system, because nothing ever publishes to the topic names they're bound to.
```

## Key files

| File | What it shows |
|---|---|
| `notification-service/.../messaging/OrderEventConsumer.java` | Two `@Bean Consumer<DomainEvent<T>>` methods (`orderCreatedConsumer`, `orderShippedConsumer`) in one `@Component` class; each checks `ProcessedEventRepository.existsById(eventId)` before acting (idempotent consumer), builds a template-variable `Map<String,String>`, and calls `NotificationService.sendNotification(customerId, type, channels, variables)`. |
| `notification-service/.../application/NotificationService.java` | `List<NotificationSender> senders` injected — Spring autowires every `@Component implements NotificationSender` bean into this list; `publishNotification` picks the sender whose `supports(channel)` returns true. This is the **Strategy pattern**. |
| `notification-service/.../infrastructure/channel/{Email,Sms,InApp}Sender.java` | Three concrete strategies implementing `NotificationSender { void send(Notification); boolean supports(Channel); }` — adding a new channel (e.g., push notifications) means adding one new class, zero changes to `NotificationService`. Textbook **Open/Closed Principle**. |
| `audit-service/.../kafka/KafkaConsumerConfig.java` | A single `@Configuration` class with six `@Bean @ConditionalOnMissingBean(name="...")` methods, each returning a `Consumer<Message<String>>` that delegates to a `handleXxxEvent(message)` private method — this is where the functions actually get registered as Spring Cloud Stream bindings (the individual `XxxEventConsumer` classes hold the parsing/business logic but aren't beans themselves). |
| `audit-service/.../kafka/BaseEventConsumer.java` | Shared parent class with `isEventProcessed`/`markEventAsProcessed`/`recordAuditLog` helpers — every concrete consumer (`ProductEventConsumer`, `OrderEventConsumer`, etc.) extends this instead of duplicating the idempotency-check boilerplate six times. **Template Method**-flavored base class. |
| `audit-service/.../domain/AuditLog.java` | JPA entity with three composite indexes (`aggregate_id+timestamp`, `user_id+timestamp`, `resource_type+timestamp+action`) and a `@Lob details` column typed as MySQL `JSON` — built for exactly the query patterns an audit trail needs ("show me everything for this order," "show me everything this user did," "show me every DELETE"). |
| `notification-service/.../application.yml` | The 6 Spring Cloud Stream bindings table above — correct topic names, but 3 of 6 are orphaned due to missing upstream producers (not a naming bug like audit-service, just a missing-producer gap shared with the 04/05 guides). |
| `audit-service/.../application.yml` | The 6 bindings using the wrong topic-naming convention — the concrete evidence for this guide's central finding. |

## Core concepts

| Concept | Explanation | Why it matters |
|---|---|---|
| Spring Cloud Stream functional binding model | A plain `java.util.function.Consumer<T>` (or `Function`/`Supplier`) exposed as a `@Bean`; Spring Cloud Stream's binder auto-wires it to a Kafka topic based on `spring.cloud.stream.bindings.<beanName>-in-0.destination`. | Decouples business/consumer logic entirely from any messaging-library-specific annotation (`@KafkaListener`) — you could swap the binder (Kafka → RabbitMQ) without touching the `Consumer<T>` implementation. This is the functional/reactive-friendly alternative to annotation-driven consumers. |
| Strategy pattern (notification channels) | `NotificationSender` interface + `EmailSender`/`SmsSender`/`InAppSender` implementations, selected at runtime via `supports(channel)`. | Textbook use case for Strategy: the "algorithm" (how to deliver a notification) varies by channel, and new channels shouldn't require modifying existing dispatch code. |
| Idempotent consumer / inbox pattern (again) | Both services check a `ProcessedEvent` (by `eventId`) table before acting and record it after acting — same pattern as order/payment-service (05 guide), independently reimplemented here rather than shared as a library. | Reinforces that Kafka's at-least-once delivery guarantee is a platform-wide concern, not specific to the order/payment saga — but also shows the pattern is duplicated across 4 services instead of extracted into a shared module, a real "would you refactor this?" discussion point. |
| Template Method (via `BaseEventConsumer`) | `isEventProcessed`/`markEventAsProcessed`/`recordAuditLog` live once in the base class; each subclass only implements the event-specific parsing + field mapping. | Removes duplicate idempotency-check boilerplate across six nearly-identical consumer classes — a lightweight version of the Template Method pattern using composition-by-inheritance rather than abstract method hooks. |
| Audit log as an append-only, indexed read model | `AuditLog` is never updated or deleted, only inserted; three composite indexes exist purely to serve expected query patterns. | This is the standard shape of an audit/event-sourcing-adjacent read model — optimize for "append fast, query by (subject, time range)" rather than normalized relational structure. |
| `@ConditionalOnMissingBean(name = "...")` on Kafka bean definitions | Prevents duplicate bean registration if a consumer bean of that name were ever defined elsewhere (e.g., in tests). | A defensive Spring idiom for functional bindings — the bean *name* is what Spring Cloud Stream uses to resolve the binding, so accidental duplicate names are a real footgun this annotation guards against. |
| Multi-channel notification fan-out | `sendNotification(customerId, type, channels, variables)` accepts a `List<Channel>` and calls `publishNotification` once per channel. | One business event (e.g., `OrderShipped`) can trigger multiple simultaneous delivery channels (email + SMS) from a single call site — a common real-world notification-service requirement. |
| JSON column for `details` (`AuditLog.details`) | Stored as `@Lob` with `columnDefinition = "JSON"`, populated from `event.get("payload").toString()` (the raw event payload, unparsed). | Keeps the audit log schema-agnostic to whatever event payload shape each producer sends — trades query-ability *inside* that JSON blob for schema flexibility as producers evolve independently. |

## Common interview Q&A

**Q: What's the difference between `@KafkaListener` and the functional `Consumer<T>` bean style used here?**
A: `@KafkaListener` is Spring Kafka's annotation-driven model — you annotate a method with the topic/group directly, and Spring Kafka wires it. The functional style (used in notification-service and audit-service) instead defines a plain `Consumer<T>`/`Function<T,R>` bean and lets Spring Cloud Stream's binder resolve topic bindings from `application.yml` by matching the bean name — this decouples the code from any specific messaging technology and is Spring's more modern, binder-agnostic recommendation, though it means the topic name lives in config rather than in the code, which is exactly what caused audit-service's mismatch.

**Q: Why does audit-service never receive real events despite having fully implemented consumers?**
A: Its bindings target topic names following a `<service>-service.<domain>.events` convention (e.g., `order-service.order.events`), but the actual producers (order-service, payment-service) publish to `pharmacy.<domain>.events.v1` (e.g., `pharmacy.order.events.v1`). No producer in the codebase ever writes to the topic names audit-service listens on, so its consumers are permanently idle — the code is correct, the configuration is wrong.

**Q: How would you detect this kind of bug before it reached production?**
A: A contract test or a lightweight integration test that spins up an embedded/test Kafka broker, has each producer publish a real event, and asserts audit-service actually records an `AuditLog` row — this class of bug (config-level topic mismatch) is invisible to unit tests of either side in isolation and only surfaces with a real end-to-end or contract-level test.

**Q: Explain the Strategy pattern usage in notification-service.**
A: `NotificationSender` is the strategy interface (`send`, `supports`); `EmailSender`/`SmsSender`/`InAppSender` are concrete strategies, all auto-injected into `NotificationService` as a `List<NotificationSender>` by Spring. At dispatch time, the service iterates the list and picks whichever sender's `supports(channel)` returns true — adding a push-notification channel later requires zero changes to `NotificationService`, only a new `NotificationSender` implementation.

**Q: Why do both notification-service and audit-service re-implement the same idempotent-consumer check that order/payment-service already have?**
A: Each service owns its data, including its own `ProcessedEvent` table — sharing that table across services would violate the "no service reads another service's tables" rule. The pattern (check-before-act, mark-after-act) is duplicated in code across four services rather than extracted into a shared library; a legitimate refactor candidate would be a small shared utility class (not a shared *table*, since the DB must stay per-service) if this codebase grows more consumer services.

**Q: What's stored in `AuditLog.details` and why is it JSON rather than structured columns?**
A: It's the raw event payload JSON string (via `event.get("payload").toString()`), stored in a MySQL `JSON` column via `@Lob`. This keeps the audit schema stable even as individual event payloads evolve (new fields, new event types) — you trade the ability to query *inside* the payload via SQL for schema flexibility, which is an acceptable tradeoff for an audit trail whose primary access pattern is "look up everything for this aggregate/user/resource-type," not "filter by a specific payload field."

**Q: If you had to fix the audit-service gap with a one-line-per-service change, what would you do?**
A: Change audit-service's six `destination` values in `application.yml` to match the real topics (`pharmacy.order.events.v1`, `pharmacy.payment.events.v1`, etc.) — no code changes needed, since the consumer classes already parse `eventType`/`aggregateId`/`payload` generically from the JSON. The two orphaned notification-service consumers (inventory/prescription) and the still-nonexistent inventory/prescription producers are a separate, larger gap requiring actual new producer code in those services (see 04/05 guides).

## Gotchas / real findings

- **audit-service's Kafka topic names don't match any real producer** — the platform's entire audit trail is currently non-functional end-to-end, despite fully correct consumer code. This is the single highest-value finding in this guide and a great "I found and can explain a real production-blocking config bug" interview story.
- **notification-service's `orderCreatedConsumer` and `orderShippedConsumer` are bound to the exact same topic (`pharmacy.order.events.v1`) and the exact same consumer group (`notification-order-events-v1`)**, but with different generic payload types (`DomainEvent<OrderCreatedPayload>` vs `DomainEvent<OrderShippedPayload>`). Functionally, Spring Cloud Stream registers each as its own binder consumer; whether both receive every message or Kafka partitions the traffic between them depends on binder/partition-assignment behavior that isn't obvious from the config alone — worth flagging as a subtlety to verify with a live test rather than assuming either behavior. The same duplicate-group pattern repeats for `prescriptionFilledConsumer`/`prescriptionExpiringConsumer`.
- **The inbox/idempotency pattern is duplicated four times** (order-service, payment-service, notification-service, audit-service) with near-identical logic instead of being extracted into a shared library — acceptable given per-service data ownership rules, but a legitimate "what would you refactor" answer.
- Everything else in these two services — the Strategy pattern for notification channels, the audit log's indexing strategy, the idempotent-consumer implementation itself — is solid, correctly implemented, and free of the kind of bugs found elsewhere in the platform.

## Trace-through: an `OrderShipped` event that never gets audited

1. (Hypothetically) order-service marks an order as shipped and publishes an `OrderShipped` event to `pharmacy.order.events.v1`.
2. notification-service's `orderShippedConsumer` picks it up, checks `ProcessedEventRepository`, sends the SMS/email — this half works today (assuming order-service actually has a "mark shipped" path that publishes; verify against the real `OrderStatus` enum in the 05 guide before assuming this literally exists).
3. audit-service's `orderEventConsumer` is listening on `order-service.order.events` — a topic name nobody ever produces to.
4. Result: the customer gets notified, but there is **no audit record** that the shipment event ever happened — a silent, permanent gap between "the business event occurred" and "the compliance/audit trail reflects it," exactly the kind of thing a healthcare-adjacent (pharmacy) platform's audit story cannot afford to have wrong.
