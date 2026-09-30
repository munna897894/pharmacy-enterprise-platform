# 04 — Inventory and Prescription (Prompt 04)

## Summary

Inventory owns stock levels **and** order-driven reservations: it consumes `OrderCreated`, publishes `InventoryReserved` or `InventoryRejected` via its transactional outbox, and commits/releases reservations on payment results. Prescription still implements a local `PENDING -> ACTIVE -> FILLED/EXPIRED` lifecycle; its `RestTemplate` is not used for external verification and it does not publish `PrescriptionVerified`/`PrescriptionRejected`. Notification/audit subscribe to the prescription topic, but those bindings do not create events.

## Diagram

```text
order -> GET inventory /api/v1/inventory/availability (early pre-check)
order outbox -> OrderCreated -> inventory Kafka consumer
  -> reserve stock + reservation row + InventoryReserved outbox
     or InventoryRejected outbox
  -> payment succeeds: commit reservation
     payment fails: release stock + InventoryReleased outbox

client -> prescription create -> activate -> fill line(s) -> FILLED
                                      -> expiry sweep -> EXPIRED
       (no current outbound verification or prescription Kafka publication)
```

## Key files

| File | What it shows |
|---|---|
| `services/inventory-service/.../application/service/InventoryService.java` | Direct stock CRUD/adjustments, availability and low-stock queries. |
| `services/inventory-service/.../application/service/InventorySagaService.java` | Reservation/rejection, compensation, inbox and broker-acknowledged outbox relay. |
| `services/inventory-service/.../infrastructure/kafka/{OrderEventConsumer,PaymentEventConsumer}.java` | Order/payment listener paths. |
| `services/inventory-service/.../domain/model/{StockLevel,InventoryReservation}.java` | Optimistic stock versioning and per-order reservation states. |
| `services/order-service/.../infrastructure/client/InventoryAvailabilityClient.java` | Pre-check uses `/api/v1/inventory/availability` with query parameters; not the old pharmacy/product stock endpoint. |
| `services/prescription-service/.../application/service/PrescriptionService.java` | Current local create/activate/fill/expire flow; UUID format checks are not external identity verification. |
| `services/prescription-service/.../domain/model/{Prescription,PrescriptionLine}.java` | Aggregate and partial fill logic. |

## Core concepts

| Concept | Why it matters |
|---|---|
| Pre-check vs reservation | A read of available quantity cannot hold stock. Inventory's transactional consumer makes the actual reservation and can reject under contention. |
| Optimistic locking | `StockLevel` uses `@Version` to prevent silent lost updates; do not rely on the earlier HTTP read for concurrency safety. |
| Compensation | Payment failure releases held stock and emits `InventoryReleased`, instead of rolling back another service's committed database transaction. |
| Inbox/outbox | Inventory records a processed `eventId` alongside stock/reservation changes and publishes result events later, waiting for broker acknowledgement. |
| Prescription aggregate | Lines can be partially filled while the prescription remains active; the aggregate becomes filled only when all lines are complete. |

## Common interview Q&A

**Q: Does inventory reserve stock on orders?**
A: Yes. The order service first calls a synchronous availability endpoint, then publishes `OrderCreated`; inventory's Kafka consumer reserves or rejects and emits the result. A synchronous pre-check is not the hold.

**Q: What happens if payment fails after reservation?**
A: Inventory consumes the failed payment event, restores stock, marks the reservation released and emits `InventoryReleased`. Order separately transitions to `CANCELLED_PAYMENT`.

**Q: How does inventory handle duplicate `OrderCreated` events?**
A: It checks its processed-event record and also checks for an existing reservation by order ID; both the mutation and event record are in its local transaction.

**Q: What does prescription actually verify?**
A: Today creation validates dates and identifier *format*, and staff can activate/fill prescriptions. No real external verifier is called in `PrescriptionService`; there is no verification-result producer, despite the intended contracts and downstream topic subscriptions.

**Q: Why keep line and prescription states separate?**
A: A multi-line prescription may be partially dispensed. The aggregate decides when all lines are complete.

## Gotchas / trace-through

- `GET /api/v1/inventory/availability` is the current pre-check contract. Older descriptions of a request to `/pharmacies/{id}/products/{id}` returning an availability DTO are historical.
- The inventory topic has a real outbox producer. Prescription's configured downstream consumers remain unfed by a prescription-service producer; do not merge these two states into one "no producers" claim.
- Inventory uses `productId` in its stock model while order uses `medicationId`; verify ID semantics at the boundary rather than assuming matching Java property names.

**Trace:** create order -> pre-check -> `OrderCreated` -> inventory transaction reserves stock and writes outbox -> relay waits for acknowledgement of `InventoryReserved` -> order advances to payment pending; on a failed payment, inventory releases stock and emits `InventoryReleased`.
