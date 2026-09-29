# 05 — Order and Payment Workflow (Prompt 05)

## Summary

order-service and payment-service implement a **hybrid** saga: part synchronous REST (inventory availability check), part real event-driven choreography (order↔payment), with a transactional outbox on both sides and an idempotent-consumer ("processed event") table on the receiving side of every Kafka listener. This is the most event-heavy pair of services in the platform, and also the pair where "what the docs say" and "what the code does" diverge the most — study the real flow below, not the idealized three-way saga.

## Diagram — the REAL order lifecycle, step by step

```
1. POST /api/v1/orders  (OrderController -> OrderService.createOrder)
      │
      ▼
2. FOR EACH item: synchronous REST GET to inventory-service
   InventoryAvailabilityClient.checkAvailability(pharmacyId, medicationId, qty)
   -> if any item unavailable, throw IllegalArgumentException (400) — order never persisted
      │  (all items available)
      ▼
3. Validate calculatedTotal vs request.total (±0.01 rounding tolerance)
      │
      ▼
4. Persist Order (status = CREATED) + OrderItems  [DB transaction #1 start]
      │
      ▼
5. order.transitionTo(INVENTORY_PENDING)              <- state machine guard
      │
      ▼
6. Build OrderCreatedEvent, INSERT into outbox_event table
   (topic literal: "pharmacy.order.events.v1", aggregateType "Order")
      │
      ▼  [DB transaction #1 commits — order row + outbox row committed atomically]
      │
      ▼  (separate thread, every 5s)
7. OutboxPublisher.@Scheduled(fixedDelay=5000) -> OrderService.publishOutboxEvents()
      │  reads unpublished outbox rows, KafkaTemplate<String,String>.send(topic, key=orderId, payload)
      ▼
   Kafka topic: pharmacy.order.events.v1
      │                                   │
      ▼                                   ▼
 notification-service                (nothing else consumes this for the
 (orderCreatedConsumer)               "reserve inventory" step — see gap below)
 sends ORDER_CONFIRMATION email/in-app

── The originally-designed next saga step ("inventory reserves stock, emits
   InventoryReserved/InventoryRejected") DOES NOT EXIST in the code. ──

8. order-service DOES have a live listener waiting for it:
   OrderEventConsumer.onInventoryEvent() @KafkaListener("pharmacy.inventory.events.v1")
   -> would call processInventoryReserved()/processInventoryRejected()
   -> but inventory-service never publishes to this topic, so this path is
      permanently idle in the current codebase (see 04 guide for the inventory side).

9. Payment is instead triggered by a DIRECT CLIENT CALL (frontend / Postman /
   API consumer) to payment-service, e.g. POST /api/v1/payments, NOT by an
   event. payment-service:
      a. PaymentService.processPayment(request, idempotencyKey)
      b. checks paymentRepository.findByIdempotencyKey(...) first — if found,
         returns the existing payment instead of double-charging (idempotent
         write, not just idempotent read)
      c. OrderLookupClient -> REST GET to order-service (fetches order to
         validate amount/currency/status before charging)
      d. PaymentGatewayClient -> REST POST to external-mock-service (simulates
         a real payment gateway: tok_success / tok_fail / tok_delay tokens)
      e. Persists Payment (SUCCESS or FAILED), writes an OutboxEvent
         ("PaymentProcessedEvent") in the SAME transaction
      f. publishOutboxEvents() sends to Kafka topic "pharmacy.payment.events.v1"
         via KafkaTemplate<String,Object>
      ▼
   Kafka topic: pharmacy.payment.events.v1
      │                                  │
      ▼                                  ▼
 order-service                     notification-service
 OrderEventConsumer.onPaymentEvent  (paymentProcessedConsumer)
 -> processPaymentCompleted()       sends payment notification
 -> transitions order:
    SUCCESS -> CONFIRMED
    FAILED  -> CANCELLED_PAYMENT
 ✅ THIS LOOP IS REAL, FUNCTIONAL, AND IDEMPOTENT (see idempotency below)

10. Later, staff calls markOrderReadyForPickup() (CONFIRMED -> READY_FOR_PICKUP)
    and completeOrder() (READY_FOR_PICKUP -> COMPLETED); these are plain
    synchronous REST calls, not event-driven.
```

