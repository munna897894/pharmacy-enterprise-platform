# Testing strategy and quality gates

## Test pyramid

| Layer | Purpose | Typical tools |
|---|---|---|
| Unit | Domain state transitions, calculations, mapping and validation | JUnit 5, AssertJ, Mockito only at boundaries |
| Slice | MVC/security/JPA/Kafka configuration in focused scope | `@WebMvcTest`, `@DataJpaTest`, Spring Security Test |
| Integration | Real MySQL/Kafka/Redis behavior | Spring Boot Test + Testcontainers |
| Contract | OpenAPI/event schema compatibility | OpenAPI validation, JSON Schema, optional contract tool |
| End-to-end | Gateway-to-services happy/negative workflows | Postman/Newman or REST-assured runner |
| Operational | Probes, metrics, retries, lag and failure recovery | scripts, kubectl, dashboards |

## Required tests by service

- Controllers: happy path, invalid input, 401, 403, 404 and conflict as applicable.
- Application services: every business rule and state transition.
- Repositories: query behavior, uniqueness and optimistic-lock behavior with MySQL Testcontainer.
- Security: issuer/audience/role/owner checks.
- Kafka producer/consumer: schema fields, correct topic/key, duplicate event, retryable failure and DLT.
- Outbox: aggregate and outbox row commit together; unpublished rows later publish; repeated publisher attempt is safe.
- Cache: hit, miss, invalidation and Redis-down fallback.
- Resilience: timeout, circuit opens, recovery/half-open behavior and no false-success fallback.

## Critical workflow tests

1. Verified prescription + available inventory + successful payment -> confirmed order.
2. Duplicate `OrderCreated` -> one reservation.
3. Insufficient inventory -> cancelled order, no payment.
4. Payment failure -> inventory released and order cancelled.
5. Duplicate `PaymentCompleted` -> one order transition and one notification effect.
6. Notification DLT does not roll back confirmed order.
7. Invalid prescription cannot create an order.
8. Same order `Idempotency-Key` + same request returns original result; same key + different request returns 409.

## Coverage philosophy

Do not optimize for a vanity percentage. Configure a reasonable JaCoCo floor for line/branch coverage, but require 100% coverage of explicitly listed state transitions and compensation paths.

## CI quality gates

The pull-request pipeline must fail on:

- compilation or test failure
- formatting/static-analysis failure
- architecture rule violation
- migration validation failure
- high/critical dependency or image vulnerability without an explicit reviewed exception
- secret detection
- changed API/event contract without its contract/test update

## Commands

Target commands after the repository is generated:

```bash
./mvnw -T 1C clean verify
docker compose -f infra/compose/compose.yml config
docker compose -f infra/compose/compose.yml up -d
docker compose -f infra/compose/compose.yml ps
./scripts/smoke-test.sh
./scripts/e2e-test.sh
```

## Test data

- Use deterministic fictional names and identifiers.
- Use a fixed test clock for time-dependent transitions.
- Seed minimal local demo data through Flyway repeatable/versioned migrations or a local-only initializer.
- E2E tests create their own data or reset to a known state.
- Never depend on execution order between unit/integration tests.

## Stage acceptance rule

A numbered Copilot prompt is complete only when:

1. the affected modules compile;
2. its new tests pass;
3. the named manual verification succeeds;
4. there are no placeholder methods/TODO-only implementations;
5. Copilot explains the main request/event flow and the developer can restate it;
6. changes are committed with a narrow message.

