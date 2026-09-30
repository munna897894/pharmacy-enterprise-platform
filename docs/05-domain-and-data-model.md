# Domain and data model

## Ownership rules

- A service is the only writer and direct reader of its schema.
- Foreign identifiers across services are plain UUID values, not JPA relationships.
- Every mutable table has `created_at`, `updated_at` and an optimistic-lock `version` when concurrent updates are possible.
- Public IDs are UUIDs. Internal database surrogate keys are optional; do not expose them.
- Store timestamps as UTC.
- Flyway owns every schema change. `ddl-auto` must be `validate`, never `update` in normal profiles.

## Auth service

### Tables

- `app_user(id, username, email, password_hash, first_name, last_name, roles, is_active, created_at, updated_at, last_login_at, version)`
- `refresh_token(id, user_id, token_hash, expires_at, revoked_at, created_at)`

### Rules

- Unique normalized username and email.
- Store only BCrypt password hashes and hashes of refresh tokens.
- Seed fictional demo users for `CUSTOMER`, `PHARMACIST`, `STORE_MANAGER` and `ADMIN` in the local profile.

## Product service

### Tables

- `medication(id, ndc_code, name, generic_name, manufacturer, dosage_form, strength, unit_price, currency, active, created_at, updated_at, version)`

### Rules

- `ndc_code` is unique for the exercise.
- Price is non-negative.
- Search by name, generic name and NDC code.
- Redis cache keys: `medication:{id}` and versioned search keys with short TTLs.

## Customer service

### Tables

- `customer(id, auth_user_id, first_name, last_name, email, phone, date_of_birth, status, created_at, updated_at, version)`
- `customer_address(id, customer_id, type, line1, line2, city, state, postal_code, country)`

Use fictional data. Do not log profile fields.

## Pharmacy service

### Tables

- `pharmacy(id, name, license_number, phone, status, timezone, created_at, updated_at, version)`
- `pharmacy_address(id, pharmacy_id, line1, line2, city, state, postal_code, latitude, longitude)`
- `business_hour(id, pharmacy_id, day_of_week, open_time, close_time, closed)`

## Inventory service

### Tables

- `stock_level(id, pharmacy_id, product_id, quantity_on_hand, reorder_level, reorder_quantity, status, created_at, updated_at, version)`
- `stock_adjustment(id, stock_level_id, adjustment_type, quantity_adjusted, reason, adjusted_by, created_at)`
- `inventory_reservation(id, order_id, pharmacy_id, status, items_json, created_at, updated_at)`
- `processed_event(id, order_id, event_id, consumer_name, processed_at)`
- `outbox_event(event_id, aggregate_type, aggregate_id, event_type, topic, event_key, payload, headers, occurred_at, published_at, attempts, last_error)`

### Invariants

- Stock quantity must never become negative.
- The unique business reservation key is `order_id`; reservation line items
  are stored as a serialized snapshot in `items_json`.
- Replaying `OrderCreated` returns the existing reservation outcome.

### Reservation states

`RESERVED -> RELEASED` after payment failure or `RESERVED -> COMMITTED` after
payment succeeds. A rejected reservation is emitted without persisting a
successful hold.

## Prescription service

### Tables

- `prescriptions(id, customer_id, prescriber_id, prescribed_at, expires_at, status, version, created_at, updated_at)`
- `prescription_lines(id, prescription_id, product_id, quantity, instructions, dispensed_quantity, filled_at, created_at)`

### Status machine

`PENDING -> ACTIVE -> FILLED`; active prescriptions may also become `EXPIRED`.

Only an `ACTIVE` prescription can be used to create an order. Prescription
activation is currently an authorized application action; this service does
not yet publish prescription lifecycle events.

## Order service

### Tables

- `customer_order(id, customer_id, prescription_id, pharmacy_id, status, subtotal, tax, total, currency, created_at, updated_at, version)`
- `order_item(id, order_id, medication_id, quantity, unit_price, line_total)`
- `order_status_history(id, order_id, from_status, to_status, reason, event_id, changed_at)`
- `processed_event(...)`
- `outbox_event(...)`

### Status machine

```text
CREATED -> INVENTORY_PENDING -> INVENTORY_RESERVED -> PAYMENT_PENDING
        -> CANCELLED_INVENTORY
PAYMENT_PENDING -> CONFIRMED
                -> CANCELLED_PAYMENT
CONFIRMED -> READY_FOR_PICKUP -> COMPLETED
```

State transitions must be explicit and tested. Invalid transitions throw a domain exception and do not partially update data.

## Payment service

### Tables

- `payment(id, order_id, idempotency_key, amount, currency, status, provider_reference, failure_code, created_at, updated_at, version)`
- `processed_event(...)`
- `outbox_event(...)`

### Status machine

`PENDING -> PROCESSING -> SUCCESS` or `PENDING -> PROCESSING -> FAILED`;
successful payments may later become `REFUNDED`.

No card number, CVV or real payment data is accepted or stored. Use a test payment token such as `tok_success` or `tok_fail`.

## Notification service

### Tables

- `notifications(id, customer_id, type, channel, recipient, subject, message, status, sent_at, read_at, created_at, updated_at, retry_count)`
- `notification_templates(id, type, channel, subject_template, message_template, is_active, created_at, updated_at)`
- `order_customers(order_id, customer_id, created_at, updated_at)`
- `processed_event(...)`
- `notification_outbox(...)`

Delivery is simulated and writes a structured log/record.

## Audit service

### Tables

- `audit_logs(id, aggregate_id, service, action, resource_type, user_id, username, timestamp, status, details, ip_address, created_at)`
- `processed_events(event_id, processed_at)`

Append only. Sanitize fields before storage. The audit service does not become the source of truth for business state.

## External mock service

In-memory control endpoints configure behavior:

- normal success
- validation rejection
- fixed delay
- HTTP 500
- timeout/connection-close simulation

Reset behavior between scenarios.

The Flyway migrations under each service are the executable schema source of
truth. This document summarizes ownership and invariants; it does not replace
those migrations.
