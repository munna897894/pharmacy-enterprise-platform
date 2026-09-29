# Kafka event contracts

## Topic policy

Use domain topics plus retry/dead-letter topics. Initial local partition count is 3 for business topics and 1 for DLTs. Replication factor is 1 locally only.

| Topic | Key | Producers | Consumers |
|---|---|---|---|
| `pharmacy.prescription.events.v1` | prescriptionId | prescription-service | audit, notification |
| `pharmacy.order.events.v1` | orderId | order-service | inventory, notification, audit |
| `pharmacy.inventory.events.v1` | orderId | inventory-service | order, payment, audit |
| `pharmacy.payment.events.v1` | orderId | payment-service | order, inventory, notification, audit |
| `pharmacy.notification.events.v1` | notificationId | notification-service | audit |

Each source topic has `<topic>.retry` and `<topic>.dlt` for the educational retry design. Do not create infinite retries.

## Common envelope

```json
{
  "eventId": "uuid",
  "eventType": "OrderCreated",
  "eventVersion": 1,
  "aggregateType": "Order",
  "aggregateId": "uuid",
  "occurredAt": "2026-09-16T12:00:00Z",
  "producer": "order-service",
  "correlationId": "uuid",
  "causationId": "uuid-or-null",
  "actorId": "uuid-or-system",
  "payload": {}
}
```

Kafka headers also carry `eventId`, `eventType`, `correlationId`, `traceparent` when available, and `contentType=application/json`.

The common envelope remains the required format for new and updated producers.
For migration compatibility, audit-service currently normalizes the flat
order, inventory and payment payloads already emitted by this repository;
this consumer-side adapter does not change the producer contract or exempt
producers from adopting the envelope.

## Required event types and payloads

### PrescriptionVerified

```json
{
  "prescriptionId": "uuid",
  "customerId": "uuid",
  "medicationId": "uuid",
  "quantity": 30,
  "verifiedAt": "2026-09-16T12:00:00Z",
  "externalReference": "RX-MOCK-123"
}
```

### PrescriptionRejected

Payload: `prescriptionId`, `customerId`, `reasonCode`, `reason`, `rejectedAt`.

### OrderCreated

Payload: `orderId`, `customerId`, `prescriptionId`, `pharmacyId`, `currency`, `total`, `paymentToken`, and `items[{medicationId, quantity, unitPrice}]`.

The test token is acceptable in this educational event, but no real payment credentials are ever permitted. In a real system, even token propagation requires stricter boundaries.

### InventoryReserved

Payload: `orderId`, `reservationId`, `pharmacyId`, `items[{medicationId, quantity}]`, `reservedAt`, `expiresAt`.

### InventoryRejected

Payload: `orderId`, `pharmacyId`, `reasonCode`, `unavailableItems[{medicationId, requested, available}]`, `rejectedAt`.

### InventoryReleased

Payload: `orderId`, `reservationId`, `reasonCode`, `releasedAt`.

### PaymentCompleted

Payload: `paymentId`, `orderId`, `amount`, `currency`, `providerReference`, `completedAt`.

### PaymentFailed

Payload: `paymentId`, `orderId`, `amount`, `currency`, `failureCode`, `failedAt`.

### OrderConfirmed

Payload: `orderId`, `customerId`, `pharmacyId`, `confirmedAt`, `pickupCode`.

### OrderCancelled

Payload: `orderId`, `customerId`, `reasonCode`, `cancelledAt`.

### OrderReadyForPickup

Payload: `orderId`, `customerId`, `pharmacyId`, `readyAt`, `pickupCode`.

### NotificationSent / NotificationFailed

Payload: `notificationId`, `orderId`, `customerId`, `channel`, `templateCode`, `status`, `occurredAt` and optional sanitized `failureCode`.

## Consumer groups

Use a unique group per logical consumer, for example:

- `inventory-order-created-v1`
- `payment-inventory-reserved-v1`
- `order-inventory-result-v1`
- `order-payment-result-v1`
- `notification-order-events-v1`
- `audit-order-events-v1`
- `audit-inventory-events-v1`
- `audit-payment-events-v1`
- `audit-prescription-events-v1`
- `audit-notification-events-v1`

Do not reuse one group for consumers that must each receive a copy.

## Delivery and error policy

- Semantics: at-least-once.
- Producer acknowledgements: strongest practical local setting; enable idempotent producer.
- Consumer commits occur after successful transaction/business processing.
- Consumer inserts `processed_event` within the same local database transaction as the business effect.
- Transient errors: bounded backoff, then retry topic/DLT.
- Non-retryable validation/schema errors: DLT immediately.
- DLT record retains original topic, partition, offset, exception class, failure timestamp and correlation ID.
- Alert on DLT count and sustained consumer lag.

## Compatibility policy

- Event name and version are immutable.
- Additive optional fields are backward compatible.
- Do not rename/remove fields within a version.
- Breaking changes create a new event version and, if needed, a new topic/versioned consumer.
- JSON Schemas live in `contracts/json-schema`; AsyncAPI documentation lives in `contracts/asyncapi`.
