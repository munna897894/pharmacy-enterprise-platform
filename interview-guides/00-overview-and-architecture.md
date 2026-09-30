# 00 — Platform Overview & Architecture (Quick Reference)

## What this project is

A production-style **educational** retail pharmacy microservices platform. Java 21, Spring Boot 3.5.x, Spring Cloud 2025.0.x. One Maven monorepo, ten database-owning services, a gateway, an external mock and three shared platform modules. Each durable service owns a MySQL schema; synchronous REST and asynchronous Kafka flows meet at a single gateway.

## Service inventory

| Service | Port | Role | Style |
|---|---:|---|---|
| api-gateway | 8080 (management 9081) | Single public entry point, JWT validation, routing, rate limiting | Spring Cloud Gateway (WebFlux) |
| auth-service | 8081 | Issues RSA-signed JWTs, refresh tokens, JWKS | Spring MVC |
| product-service | 8082 | Medication catalog, pricing, Redis cache-aside | Spring MVC |
| customer-service | 8083 | Customer profile/address, ownership auth | Spring MVC |
| pharmacy-service | 8084 | Pharmacy store info, hours, location search | Spring MVC |
| inventory-service | 8085 | Stock levels, reservations, optimistic locking | Spring MVC |
| prescription-service | 8086 | Prescription lifecycle; external verification remains unimplemented | Spring MVC |
| order-service | 8087 | Order aggregate, saga initiator | Spring MVC |
| payment-service | 8088 | Simulated payment authorization/refund | Spring MVC |
| notification-service | 8089 | Simulated email/SMS delivery | Spring MVC |
| audit-service | 8090 | Append-only event audit trail | Spring MVC |
| external-mock-service | 8080 (internal) | Test double for external/payment gateway | Spring MVC |

Shared libraries (not services, no Spring context of their own business logic):
- `platform/event-contracts` — shared `DomainEvent<T>` envelope record + event payload types
- `platform/test-support` — shared test utilities, ArchUnit helper base
- `platform/observability` — correlation, tracing and business-metric helpers

## High-level request flow

```
                       ┌─────────────────┐
   Client (mobile/web) │   api-gateway    │  <-- only public entry point
                       │  (WebFlux, 8080) │
                       └───────┬──────────┘
             JWT validated here, routed by path
                               │
     ┌─────────────┬───────────┼───────────┬──────────────┬─────────────┐
     ▼             ▼           ▼           ▼              ▼             ▼
 auth-service  product-svc customer-svc pharmacy-svc  inventory-svc  order-svc ...
  (issues JWT)  (own DB)    (own DB)     (own DB)       (own DB)     (own DB)
```

Each database-owning service has its own MySQL schema and user (never reads
another service's tables). Business services validate JWTs independently of
the gateway and expose internal Actuator health/info/prometheus; the gateway
and database-free external mock have no service-owned schema.

## The order workflow (choreography saga) — the conceptual heart of the platform

```
Order created (order-service)
   └─ DB tx: insert Order + OutboxEvent(OrderCreated)          [atomic local tx]
         │
   Outbox poller publishes "OrderCreated" → Kafka
         │
   inventory-service consumes (idempotent via inbox table)
   └─ reserves stock → publishes InventoryReserved / InventoryRejected
         │
   payment-service consumes InventoryReserved (idempotent)
   └─ simulates payment → publishes PaymentCompleted / PaymentFailed
         │
   order-service consumes payment/inventory outcomes (idempotent)
   └─ valid state transition (confirmation/cancellation events not yet emitted)
         │
   notification-service consumes order/payment/prescription bindings and
   publishes NotificationSent/Failed via outbox; audit consumes order,
   inventory, payment, prescription and notification topic bindings
```

Key idea: **no distributed transaction**. Critical producers save an outbox row with the local business change. Relays await broker acknowledgement; consumers record processed `eventId`s to prevent duplicate effects under at-least-once delivery. Prescription topic bindings exist but the current prescription service does not publish verification events; order does not yet publish confirmation/cancellation outcomes. See the full-system map for the code-versus-contract boundary.

## Cross-cutting standards applied everywhere

- UUID identifiers for all public/resource IDs
- `BigDecimal` + 3-letter currency code for money — never `double`
- UTC `Instant` in storage, ISO-8601 in API responses
- `ProblemDetail` for all API errors (no stack traces/SQL leaked)
- Constructor injection only, no field injection, no Lombok
- `X-Correlation-ID` created at the gateway, propagated through HTTP headers and Kafka headers
- Package-by-feature: `api` / `application` / `domain` / `infrastructure` per service
- ArchUnit test per service enforcing "no service depends on another service's implementation package"

## How to use these guides

Each numbered guide (`01`…`13`) maps to a corresponding `prompts/0X-*.md` stage. Structure per guide:
1. Summary — what/why
2. Diagram
3. Key files/classes
4. Core concepts table
5. Common interview Q&A
6. Real gotchas/bugs we actually hit
7. "Trace one request" walkthrough

Read the [full-system map](full-system-architecture-map.md) first, then the numbered guides; use them as flashcards afterward. Local Docker Desktop Kubernetes is the canonical runtime; Compose is a legacy full-fleet alternative. The current full-fleet AWS sandbox is prepared, not live-verified. Prompt 14 vendor hooks are disabled.
