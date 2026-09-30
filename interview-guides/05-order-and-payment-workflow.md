# 05 — Order and Payment Workflow (Prompt 05)

## Summary

The current order/inventory/payment path is a working Kafka choreography saga, not an order/payment-only loop. Order persists `OrderCreated` in its outbox; inventory consumes it and publishes `InventoryReserved` or `InventoryRejected`; payment consumes a reservation and publishes the payment outcome; order consumes both inventory and payment results. Inventory consumes payment outcomes to commit or release stock. A synchronous inventory availability check still precedes order creation, but it is not the reservation.

## Diagram — actual flow

```text
POST /api/v1/orders (Idempotency-Key)
  -> OrderService checks inventory availability via GET /api/v1/inventory/availability
  -> validates item total, persists order + OrderCreated outbox in one DB transaction
  -> order outbox relay -> pharmacy.order.events.v1
       -> inventory OrderEventConsumer -> reserve stock or reject
          -> inventory outbox -> pharmacy.inventory.events.v1
               -> order OrderEventConsumer: INVENTORY_PENDING -> PAYMENT_PENDING
                  or CANCELLED_INVENTORY
               -> payment InventoryEventConsumer on InventoryReserved:
                  look up order -> process simulated payment (idempotency key "order:<id>")
                  -> payment outbox -> pharmacy.payment.events.v1
                       -> order: CONFIRMED or CANCELLED_PAYMENT
                       -> inventory: commit reservation or release stock and publish
                          InventoryReleased to pharmacy.inventory.events.v1
       -> notification builds order/customer projection; audit records order event
```

Payment also exposes a direct API for explicitly initiated payments/refunds; that API does **not** replace the automatic `InventoryReserved` trigger in the saga. The payment gateway is an in-cluster external mock in the canonical local deployment. The inventory pre-check can reject before persisting an order; the later reservation is authoritative under contention.

## State and delivery boundaries

```text
CREATED -> INVENTORY_PENDING -> INVENTORY_RESERVED -> PAYMENT_PENDING -> CONFIRMED
                  |                                             |
                  +-> CANCELLED_INVENTORY                         +-> CANCELLED_PAYMENT
CONFIRMED -> READY_FOR_PICKUP -> COMPLETED
```

`Order.transitionTo` guards illegal transitions. On reservation, `processInventoryReserved` makes two transitions in one transaction (`INVENTORY_RESERVED`, then `PAYMENT_PENDING`). An inventory rejection cancels without charging; a failed payment cancels and releases the reservation. Scheduled outbox relays await the Kafka send future with a 10-second bound and mark rows published only after acknowledgement; failed rows retain failure/attempt information for subsequent sweeps. This is at-least-once delivery, not atomic MySQL/Kafka commit.

## Key files

| File | What to study |
|---|---|
| `services/order-service/.../application/service/OrderService.java` | Idempotency key/request hash, pre-check, outbox, guarded saga transitions and processed events. |
| `services/order-service/.../infrastructure/kafka/{OrderEventConsumer,OutboxPublisher}.java` | Inventory/payment listeners and scheduled publication. |
| `services/inventory-service/.../application/service/InventorySagaService.java` | Atomic reservation/rejection, payment compensation, processed-event records and acknowledged outbox sends. |
| `services/inventory-service/.../infrastructure/kafka/{OrderEventConsumer,PaymentEventConsumer}.java` | Kafka choreography inputs. |
| `services/payment-service/.../infrastructure/InventoryEventConsumer.java` | Starts payment from `InventoryReserved` with an order-derived idempotency key and order lookup. |
| `services/payment-service/.../application/PaymentService.java` | Simulated gateway, direct API idempotency and payment outbox. |
| `docs/07-event-contracts.md` | Intended versioned topic and envelope contracts; compare with emitted flat event payloads. |

## Core concepts

| Concept | Why it matters here |
|---|---|
| Choreography saga | Each service owns its transaction and reacts to events; order owns status, inventory owns stock/reservations, payment owns payment records. No cross-service rollback. |
| Transactional outbox | A business record and event row commit together; relays later publish and wait for broker acknowledgement. A crash after send but before marking published can duplicate a message. |
| Inbox / processed event | Each consumer records `eventId` in its own database with the business effect; duplicate delivery cannot repeat a reservation or state change. Payment's order-derived idempotency key also protects the charge path. |
| State pattern | A guarded enum transition rejects invalid jumps instead of relying on controller call order. |
| Money | Amounts use `BigDecimal` and currency; order currently validates client item totals with a 0.01 tolerance. Do not describe client prices as universally authoritative. |

## Common interview Q&A

**Q: What happens after an order is created?**
A: Order commits the order and `OrderCreated` outbox row. Inventory consumes it, atomically reserves or rejects and emits an inventory result. Payment starts on `InventoryReserved` and publishes its result; order transitions on inventory and payment outcomes. If payment fails, inventory releases reserved stock. Notification and audit independently consume their configured domain events.

**Q: Why have a synchronous availability check if reservation is asynchronous?**
A: It rejects obviously unavailable requests early, but cannot guarantee stock under concurrency. The inventory consumer's transactional reservation is the authoritative step; an `InventoryRejected` cancels an already-created order.

**Q: Why not send to Kafka in the order transaction?**
A: MySQL and Kafka cannot commit atomically here. Persisting an outbox row with the order avoids losing the intent to publish; the relay waits for acknowledgement, retries failures, and consumers tolerate duplicates.

**Q: How is a timed-out payment protected from double charging?**
A: The direct payment API checks its idempotency key before reprocessing. The saga-triggered path derives a stable key from the order ID, so re-delivery of `InventoryReserved` reuses the payment; event consumers also use processed-event records.

**Q: What does `CANCELLED_PAYMENT` mean?**
A: Payment failed after inventory reservation; order records the reason separately from `CANCELLED_INVENTORY`, while inventory compensates by releasing stock.

## Gotchas and trace-through

- The pre-check is not a hold. Race safety belongs to the reservation transaction and stock concurrency controls.
- The event contract describes a common envelope, while some existing order/inventory/payment producers send flat event JSON. Audit normalizes those payloads; do not claim every producer already emits the envelope.
- Raw Spring Kafka listeners in order/inventory/payment use `DefaultErrorHandler`; do not equate the stream binder's configured DLQs with a proven DLT recoverer for every raw listener.

**Duplicate `PaymentCompleted`:** order's `processPaymentCompleted` checks `(orderId,eventId)` before changing the status and stores the processed record within the same transaction. Re-delivery leaves the order confirmed without an extra status transition.
