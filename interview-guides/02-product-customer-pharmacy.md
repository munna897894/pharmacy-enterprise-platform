# 02 — Product, Customer, Pharmacy Services (Prompt 02)

## Summary

Three independently deployed services that share the exact same **layered architecture pattern**: `api` (controllers, DTOs) → `application` (services, mappers) → `domain` (entities, exceptions, repository interfaces) → `infrastructure` (config, JPA/Redis/Security wiring). `product-service` is the "reference-quality" implementation — study it first, then see how customer/pharmacy reuse the same shape with different authorization rules.

## Diagram — layered request flow (product-service example)

```
HTTP request
   │
   ▼
MedicationController        (api/controller)   <-- @PreAuthorize, thin, no business logic
   │  calls
   ▼
MedicationService            (application/service) <-- @Transactional, @Cacheable/@CacheEvict
   │  uses
   ├──> MedicationMapper      (application/mapper)  <-- entity <-> DTO conversion
   ├──> MedicationRepository  (domain/repository)   <-- Spring Data JPA interface
   └──> Medication             (domain/model)        <-- JPA @Entity, business methods (update/setActive)
   │
   ▼
MySQL (via Hibernate)     ◄──cache-aside──►     Redis (via RedisTemplate / Spring Cache abstraction)
```

Error handling is centralized: `GlobalExceptionHandler` (`@RestControllerAdvice`) converts domain exceptions (`MedicationNotFoundException`, `DuplicateNdcCodeException`) and validation failures into RFC-7807 `ProblemDetail` responses — never leaking stack traces or SQL.

## Key files (product-service, the reference pattern)

| File | What it shows |
|---|---|
| `domain/model/Medication.java` | JPA entity: `String id` = UUID string (not `UUID` type directly, stored as string), `@Version` for optimistic locking, `BigDecimal unitPrice` + separate `currency` field, `Instant createdAt/updatedAt`, package-private no-arg constructor for Hibernate, a real constructor for business creation, `update()`/`setActive()` mutator methods instead of public setters. |
| `application/service/MedicationService.java` | Class-level `@Transactional`; `@Cacheable(value="medications", key="#id")` on reads, `@CacheEvict` on writes; a **second** cache (`medicationSearch`) keyed by a composite string of all search params + pagination — this is the "search caching" requirement. |
| `api/controller/MedicationController.java` | Thin controller: builds `Pageable`, clamps `size` to 100 max, delegates entirely to the service, uses `@PreAuthorize("hasAnyRole('PHARMACIST','ADMIN')")` for writes and `@PreAuthorize("hasRole('ADMIN')")` for status changes — read is just `isAuthenticated()`. |
| `infrastructure/config/SecurityConfig.java` | `@Profile("!test")` resource-server config: permits actuator health + swagger, everything else `authenticated()`; `NimbusJwtDecoder.withJwkSetUri(...)` — this service **validates JWTs itself** even though the gateway already did; `JwtAuthenticationConverter` maps the `roles` claim to `ROLE_*` authorities. |
| `infrastructure/config/RedisConfig.java` | Manual `RedisTemplate` bean with `Jackson2JsonRedisSerializer` for values, `StringRedisSerializer` for keys — this is what makes cached objects human-readable in Redis instead of raw Java serialization. |
| `api/advice/GlobalExceptionHandler.java` | Maps `MedicationNotFoundException`→404, `DuplicateNdcCodeException`→409, `MethodArgumentNotValidException`→400 with per-field errors, and a catch-all `Exception`→500 that logs full stack trace server-side but returns a generic message to the client. |
| `db/migration/V1__Initial_medication_schema.sql` + `V2__Seed_medications.sql` | Flyway-owned schema + fictional seed data — Hibernate is `ddl-auto: validate` only (see `application.yml`). |

## Core concepts

| Concept | Explanation | Why it matters |
|---|---|---|
| Optimistic locking (`@Version`) | Each update increments a version column; a concurrent update with a stale version throws `OptimisticLockException`. | Prevents silent lost-updates without needing pessimistic DB locks — standard JPA concurrency control. |
| Cache-aside pattern | Service checks Redis first (`@Cacheable`); on miss, loads from MySQL, then populates cache; writes evict rather than update the cache. | Simple, correct-by-construction cache strategy — avoids stale-cache bugs from partial writes. |
| Composite cache key for search | `#query + ':' + #manufacturer + ':' + #dosageForm + ':' + #active + ':' + #pageable.pageNumber + ':' + #pageable.pageSize` | Search results depend on every filter + page, so the key must encode all of them or you'd serve wrong results from cache. |
| Resource-server JWT re-validation | Each service (not just the gateway) configures its own `JwtDecoder` against the JWKS endpoint. | Zero-trust principle: the gateway is not implicitly trusted by downstream services; every service independently verifies the token signature. |
| `ProblemDetail` (RFC 7807) | Spring's built-in structured error type (`type`, `title`, `status`, `detail`, plus custom `properties`). | Consistent, machine-parseable error shape across the whole platform instead of ad hoc JSON error bodies. |
| Ownership-or-staff authorization (customer-service) | Controller extracts `userId` (JWT `sub` claim) and `roles` from `Authentication`, passes both into the service layer, which decides "is this the owner, or is this a staff role" before allowing access. | Authorization logic lives in the **application service**, not scattered across `@PreAuthorize` SpEL expressions — necessary because "owner or staff" needs data (the resource's owning userId) that isn't available purely from the JWT. |
| Package-by-feature/layer hybrid | `api/controller`, `api/request`, `api/response`, `api/advice` are grouped by *layer*; but everything is scoped under one feature root package (`com.jagapathi.pharmacy.product`). | Keeps a single-purpose service's code organized without needing full DDD bounded-context ceremony for a small service. |

## Common interview Q&A

**Q: Why is the entity `id` a `String` instead of `java.util.UUID`?**
A: It's generated as `UUID.randomUUID().toString()` and stored as a `String` primary key. This avoids JPA/Hibernate UUID-type/dialect quirks across databases (MySQL doesn't have a native UUID column type) while still guaranteeing global uniqueness for the public resource identifier.

