# Prompt 06 — Kafka, saga, notification and audit (current state)

> **Status (2026-09-30): Complete.** The current event contract is [here](../docs/07-event-contracts.md); the audit service guide documents its active topic bindings and compatibility behavior.

This prompt reflects the event-driven pieces that are now present in the repo: Kafka contracts, saga processing, notification consumers, and audit event ingestion.

## Status

Complete for the service implementation and runtime wiring in the current repo.

The repo contains:

- `platform/event-contracts`
- `services/notification-service`
- `services/audit-service`
- Kafka-ready event and outbox/inbox plumbing in the service layer

## What is implemented

### Event contracts and Kafka foundation

Includes:

- shared event envelope and payload definitions
- explicit serialization strategy and trusted package configuration
- Kafka producer and consumer configuration aligned to the repo's service needs
- outbox and inbox patterns for idempotent event handling
- logic for duplicate event protection and bounded retries

### Choreography workflow

The services use a choreography saga approach across the order workflow, including:

- outbox publication of order lifecycle events
- idempotent inventory reservation flow
- payment simulation and payment outcome publication
- final order state progression based on upstream business events
- event ordering and duplicate defensive logic by event identity

### Notification service

Includes:

- event-driven consumption of relevant business events
- simulated delivery records with masked targets
- deterministic success/failure behavior for testing
- duplicate-safe delivery handling
- local persistence and metrics for sent/retry/failed outcomes

### Audit service

Includes:

- append-only audit capture of business events
- eventId and correlationId preservation
- sanitization and filtering of sensitive payload content
- duplicate-event protection
- admin/search read API patterns

## Operational and runtime notes

For the canonical full-fleet local Kubernetes environment, Kafka and MySQL are host services; Redis and the external mock are in-cluster. Compose is a legacy alternative. See [local deployment](../docs/architecture-local.md).

## Current validation focus

- duplicate delivery is idempotent
- poison or malformed events do not block the workflow indefinitely
- consumer processing remains safe under replays and restarts
- event metadata and correlation IDs are preserved for traceability
- audit records remain append-only and sanitized