## Order status state machine

```
CREATED -> INVENTORY_PENDING -> INVENTORY_RESERVED -> PAYMENT_PENDING -> CONFIRMED
                  │                                          │
                  └──────────> CANCELLED_INVENTORY            └──────> CANCELLED_PAYMENT
CONFIRMED -> READY_FOR_PICKUP -> COMPLETED
```
Every transition goes through `Order.transitionTo(newStatus)`, which calls a private `isValidTransition(from, to)` guard and throws `InvalidOrderStateException` on an illegal jump — this is the **State pattern** implemented as a guarded enum transition rather than a full class-per-state hierarchy (a common, simpler variant for a small number of states).

Note the real code path for a successful order actually double-transitions in one method: `processInventoryReserved()` calls `transitionTo(INVENTORY_RESERVED)` immediately followed by `transitionTo(PAYMENT_PENDING)` in the same method — both transitions must be valid per the guard, or the whole `@Transactional` method rolls back.

## Key files

| File | What it shows |
|---|---|
| `order-service/.../application/service/OrderService.java` | The whole saga's brain: `createOrder` (sync inventory check + outbox write), `processInventoryReserved/Rejected`, `processPaymentCompleted` (all three guarded by `processedEventRepository.existsByOrderIdAndEventId(...)` for idempotency), `publishOutboxEvents()`. |
| `order-service/.../infrastructure/kafka/OutboxPublisher.java` | `@Scheduled(fixedDelay = 5000)` component — polls the outbox every 5s and calls back into the service to publish. Errors are only `System.err.println`'d (worth flagging — see Gotchas). |
| `order-service/.../infrastructure/kafka/OrderEventConsumer.java` | Raw `@KafkaListener(topics="pharmacy.inventory.events.v1", groupId="order-inventory-result-v1")` and a second listener on `pharmacy.payment.events.v1`; manually parses JSON via `ObjectMapper.readTree` and branches on an `eventType` string field rather than using a typed envelope/deserializer. |
| `order-service/.../domain/OrderStatus.java` + `Order.java` | The 9-state enum and the `transitionTo`/`isValidTransition` guard — the state machine pattern. |
| `payment-service/.../application/PaymentService.java` | `processPayment(request, idempotencyKey)` — checks `findByIdempotencyKey` FIRST (idempotent write pattern, not just idempotent read), then calls `OrderLookupClient` + `PaymentGatewayClient`, persists `Payment`, writes to outbox, later `publishOutboxEvents()` sends via `KafkaTemplate<String,Object>`. |
| `payment-service/.../infrastructure/client/PaymentGatewayClient.java` | REST client to `external-mock-service` simulating gateway responses (success/fail/delay tokens) — lets the whole payment path be tested without a real payment processor. |
| `*/infrastructure/ProcessedEvent*.java` (both services) | The **inbox/idempotent-consumer pattern**: a small table keyed by `(orderId, eventId)` (order-service) or unique `eventId` (elsewhere) that every consumer checks before processing and writes after processing, inside the same `@Transactional` boundary as the business update. |

## Core concepts

