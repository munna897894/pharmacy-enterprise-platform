# 06 — Kafka, Notification, and Audit (Prompt 06)

## Summary

Notification and audit are active Spring Cloud Stream consumers, not orphaned scaffolds. Notification subscribes to order, payment and prescription topics, creates simulated deliveries and publishes `NotificationSent`/`NotificationFailed` through its own transactional outbox. Audit subscribes to the actual versioned order, inventory, payment, prescription and notification topics. Order, inventory and payment also use raw Spring Kafka listeners and outbox-backed publication. The current prescription service does **not** publish verification outcomes; a configured subscription alone is not evidence of a producer. See `docs/07-event-contracts.md` for intended topic/event contracts.

## Wiring

| Topic | Publisher | Relevant consumers |
|---|---|---|
| `pharmacy.order.events.v1` | order | inventory, notification, audit |
| `pharmacy.inventory.events.v1` | inventory | order, payment, audit |
| `pharmacy.payment.events.v1` | payment | order, inventory, notification, audit |
| `pharmacy.prescription.events.v1` | No producer in current `prescription-service` | notification, audit (configured; no current prescription traffic) |
| `pharmacy.notification.events.v1` | notification outbox | audit |

Notification's three functions are `orderEventConsumer`, `paymentEventConsumer`, `prescriptionEventConsumer`, each with its own consumer group. OrderCreated builds an order-to-customer projection before payment result notifications; a payment event that outruns that projection retries with backoff. New notification groups start from `earliest`; processed-event uniqueness prevents repeat effects. Audit has five corresponding event consumers with real `pharmacy.*.events.v1` destinations, distinct groups and binder DLQs. No product/customer event topic is claimed here. Prescription bindings can process a conforming event, but are not fed by the present prescription service.

```text
order/inventory/payment/prescription events -> notification consumers
    -> processed_event + Notification rows + notification_outbox
    -> acknowledged Kafka send -> pharmacy.notification.events.v1 -> audit
order/inventory/payment/prescription events ---------------------------> audit
```

## Key files

| File | What to study |
|---|---|
| `services/notification-service/src/main/resources/application.yml` | Three function bindings, `startOffset: earliest`, bounded retries and one-partition DLQs. |
| `services/notification-service/.../infrastructure/messaging/NotificationEventConsumers.java` | Event-type dispatch, including order/customer projection and payment/prescription paths. |
| `services/notification-service/.../application/{NotificationEventHandler,NotificationService,NotificationOutboxService}.java` | Inbox transaction, channel strategies, and broker-acknowledged relay. |
| `services/notification-service/.../infrastructure/channel/` | Simulated email and in-app delivery strategies; distinguish simulation from a live mail provider. |
| `services/audit-service/src/main/resources/application.yml` | Actual five domain bindings and DLQ configuration. |
| `services/audit-service/.../infrastructure/kafka/KafkaConsumerConfig.java` and `.../application/AuditEventProcessor.java` | Functional consumers, correlation header handling, flat-payload normalization and audit persistence. |

## Core concepts and interview Q&A

| Concept | Why it matters |
|---|---|
| Functional bindings | Spring Cloud Stream maps `Consumer<Message<String>>` bean names to destinations/groups in YAML; unlike `@KafkaListener`, topic wiring lives in config. |
| Per-service inbox | Notification and audit each maintain their own processed-event records; sharing a database table would violate service ownership. |
| Outbox on notification | Delivery outcomes become durable event rows with the notification change; acknowledgement is required before an outbox row is marked published. |
| Strategy | `NotificationSender` implementations choose channels without changing event consumption rules. |
| Audit read model | Append-only audit records can represent events from multiple producer schemas, including legacy flat payloads normalized by the processor. |

**Q: Why do audit and notification both receive a payment event?**
A: Their groups are independent. Notification creates customer-facing delivery records; audit stores a separate append-only event trail. Neither competes with order's payment-result group.

**Q: What happens if simulated delivery fails?**
A: Notification records a failed attempt and `NotificationFailed` in an independent transaction, while the original consume transaction rolls back for broker redelivery. The outbox later publishes the failure event. Avoid treating a failed delivery as a successful notification.

**Q: What if a payment event arrives before OrderCreated at notification?**
A: The order-to-customer projection may not exist yet. The payment binding has bounded retries with backoff and a DLQ after exhaustion, rather than inventing a customer or silently dropping the event.

**Q: How would you detect topic drift?**
A: Compare producer topic constants with binding destinations, then run a broker-backed end-to-end test that asserts audit rows and notification outcomes. Today's audit bindings target the real `pharmacy.*.events.v1` topics.

## Gotchas / trace-through

- Notification handles the event types it explicitly dispatches; a topic subscription does not imply every event type generates a customer message. Audit has no product/customer bindings.
- Binder `enableDlq`/`dlqName` and bounded attempts cover stream consumers. Raw order/inventory/payment listeners are separately configured and should not be described as having the same DLQ path.
- DLT topics have one partition whereas source topics have three; binder `dlqPartitions: 1` matters.
- Do not put recipient addresses, prescription text or payment details into audit/log claims. Local email delivery is simulated.

**Trace:** `PaymentCompleted` reaches notification's payment group; it resolves the customer from OrderCreated's projection, persists simulated email/in-app notifications and outcome outbox rows, then the relay publishes acknowledged `NotificationSent` events for audit's notification group. Duplicate source `eventId`s do not repeat delivery.
