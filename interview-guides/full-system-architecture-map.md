# Full System Architecture Map (All Services, As Actually Implemented)

This is a ground-truth map of how every service in the platform actually connects today — verified by reading the real controllers, Kafka listeners, clients, and config files in this repo, not the aspirational design docs. Where the real wiring differs from the original design intent, it's called out explicitly as a **known gap**, not glossed over. Use this as your single reference diagram before an interview.

---

## 1. Full topology — every service, port, database, and infra dependency

```
                                    ┌─────────────────────────┐
                    external       │      api-gateway         │  :8080  (WebFlux)
                    clients ──────>│  JWT validation, routing, │
                                    │  rate limiting (Redis)   │
                                    └────────────┬─────────────┘
                                                 │ (routes below)
        ┌───────────────┬───────────────┬───────┼───────┬───────────────┬───────────────┐
        ▼               ▼               ▼       ▼       ▼               ▼               ▼
  auth-service    product-service  customer-svc pharmacy-svc inventory-svc prescription-svc order-service
   :8081            :8081*          :8082       :8084        :8085         :8086*         :8087
   DB: auth_service DB: pharmacy_   DB: pharmacy_ DB: pharmacy_ DB: pharmacy_ DB: prescription_ DB: pharmacy_
                     product         customer     pharmacy     inventory     service           order
   no Redis         Redis(cache)    no Redis     no Redis     no Redis      no Redis          no Redis
   no Kafka         no Kafka        no Kafka     no Kafka     no Kafka      no Kafka          Kafka:
                                                                                                produces
                                                                                                pharmacy.order.
                                                                                                events.v1;
                                                                                                consumes
                                                                                                pharmacy.inventory.
                                                                                                events.v1,
                                                                                                pharmacy.payment.
                                                                                                events.v1

        ┌───────────────┬───────────────┬───────────────┐
        ▼               ▼               ▼               ▼
  payment-service  notification-svc  audit-service   external-mock-svc
   :8088             :8089           :8086*            :8089*
   DB: pharmacy_     DB: pharmacy_    DB: pharmacy_     no DB
   payment           notification     audit
   Kafka: produces   Kafka: consumes  Kafka: consumes   no Kafka
   pharmacy.payment. (spring-cloud-   (spring-cloud-
   events.v1         stream, 5       stream, 6
                      bindings —      bindings — see
                      see gaps)       gaps)

  Shared infra: MySQL (one schema/user per service, host-based locally),
                Redis (in-cluster locally; used only by product-service + api-gateway),
                Kafka (host-based locally, host.docker.internal:9092)

  * = port collision with another service (see §5 Known Gaps)
```

## 2. Synchronous (REST) call graph — who calls whom directly

```
Client → api-gateway → any service (JWT re-validated at each downstream service)

order-service  ──REST GET──>  inventory-service   (InventoryAvailabilityClient)
               "check availability" BEFORE creating the order — this is a
               synchronous pre-check, NOT the "reserve via event" pattern the
               original saga doc describes.

payment-service ──REST GET──>  order-service       (OrderLookupClient)
               looks up order details before authorizing payment.

payment-service ──REST POST──> external-mock-service (PaymentGatewayClient)
               simulates the payment gateway call (tok_success/tok_fail/tok_delay).

notification-service ──REST (SMTP-like)──> external SMTP (mail host, port 1025 local)
               and references external-service.url: http://external-mock-service:8080
               for any external-facing simulation it needs.

Every service (independently) ──> auth-service JWKS endpoint
               to validate inbound JWTs as an OAuth2 resource server.
```

Notice: **no service calls prescription-service, customer-service, or pharmacy-service synchronously** from another service in this codebase today — those three are pure leaf services reachable only through the gateway from external clients.

## 3. Kafka — the real wiring (topics, producers, consumers)

### 3a. Topics that are actually produced to

| Topic | Producer | Mechanism |
|---|---|---|
| `pharmacy.order.events.v1` | order-service | Transactional outbox (`OutboxEvent` row saved in the same DB transaction as the order) + a `@Scheduled` `OutboxPublisher` polling every 5s, sending via `KafkaTemplate<String,String>` |
| `pharmacy.payment.events.v1` | payment-service | `KafkaTemplate<String,Object>.send(...)` directly from `PaymentService` after processing a payment (also outbox-backed via `payment.domain.OutboxEvent`) |

### 3b. Topics that are consumed, and whether anything actually feeds them

