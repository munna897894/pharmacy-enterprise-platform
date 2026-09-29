# 00 — Platform Overview & Architecture (Quick Reference)

## What this project is

A production-style **educational** retail pharmacy microservices platform. Java 21, Spring Boot 3.5.x, Spring Cloud 2025.0.x. One Maven monorepo, 11 business services + gateway + 2 shared platform libraries, each service owning its own MySQL schema, communicating synchronously (REST/OpenFeign/WebClient) and asynchronously (Kafka), fronted by a single API Gateway.

## Service inventory

| Service | Port | Role | Style |
|---|---:|---|---|
| api-gateway | 8080 | Single public entry point, JWT validation, routing, rate limiting | Spring Cloud Gateway (WebFlux) |
| auth-service | 8081 | Issues RSA-signed JWTs, refresh tokens, JWKS | Spring MVC |
| product-service | 8081(local)/varies | Medication catalog, pricing, Redis cache-aside | Spring MVC |
| customer-service | 8082 | Customer profile/address, ownership auth | Spring MVC |
| pharmacy-service | 8084 | Pharmacy store info, hours, location search | Spring MVC |
| inventory-service | 8085 | Stock levels, reservations, optimistic locking | Spring MVC |
| prescription-service | 8086 | Prescription lifecycle + external verification | Spring MVC |
| order-service | 8087 | Order aggregate, saga initiator | Spring MVC |
| payment-service | 8088 | Simulated payment authorization/refund | Spring MVC |
| notification-service | 8089 | Simulated email/SMS delivery | Spring MVC |
| audit-service | 8090 | Append-only event audit trail | Spring MVC |
| external-mock-service | 8089(local) | Test double for external verification/payment gateway | Spring MVC |

Shared libraries (not services, no Spring context of their own business logic):
- `platform/event-contracts` — shared `DomainEvent<T>` envelope record + event payload types
- `platform/test-support` — shared test utilities, ArchUnit helper base

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

Each service:
- owns exactly one MySQL schema (never reads another service's tables)
- validates the JWT again itself (gateway validation is not "trusted" blindly downstream)
- exposes Actuator health/info/prometheus

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
   └─ valid state transition only → publishes OrderConfirmed / OrderCancelled
         │
   notification-service + audit-service consume everything (independently, own consumer groups)
```

Key idea: **no distributed transaction**. Every step is: local DB transaction + outbox row, in the same commit. A separate poller/publisher reads the outbox and pushes to Kafka. Consumers use an inbox/processed-event table keyed by `eventId` to guarantee idempotency under at-least-once delivery.

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

Each numbered guide (`01`…`09`) maps 1:1 to the corresponding `prompts/0X-*.md` stage. Structure per guide:
1. Summary — what/why
2. Diagram
3. Key files/classes
4. Core concepts table
5. Common interview Q&A
6. Real gotchas/bugs we actually hit
7. "Trace one request" walkthrough

Read them in order (01 → 09) the first time; use them as flashcards afterward.