**Q: How does the cache stay consistent with the database?**
A: Read methods are `@Cacheable`; every mutation method (`updateMedication`, `updateMedicationStatus`) is annotated `@CacheEvict(key = "#id")`, so a write always invalidates the per-ID cache entry rather than trying to update it in place. The broader `medicationSearch` cache isn't explicitly evicted on writes in this implementation — worth calling out as a known staleness window (see Gotchas).

**Q: Why does the gateway validate the JWT AND the downstream service validate it again?**
A: Defense in depth / zero trust. The gateway's validation is a UX/perimeter optimization (fail fast, don't even forward bad requests), but each service must independently verify the signature and claims because a service should never assume network-level trust is sufficient — a compromised or misconfigured route could otherwise let unauthenticated traffic reach a business service directly.

**Q: How would you implement "owner or staff" authorization cleanly?**
A: Pull `userId` and `roles` from the `Jwt` principal in the controller (or a shared argument resolver), pass them into the application service method, and let the service compare `userId` against the resource's actual owner field, OR check if `roles` contains an authorized staff role — throwing a domain-specific `CustomerAccessDeniedException` mapped to 403 by the exception handler. This can't be done purely with `@PreAuthorize` SpEL because the "owner" fact requires a DB lookup.

**Q: Why validate at 100 max page size in the controller instead of the repository?**
A: It's a defensive input clamp close to the API boundary — protects against a client requesting an unbounded page size that could create a large query/response payload, independent of whatever the repository/DB could technically return.

## Gotchas / real issues found while reviewing this codebase (fixed)

- **Cache fallback gap — fixed.** The original design goal was "graceful fallback to MySQL when Redis is unavailable," but the code had **no custom `CacheErrorHandler`** registered, so Spring's default behavior on `@Cacheable`/`@CacheEvict` failures was to **propagate the exception** rather than degrade gracefully. Fixed by making `RedisConfig` implement `CachingConfigurer` and registering an `errorHandler()` bean that logs and swallows GET/PUT/EVICT/CLEAR cache failures — a Redis outage now genuinely falls through to MySQL instead of failing the request.
- **`medicationSearch` cache staleness on writes — fixed.** Only the single-ID cache (`medications`) was being evicted on update/status-change; the broader search-result cache could serve stale pages until TTL (10 min) expired. Fixed by adding `@CacheEvict(value = "medicationSearch", allEntries = true)` (combined via `@Caching`) to `createMedication`, `updateMedication`, and `updateMedicationStatus`, so any mutation now invalidates all cached search pages immediately.
- **Local vs container profile drift** (see Prompt 08/09 guides): `application-local.yml` in this repo had indentation/profile-activation issues that needed correcting during the runtime stabilization pass — a good reminder that YAML profile files are easy to silently break.

Verified via `./mvnw -pl services/product-service test` — 16/16 tests pass after both fixes.

## Trace-through: "Client searches medications, then creates one"

1. `GET /api/v1/medications?q=ibuprofen&page=0&size=20` hits the gateway, which validates the JWT and forwards to product-service.
2. `MedicationController.search()` clamps size, builds a `Pageable`, calls `medicationService.searchMedications(...)`.
3. `MedicationService` checks the `medicationSearch` Redis cache using the composite key. Miss → queries `MedicationRepository.searchMedications(...)` (a Spring Data JPA specification/query), maps results with `MedicationMapper`, wraps in `PageResponse`, and Spring's caching aspect stores the result in Redis before returning.
4. `POST /api/v1/medications` with a PHARMACIST/ADMIN JWT: controller validates the request body (Bean Validation), calls `MedicationService.createMedication()`.
5. Service checks `findByNdcCode` for duplicates first (throws `DuplicateNdcCodeException` → 409 if found), otherwise constructs a new `Medication` entity (UUID generated in the constructor), saves via the repository (Hibernate INSERT, `ddl-auto=validate` means the schema must already match via Flyway), logs the creation, maps to `MedicationResponse`, returns 201.
6. Any subsequent `GET .../{id}` for that new medication is a cache miss on first read, then cached going forward until the next write evicts it.