| Consumer | Service | Style | Listens on | Is this topic ever produced to? |
|---|---|---|---|---|
| `onInventoryEvent` | order-service | raw `@KafkaListener` | `pharmacy.inventory.events.v1` | **No producer exists.** inventory-service has zero Kafka code. This listener is wired but will never fire in the current codebase. |
| `onPaymentEvent` | order-service | raw `@KafkaListener` | `pharmacy.payment.events.v1` | **Yes** — payment-service really publishes here. This leg of the saga is real and functional. |
| `lowStockConsumer`, `orderCreatedConsumer`, `orderShippedConsumer`, `paymentProcessedConsumer`, `prescriptionFilledConsumer`, `prescriptionExpiringConsumer` | notification-service | Spring Cloud Stream functional bindings | `pharmacy.order.events.v1` (real), `pharmacy.payment.events.v1` (real), `pharmacy.inventory.events.v1` (no producer), `pharmacy.prescription.events.v1` (no producer) | Mixed — order/payment consumers will actually receive messages; inventory/prescription consumers are orphaned because no service publishes there. |
| `productEventConsumer`, `customerEventConsumer`, `orderEventConsumer`, `paymentEventConsumer`, `prescriptionEventConsumer`, `inventoryEventConsumer` | audit-service | Spring Cloud Stream functional bindings | `product-service.product.events`, `customer-service.customer.events`, `order-service.order.events`, `payment-service.payment.events`, `prescription-service.prescription.events`, `inventory-service.inventory.events` | **None of these topic names match any real producer.** order-service and payment-service actually publish to `pharmacy.order.events.v1` / `pharmacy.payment.events.v1` — completely different naming convention (`pharmacy.<x>.events.v1` vs `<x>-service.<x>.events`). As configured today, **audit-service will never receive a real business event.** |

### 3c. Diagram — real vs orphaned event flow

```
 order-service                         payment-service
      │  outbox → Kafka                     │  KafkaTemplate.send
      ▼                                      ▼
 pharmacy.order.events.v1            pharmacy.payment.events.v1
      │            │                        │         │
      │            └───────────┐   ┌────────┘         │
      ▼                        ▼   ▼                   ▼
 notification-service      order-service(consumes    notification-service
 (orderCreated/Shipped     PaymentCompleted/Failed,   (paymentProcessed
  consumers) ✅ REAL       transitions order state)    consumer) ✅ REAL
                            ✅ REAL LOOP CLOSED

 ── the rest is configured but not connected ──

 inventory-service            prescription-service
   (no Kafka code at all)       (no Kafka code at all)
        X                             X
        │ (nothing published)         │ (nothing published)
        ▼                             ▼
 pharmacy.inventory.events.v1   pharmacy.prescription.events.v1
        │                             │
        ▼                             ▼
 order-service.onInventoryEvent   notification-service
 notification-service.lowStock    prescriptionFilled/Expiring
   consumers  ⚠ ORPHANED             consumers  ⚠ ORPHANED

 audit-service's 6 bindings target topic names that no producer
 in the codebase uses at all (`order-service.order.events` etc.
 vs the real `pharmacy.order.events.v1`) ⚠ ALL ORPHANED
```

## 4. JWT / security flow across the fleet

```
1. Client → POST /api/v1/auth/login → gateway → auth-service
2. auth-service verifies credentials (BCrypt), issues RS256 JWT (issuer, audience,
   sub, userId, roles, iat, exp, jti) + rotating refresh token
3. auth-service exposes public keys at GET /api/v1/auth/.well-known/jwks.json
4. Client sends "Authorization: Bearer <jwt>" on every subsequent call
5. api-gateway validates the JWT as an OAuth2 resource server (JWKS from auth-service)
6. Gateway forwards the request downstream (still carrying the original JWT)
7. EVERY downstream business service independently re-validates the same JWT
   using its own NimbusJwtDecoder against the auth-service JWKS endpoint
   (zero-trust: gateway validation is not "trusted" by services)
```

Each service maps the JWT's `roles` claim to Spring Security `ROLE_*` authorities via a `JwtAuthenticationConverter`, then uses `@PreAuthorize` (or manual `Authentication` extraction, as in customer-service) for method-level authorization.

## 5. Known gaps found during this review (real, verified in code)

These are accurate findings from reading the actual source — useful both as engineering follow-ups and as strong "tell me about a time you found an architecture gap" interview material.