| Concept | Explanation | Why it matters |
|---|---|---|
| Transactional Outbox pattern | The order/payment row and its corresponding `OutboxEvent` row are written in the **same local DB transaction**; a separate poller publishes to Kafka afterward. | Solves the "dual write" problem — you cannot atomically commit to MySQL and publish to Kafka in one distributed transaction, so the outbox guarantees "if the business change is durable, the event WILL eventually be published," at the cost of at-least-once delivery (duplicates are possible if the publisher crashes after sending but before marking the row published). |
| Idempotent consumer / inbox pattern | Every Kafka listener checks a `ProcessedEvent` table keyed by `eventId` (or `orderId+eventId`) before doing any work, and records the event as processed in the same transaction as the business mutation. | At-least-once Kafka delivery means the same message can arrive twice (consumer rebalance, retry after a transient failure, etc.) — without this pattern a duplicate `PaymentCompleted` message could double-confirm or double-cancel an order. |
| Idempotent write via idempotency key (payment-service) | `processPayment` looks up `findByIdempotencyKey` before charging; if found, it returns the existing result instead of creating a second `Payment`. | Distinct from Kafka-consumer idempotency — this protects the **synchronous API** itself (e.g., a client retry after a timeout) from double-charging a customer. Two different idempotency mechanisms solving two different failure modes. |
| State pattern (guarded transitions) | `Order.transitionTo()` + `isValidTransition()` enforce a fixed graph of legal status changes; illegal jumps throw `InvalidOrderStateException`. | Prevents invalid states (e.g., completing an order that was never paid) from ever being persisted, without needing a full State-pattern class hierarchy for only 9 states. |
| Choreography saga (partial) | Services react to each other's events instead of a central orchestrator; here only the payment↔order leg is real. | Real interview point: you can accurately describe *why* choreography was chosen (no single point of failure, services stay decoupled) while honestly noting this implementation is incomplete against that ideal. |
| Manual JSON parsing vs typed events | order-service and payment-service parse Kafka messages by hand (`ObjectMapper.readTree(...).get("eventType").asText()`), unlike notification/audit which use a typed `DomainEvent<T>` envelope with Spring Cloud Stream. | Shows two different maturity levels of the same pattern coexisting in one codebase — a realistic finding for a system built incrementally, and worth being able to explain the tradeoff (typed envelopes are safer/refactor-friendly; raw JSON parsing is more defensive against schema drift but loses compile-time safety). |
| Rounding-tolerant total validation | `calculatedTotal.subtract(request.getTotal()).abs().compareTo(new BigDecimal("0.01")) > 0` | Real-world monetary math needs an explicit epsilon comparison, never `equals()`, when values may have been rounded client-side. |
| `BigDecimal` + separate currency field for money | Every money field pairs a `BigDecimal` amount with a currency string. | Never use floating point for currency (binary floating point can't represent most decimal fractions exactly) — an industry-standard rule, and a stated platform rule here. |

## Common interview Q&A

**Q: Walk me through what happens, end to end, when a customer places an order and pays for it.**
A: (Use the diagram above — sync inventory check → order persisted with an outbox row in one transaction → 5s poller publishes `OrderCreated` → notification-service emails confirmation → a separate payment call happens against payment-service, which checks idempotency, calls order-service to validate, calls the mock gateway, persists the payment result with its own outbox row → publishes `PaymentCompleted`/`Failed` → order-service consumes that and transitions the order to `CONFIRMED` or `CANCELLED_PAYMENT`.)

**Q: What's the transactional outbox pattern, and why not just call `kafkaTemplate.send()` directly inside the same method that saves the order?**
A: Because the DB commit and the Kafka publish are two separate systems — if you call `kafkaTemplate.send()` inside the same method and the DB commit fails afterward (or vice versa: DB commits but the process crashes before the Kafka call), you get a permanent inconsistency. Writing an outbox row in the *same* local transaction as the business change means the event's existence is exactly as durable as the business change; a background poller then handles the actual publish, decoupled from request latency and safe to retry.

**Q: Is this system exactly-once or at-least-once?**
A: At-least-once end to end. The outbox poller can publish the same event twice if it crashes mid-cycle before marking a row as sent; Kafka itself is at-least-once by default. That's exactly why every consumer implements the idempotent-consumer/inbox pattern — "at least once delivery + idempotent processing" is the standard way to get effectively-exactly-once *outcomes* without needing exactly-once infrastructure.

**Q: How does payment-service prevent double-charging a customer who retries a timed-out request?**
A: `processPayment` takes a client-supplied `idempotencyKey`, looks it up first via `paymentRepository.findByIdempotencyKey(...)`, and returns the already-processed result if found instead of re-executing the charge — a distinct mechanism from the Kafka-side idempotent-consumer pattern, since this protects the *inbound synchronous API call*, not an event consumer.

**Q: Why does order-service have a listener for `pharmacy.inventory.events.v1` if nothing ever publishes to it?**
A: It reflects the originally-designed three-way choreography saga (order → inventory reserve → payment → confirm) that was never fully finished — inventory-service was never given Kafka producer code. The listener is harmless dead code today, but it's a real gap: as it stands, orders are never actually inventory-reserved asynchronously; the only inventory check is the synchronous pre-check at order-creation time, which has no accompanying "hold"/reservation, so a race condition between two concurrent orders for the same last unit of stock is theoretically possible.

**Q: Why does `OrderStatus` have both `CANCELLED_INVENTORY` and `CANCELLED_PAYMENT` instead of one generic `CANCELLED`?**
A: So the audit trail / status history preserves *why* an order was cancelled, which matters for customer support and reporting — collapsing both into one `CANCELLED` value would lose that distinction permanently once written to the DB.

**Q: What would you change first if asked to make this a "real" saga?**
A: Give inventory-service actual Kafka producer code (outbox + publish `InventoryReserved`/`InventoryRejected` to `pharmacy.inventory.events.v1`), move the availability check from a synchronous pre-check to an actual asynchronous reservation with a compensating cancel path, and only trigger payment-service from the `InventoryReserved` event rather than a direct external client call — that would close the loop the docs originally described.

## Gotchas / real findings

- **The inventory leg of the saga doesn't exist.** No inventory reservation event is ever produced; order-service's listener for it is permanently idle. This is the single biggest gap between the documented saga design and the real implementation.
- **Payment isn't saga-triggered.** It's invoked by a direct external REST call to payment-service, not by consuming an `InventoryReserved` event as the original design implied — worth being precise about this distinction in an interview.
- **Outbox publish failures are swallowed silently.** `OutboxPublisher.publishPendingEvents()` catches `Exception` and only does `System.err.println(...)` — there's no retry/backoff, no alerting, and no dead-letter handling if publishing consistently fails (e.g., Kafka is down for an extended period); the outbox rows would simply stay unpublished and get retried again in 5 seconds indefinitely, silently, with no operator visibility beyond stdout logs.
- **No synchronous inventory "hold"/lock at order time.** Two concurrent order requests for the last unit of the same medication could both pass the availability check before either decrements stock — a real race-condition risk, not just a stylistic gap, since the check-then-act isn't atomic across services today.
- **Two Kafka client styles used in the same two services.** order-service/payment-service both use raw `@KafkaListener`/`KafkaTemplate` with manual JSON parsing, unlike notification-service/audit-service's typed Spring Cloud Stream functional bindings — a legitimate architectural inconsistency worth naming if asked "is this system internally consistent?"

## Trace-through: a duplicate `PaymentCompleted` message

1. Kafka redelivers the same `PaymentCompleted` message to order-service (e.g., after a consumer rebalance mid-processing).
2. `OrderEventConsumer.onPaymentEvent()` parses it and calls `orderService.processPaymentCompleted(event)`.
3. Inside the `@Transactional` method, the very first line checks `processedEventRepository.existsByOrderIdAndEventId(event.orderId, event.eventId)` — since this exact `eventId` was already recorded on the first delivery, the method returns immediately without re-transitioning the order or writing a duplicate `OrderStatusHistory` row.
4. Net effect: the order stays `CONFIRMED` (not double-processed), proving the inbox pattern does its job even though Kafka's delivery guarantee is only at-least-once.
