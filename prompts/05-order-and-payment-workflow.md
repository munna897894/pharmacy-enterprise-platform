# Prompt 05 — Order and payment workflow (current state)

> **Status (2026-09-30): Complete.** Current event and API contracts are in [API contracts](../docs/06-api-contracts.md) and [event contracts](../docs/07-event-contracts.md).

This prompt matches the repository’s order and payment implementation and the workflow logic that is now running in the service fleet.

## Status

Complete for the current workflow scope.

The repo contains:

- `services/order-service`
- `services/payment-service`

## What is implemented

### Order service

Includes:

- order aggregate, item structure, and workflow state model
- idempotency-key behavior for create flows
- server-side price resolution from product data rather than request-supplied amounts
- validation of verified-prescription requirements before order creation
- transactional persistence of order data and outbox records
- authorization patterns for read and state-related actions
- order state transition and invalid-transition protections

### Payment service

Includes:

- simulated payment authorization and refund logic
- idempotent payment handling based on order identity and payment keys
- deterministic token simulation with success/failure/delay outcomes
- validation of amount and currency
- outbox generation for business-event publication
- refund and read endpoints with admin-specific controls

## Operational rules now enforced

- Price is never trusted from the inbound request; it is resolved from service state.
- Same idempotency key with equivalent payload returns the original order.
- Reuse of the same idempotency key with a different payload results in conflict behavior.
- Invalid order transitions fail without mutating business state.
- The payment service remains explicitly a simulator and does not pretend to be a real payment gateway.

## Event and workflow context

The order/payment path is part of the broader event-driven workflow and is aligned with the choreography pattern used by the platform. The service local outbox is used to support downstream event publication without pretending the local database transaction and Kafka publish are atomic.

## Related validation focus

- unverified prescription cannot create an order
- generated totals and state transitions are deterministic
- duplicated requests remain safe and consistent
- failures and invalid transitions are rejected without creating hidden side effects