| # | Gap | Evidence | Impact |
|---|---|---|---|
| 1 | **Kafka topic naming split-brain.** order-service/payment-service publish to `pharmacy.<domain>.events.v1`; audit-service's bindings expect `<domain>-service.<domain>.events`. | `OrderService.java` outbox topic literal vs `audit-service/application.yml` bindings | audit-service currently receives **zero** real events — its "append-only audit trail" goal is not actually being fulfilled end-to-end. |
| 2 | **Inventory/prescription sagas are unimplemented on the producer side.** inventory-service and prescription-service have no `KafkaTemplate`, no `StreamBridge`, no outbox producer code at all. | `grep` for Kafka producer code returns nothing in either service | order-service's inventory-result consumer and notification-service's inventory/prescription consumers are wired but permanently idle. The real "reserve inventory via event" saga step described in the design docs does not exist — order-service instead does a synchronous REST availability check before creating the order. |
| 3 | **Gateway route ports don't match actual service ports.** `GatewayConfig.java` routes to `product-service:8082` and `customer-service:8083` and `audit-service:8090`, but the real `application.yml`/`application.properties` for those services bind `8081`, `8082`, and `8086` respectively. | `GatewayConfig.java` vs each service's `application.yml` | In a pure Spring-DNS-routing context this is masked by Kubernetes Service objects (which remap ports), but as literal Java config the two disagree — a real drift risk if the K8s Service port mapping is ever changed to "just forward the container's declared port." |
| 4 | **`stripPrefix(2)` inconsistency at the gateway.** Most gateway routes apply `.filters(f -> f.stripPrefix(2))`, which strips `/api/v1` before forwarding — but every downstream controller is mapped at `/api/v1/...` (e.g., `@RequestMapping("/api/v1/medications")`). Some auth routes (login/register/refresh/jwks) do **not** strip the prefix, others (logout) do. | `GatewayConfig.java` | If routing genuinely worked this way, most `/api/v1/**` requests forwarded with the prefix stripped would 404 against controllers still expecting `/api/v1/...`. This needs a consistent decision: either strip nowhere (since services already prefix `/api/v1`) or move all controllers to accept the stripped path. |
| 5 | **JWKS URI is inconsistent across services.** Correct full path is `/api/v1/auth/.well-known/jwks.json` (confirmed: `AuthController` is `@RequestMapping("/api/v1/auth")` + `@GetMapping("/.well-known/jwks.json")`). `api-gateway` and `notification-service` use the correct full path. `audit-service` and `product-service` use `http://.../.well-known/jwks.json` **without** the `/api/v1/auth` segment — and product-service additionally hardcodes `http://localhost:8080` (the gateway's address, not auth-service's), with no environment override at all. | `product-service/SecurityConfig.java`, `audit-service/application.yml` | product-service's JWT validation would fail to fetch the correct JWKS document in any real container/k8s deployment (`localhost:8080` inside the product-service container is itself, not the gateway). This is a legitimate outstanding bug, not just a style inconsistency. |
| 6 | **Port collisions across independent services' default configs.** `auth-service` and `product-service` both default to `8081`; `prescription-service` and `audit-service` both default to `8086`; `notification-service` and `external-mock-service` both default to `8089`. | grep across all `application.yml`/`application.properties` | Harmless when each runs in its own container/pod, but a real trap if you ever try to run more than one of these directly on the host at once for local debugging. |
| 7 | **DB naming convention drift.** Every service uses `pharmacy_<service>` except `auth-service` (`auth_service`) and `prescription-service` (`prescription_service`). | grep of `DB_NAME` defaults | Cosmetic, but worth knowing before writing infra scripts that assume a single naming pattern. |
| 8 | **Redis is used by only 2 of 12 services** (`product-service` for cache-aside, `api-gateway` for rate limiting) — every other service has no Redis dependency at all, despite Redis being described platform-wide in some design docs. | grep for `redis` config across all services | Not a bug — just a documentation-vs-reality gap worth knowing so you don't over-describe Redis's role in an interview. |

## 6. What this means for how you should describe the system in an interview

- **Accurately describe the *real* saga**: today it is order⇄payment (fully event-driven, real outbox, real consumer loop) plus a *synchronous* inventory availability pre-check — not the full three-way choreography (order→inventory→payment→order) the original design intended. You can honestly say: "the payment leg of the saga is fully implemented and event-driven; the inventory leg is currently a synchronous pre-check rather than an asynchronous reservation event, and that's a known gap I identified."
- **audit-service is scaffolded, not wired** — a great "what would you fix first" answer: align topic naming (either rename audit-service's bindings to `pharmacy.*.events.v1`, or vice versa) before anything else, since it silently produces an empty audit trail today.
- **Gateway routing has real inconsistencies** (ports + prefix stripping) that would only be masked by Kubernetes Service-level port remapping — worth mentioning as "the kind of drift that hides behind orchestration layers until someone changes the infrastructure."
- These are exactly the kind of findings a senior engineer is expected to surface during a design review — you now have first-hand ownership of having found and can explain each one.

## 7. Suggested fix order (if/when you want to close these gaps)

1. Fix product-service's JWKS URI (functional bug, breaks JWT validation in real deployments).
2. Align Kafka topic naming between audit-service's bindings and the real `pharmacy.*.events.v1` producers.
3. Decide and standardize the gateway's prefix-stripping behavior across all routes.
4. Correct the gateway's hardcoded ports to match each service's actual configured port.
5. (Larger, optional) Implement real inventory-service and prescription-service Kafka producers if you want the originally-designed three-way choreography saga instead of the current hybrid REST+Kafka model.
