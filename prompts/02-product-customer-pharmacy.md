# Prompt 02 — Product, customer and pharmacy services (current state)

> **Status (2026-09-30): Complete.** Current architecture and deployment facts are in [architecture](../docs/02-architecture.md) and [local deployment](../docs/architecture-local.md); this file records the service-stage scope.

This prompt now reflects the implemented service set and the actual business boundaries in the repository.

## Status

Complete for the current product/customer/pharmacy scope.

The repo now contains service implementations for:

- `product-service`
- `customer-service`
- `pharmacy-service`

These services follow the repo standards for package-by-feature layout, validation, JWT-aware security, persistence, and operational observability.

## Current implementation scope

### Product service

Implemented as the reference Spring MVC service for medication inventory and catalog access.

Included:

- JPA repository and Flyway schema setup
- search and filtering endpoints
- Redis cache-aside patterns with fallback behavior
- invalidation on write paths
- validation and sanitized `ProblemDetail` responses
- resource-server security model with test JWT support
- Actuator and Micrometer observability hooks
- controller and service layer separation
- repository and cache-focused tests

### Customer service

Implemented as a separate service with its own schema and access rules.

Included:

- customer profile and address APIs
- owner-or-staff authorization model based on JWT identity and roles
- service-level validation and sensitive-data handling
- separate Flyway migrations and data model
- MVC/security and repository tests

### Pharmacy service

Implemented as a separate service with location and operational data support.

Included:

- pharmacy profile and status models
- address and business-hours handling
- location search and status transitions
- role-based authorization rules
- validation for operating-hour and contact fields
- repository and security tests

## Key design constraints now enforced

- Business services do not expose JPA entities directly.
- DTOs, validation, and application services are explicit and separated.
- JWT-backed role checks are used where authorization is required.
- Product reads gracefully degrade when Redis is unavailable.
- Local MySQL and Redis assumptions are environment-driven rather than hardcoded for a single profile.

## Operational notes

The original prompt was a task brief; the live repo is now the authority. The work that landed here includes the actual success criteria from those service implementations, including runtime fixes for stale config, DB schema drift, and host-based dependency access.

## Current validation focus

The service-level validation path still follows the same patterns introduced in implementation:

- create/update/deactivate flows for authorized roles
- index and search by identifier or name fields
- validation failure handling without leaking internals
- safe cache fallback and invalidation behavior
- unauthorized access rejection with clear auth behavior
