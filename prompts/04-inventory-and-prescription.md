# Prompt 04 — Inventory and prescription services (current state)

This prompt reflects the implemented inventory, external verification, and prescription workflow in the codebase.

## Status

Complete for the current service scope.

The repo contains:

- `services/inventory-service`
- `services/external-mock-service`
- `services/prescription-service`

## What is implemented

### Inventory service

Includes:

- stock and reservation domain logic
- optimistic locking and validation guardrails
- unique pharmacy/medication inventory identity
- inventory and availability queries
- staff-only mutation authorization patterns
- metrics for low-stock and availability states
- persistence and security tests

### External mock service

Includes:

- controlled verification outcomes such as success, reject, delay, HTTP 500 and connection failure modes
- safe, local-only mock configuration
- request propagation support for correlation IDs and trace metadata
- sanitized mock config inspection endpoints
- deterministic behavior for validation and resilience testing

### Prescription service

Includes:

- prescription lifecycle management and verification flow
- typed external HTTP interactions with explicit timeouts and resilience controls
- safe retry design only for idempotent or transient behavior
- outbox preparation for downstream event publication
- verification, rejection, and infrastructure-failure handling
- concurrent/idempotent verification coverage

## Operational model

The prescription verification flow is deliberately structured so that external I/O occurs outside the persistence transaction. This avoids holding a DB transaction open during network calls and keeps verification outcomes consistent with the application state machine.

## Current design expectations

- Inventory cannot be reduced below the reserved quantity.
- Slow external verification respects the configured timeout budget.
- Circuit breaker and retrial behavior are applied only where duplicate business actions are not introduced.
- Outbox records are used to keep downstream event generation transactionally consistent.

## Related deepening topics

- optimistic locking and conflict handling
- time-bound external verification without unsafe retries
- idempotency for duplicates and replays
- event-driven status propagation after verification

