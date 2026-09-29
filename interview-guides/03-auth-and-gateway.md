# 03 — Auth and Gateway (Prompt 03)

## Summary

`auth-service` is a classic Spring MVC security service: it stores users and hashed refresh-token fingerprints in MySQL, verifies passwords with BCrypt, and issues RS256-signed JWTs plus a JWKS document. `api-gateway` is the platform ingress built on Spring Cloud Gateway WebFlux, where routing, JWT checks, CORS, correlation IDs, rate limiting, and security headers all live.

The intended architecture is strong: gateway validation plus per-service validation creates a zero-trust, defense-in-depth model. But the current code also has several real integration mismatches that are worth studying because they are exactly the kind of “looks right in diagrams, breaks in runtime” issues interviewers like to probe.

## Diagram — login + protected request flow

```text
Client
  │
  ├─ POST /api/v1/auth/login
  │    ▼
  │  api-gateway
  │    ├─ JwtAuthenticationFilter skips public auth route
  │    ├─ CorrelationIdFilter adds/preserves X-Correlation-ID
  │    └─ Route auth-login -> http://auth-service:8081
  │              ▼
  │           AuthController.login()
  │              ▼
  │           AuthService.authenticateUser()
  │              ├─ UserRepository.findByUsername(...)
  │              ├─ BCryptPasswordEncoder.matches(...)
  │              ├─ JwtProvider.generateAccessToken(...)
  │              └─ RefreshTokenRepository.save(SHA-256(refreshToken))
  │
  └─ GET /api/v1/medications/...
       ▼
     api-gateway
       ├─ custom JwtAuthenticationFilter tries JWKS lookup + signature check
       ├─ Spring Security WebFlux resource-server also expects Bearer auth
       └─ forwards to downstream service only if both gateway layers succeed
                ▼
           downstream service validates JWT again
```

## Key files

| File | What it shows |
|---|---|
| `services/auth-service/src/main/java/com/jagapathi/pharmacy/auth/api/controller/AuthController.java` | Real public surface: `POST /register`, `POST /login`, `POST /refresh`, `GET /validate`, and `GET /.well-known/jwks.json` under `@RequestMapping("/api/v1/auth")`. There is **no logout method** even though the gateway has a logout route. |
| `services/auth-service/src/main/java/com/jagapathi/pharmacy/auth/application/service/AuthService.java` | Core auth logic: duplicate email/username checks, password-policy enforcement, BCrypt verification, `lastLoginAt` update, access-token issuance, refresh-token hashing with SHA-256, and refresh-token lookup via `findByTokenHash(...)`. |
| `services/auth-service/src/main/java/com/jagapathi/pharmacy/auth/infrastructure/jwt/JwtProvider.java` | Actual JWT behavior: RS256 signing with PEM-loaded RSA keys, 15-minute access tokens, 7-day refresh tokens, access-token claims `sub`, `username`, `roles`, `iat`, `exp`, and JWKS JSON generation with `kid="auth-service-key"`. |
| `services/auth-service/src/main/java/com/jagapathi/pharmacy/auth/infrastructure/config/SecurityConfig.java` | Stateless Spring Security config with `BCryptPasswordEncoder(12)` and `permitAll()` for register/login/refresh/validate/JWKS plus actuator. This service is not an OAuth2 resource server; it is the issuer. |
| `services/auth-service/src/main/java/com/jagapathi/pharmacy/auth/domain/User.java` | Persistent auth user model: UUID primary key, JSON `roles`, `@Version`, `lastLoginAt`, `UserDetails` implementation, and authority mapping to `ROLE_*`. |
| `services/auth-service/src/main/java/com/jagapathi/pharmacy/auth/domain/RefreshToken.java` | Refresh-token table model: only a SHA-256 fingerprint is stored, plus `expiresAt`, optional `revokedAt`, and `isValid()` logic. Good pattern for not storing raw refresh tokens in the DB. |
| `services/auth-service/src/main/resources/db/migration/V1__Initial_auth_schema.sql` | Flyway schema: `app_user` and `refresh_token` tables, unique username/email, JSON roles, `version`, FK from refresh token to user, and indexes on `token_hash`, `user_id`, `email`, `username`. |
| `services/auth-service/src/test/java/com/jagapathi/pharmacy/auth/api/controller/AuthControllerIT.java` | Useful behavioral proof: registration validation, successful login returning `accessToken`/`refreshToken`, invalid-password 401, refresh success, and public `GET /api/v1/auth/validate`. |
| `services/api-gateway/src/main/java/com/jagapathi/pharmacy/gateway/config/GatewayConfig.java` | Route table and the biggest operational clues: auth public routes go straight through, while most downstream routes apply `stripPrefix(2)` and hard-code service host:port pairs. |
| `services/api-gateway/src/main/java/com/jagapathi/pharmacy/gateway/filter/JwtAuthenticationFilter.java` | Custom global JWT filter that skips public auth endpoints, requires `Authorization: Bearer ...`, parses `kid` from the JWT header, fetches the public key from JWKS, validates RS256 signatures, and injects `X-User-Id` / `X-User-Roles` headers. |
| `services/api-gateway/src/main/java/com/jagapathi/pharmacy/gateway/filter/RateLimitingFilter.java` + `CorrelationIdFilter.java` | Cross-cutting ingress controls: Redis-backed fixed-window counters, `Retry-After` on 429s, fail-open behavior if Redis is down, and generation/propagation of `X-Correlation-ID`. |
| `services/api-gateway/src/main/resources/application.yml` | Gateway runtime config: WebFlux global CORS allowlist, 30s HTTP timeouts, Redis host/port, `spring.security.oauth2.resourceserver.jwt.jwk-set-uri`, `auth-service.jwks-uri`, server port `8080`, and exposed headers. |
| `services/api-gateway/src/test/java/com/jagapathi/pharmacy/gateway/filter/CorrelationIdFilterTest.java` + `JwtAuthenticationFilterTest.java` | Tests reveal what is and is not covered: public auth routes and correlation ID behavior are exercised, but there is no end-to-end test that proves a real RS256 token can pass through the custom JWT filter. |

## Core concepts

| Concept | Explanation | Why it matters |
|---|---|---|
| RS256 asymmetric JWT signing | `JwtProvider` signs with an RSA private key and the gateway/services validate with the public key from JWKS. Unlike HS256, validators never need the signing secret. | This is the right multi-service pattern because key distribution is safer: only `auth-service` holds the private key. |
| JWT structure | Real tokens here have a JOSE header, payload claims (`sub`, `username`, `roles`, `iat`, `exp`), and an RSA signature. The code does **not** currently set `iss`, `aud`, `jti`, or `kid` when building tokens. | Interviewers often ask you to explain exactly what is signed and which claims the platform depends on. |
| JWKS (RFC 7517) | `AuthController.jwks()` returns a JSON Web Key Set containing modulus `n`, exponent `e`, `alg=RS256`, and `kid=auth-service-key`. `JwtService` caches parsed keys for 1 hour. | JWKS lets many validators discover the right public key and supports future key rotation without sharing PEM files everywhere. |
| OAuth2 resource-server pattern | Downstream services and the gateway are configured to validate Bearer tokens as resource servers instead of trusting opaque session state. | This yields stateless auth and horizontal scalability: any instance can validate the JWT independently. |
| Defense in depth / zero trust | The gateway validates JWTs at the edge, and downstream services also validate them again. That means a bad route or bypass does not automatically become a privilege escalation. | This is one of the strongest architectural ideas in the repo, and it is worth calling out explicitly in interviews. |
| BCrypt password hashing | `auth-service` uses `new BCryptPasswordEncoder(12)` and never stores raw passwords. Registration validates complexity; login uses `matches()` against the stored hash. | BCrypt is slow on purpose, which raises the cost of offline password cracking after a DB leak. |
| Refresh-token fingerprint storage | The raw refresh token is returned once to the client, but the DB stores only `SHA-256(token)` in `refresh_token.token_hash`. | If the auth DB is leaked, the attacker does not automatically receive usable refresh tokens. |
| API Gateway pattern | `api-gateway` centralizes routing, auth checks, CORS, correlation IDs, security headers, and rate limiting before requests reach business services. | This keeps cross-cutting concerns out of every service and gives you a single public ingress. |
| WebFlux gateway vs MVC services | The gateway uses Spring Cloud Gateway/WebFlux because it is a high-concurrency edge proxy built around reactive filters; business services use Spring MVC because their workloads are ordinary request/DB flows. | Good interview answer: pick reactive where you need a non-blocking edge, not everywhere by default. |
| Fixed-window Redis rate limiting | `RateLimitingFilter` uses `RedisTemplate.opsForValue().increment(key)` plus a 60-second TTL. That is a simple fixed-window counter, **not** a token-bucket algorithm. | Knowing the real algorithm matters because burst behavior and fairness differ from token bucket or leaky bucket designs. |
| Correlation ID propagation | `CorrelationIdFilter` accepts or generates `X-Correlation-ID`, stores it in the exchange, adds it to the downstream request, and returns it in the response. | This is how you trace one user request across gateway logs and service logs without exposing sensitive payloads. |
| RFC 7807-style errors | `auth-service` uses `ProblemDetail`; gateway custom filters hand-build similar JSON bodies for 401/429. | Consistent structured errors help clients react programmatically and make logs/observability cleaner. |

## Common interview Q&A

**Q: Why use RS256 instead of HS256 for this platform?**  
A: With HS256 every validator would need the same shared secret, so any service that can verify could also mint tokens. Here `JwtProvider` alone has the private key; validators only fetch the public key from JWKS.

**Q: What exactly happens during login in the real code?**  
A: `AuthController.login()` delegates to `AuthService.authenticateUser()`, which loads the user by username, rejects inactive accounts, runs `BCryptPasswordEncoder.matches(...)`, updates `lastLoginAt`, generates a 15-minute access token and 7-day refresh token, hashes the refresh token with SHA-256, saves that hash in MySQL, and returns `TokenResponse`.

**Q: Is this session-based auth or stateless auth?**  
A: Stateless auth. Both the gateway and downstream services expect Bearer JWTs on every request. `SessionCreationPolicy.STATELESS` is set in the auth service, and gateway authorization is configured through WebFlux resource-server support.

**Q: How does the gateway learn the public key?**  
A: The custom `JwtService` calls `auth-service`’s JWKS URL with `WebClient`, parses the JSON keys, turns `n` and `e` into an `RSAPublicKey`, caches the map by `kid`, and reuses it until the 1-hour TTL expires.

**Q: What is the purpose of refresh tokens if the access token already exists?**  
A: Access tokens are short-lived and travel often, so they should expire quickly. Refresh tokens live longer and are used only to mint new access tokens, reducing the blast radius of an intercepted access token.

**Q: Why validate JWTs at both the gateway and the service?**  
A: Because the gateway is a convenience boundary, not the only trust boundary. A direct pod-to-pod request, route bug, or misconfigured ingress should not let an unvalidated JWT reach business logic.

**Q: Why is a reactive gateway useful even if business services are blocking MVC apps?**  
A: The gateway’s main job is I/O-heavy filtering and proxying. WebFlux is a good fit there because it can handle many concurrent connections efficiently while offloading actual business persistence to downstream services.

**Q: What is the real rate-limiting algorithm here?**  
A: A fixed-window counter. Each key is incremented in Redis and expires after 60 seconds. This is simpler than token bucket but can allow burstiness at window boundaries.

**Q: How would you explain JWKS rotation in an interview using this repo?**  
A: Add a second public key to the JWKS, start issuing new tokens with a new `kid`, keep validators accepting both keys during the overlap, then retire the old key after outstanding tokens expire. The current code is close conceptually but misses one critical piece: emitted tokens do not set `kid`.

**Q: What would you improve first if you inherited this auth/gateway code?**  
A: I would pick one gateway JWT-validation path, not two; make tokens emit `kid`, `jti`, and `iss`; implement real refresh-token revocation/reuse detection; and replace hard-coded route targets with environment-driven URIs so port drift does not break routing.

## Gotchas / real findings

- **Refresh-token rotation is only partial.** `AuthService.refreshAccessToken()` validates the existing refresh token and issues a brand-new one, but it does **not** revoke or delete the old stored token. `RefreshToken.revokedAt` exists, yet no code sets it, so there is no real refresh-token reuse detection.
- **`auth-service` has no logout endpoint.** `AuthController` exposes register/login/refresh/validate/JWKS only. Meanwhile `GatewayConfig` defines an `auth-logout` route to `http://auth-service:8081` with `stripPrefix(2)`, so even the gateway route shape is inconsistent with the service.
- **JWT header / JWKS mismatch.** The JWKS publishes `kid="auth-service-key"`, but `JwtProvider.generateAccessToken()` and `generateRefreshToken()` never place a `kid` in the JWT header. `JwtAuthenticationFilter` immediately reads `header.get("kid").asText()`, so authenticated gateway traffic is at risk of failing before routing.
- **Claim-type mismatch in the gateway.** Auth tokens store `roles` as a JSON array (`roles.stream().map(Role::name).toList()`), but the gateway reads it as `claims.get("roles", String.class)`. That is a likely runtime type mismatch when building `X-User-Roles`.
- **Route rewriting is inconsistent with downstream controller mappings.** For product/customer/pharmacy/inventory/prescription/order/payment/notification/audit routes, `GatewayConfig` applies `stripPrefix(2)`. That converts `/api/v1/medications/...` to `/medications/...`, but downstream controllers are generally mapped under `/api/v1/...`, so the forwarded path shape is suspicious.
- **Gateway port drift is real.** The route table points product-service to `8082`, customer-service to `8083`, and audit-service to `8090`, while the real service configs are product `8081`, customer `8082`, and audit `8086` in their respective `application.yml` files.
- **Public auth endpoints are exempt from gateway rate limiting.** `RateLimitingFilter.EXEMPT_ENDPOINTS` includes login/register/refresh/JWKS, which is the opposite of the repo’s cross-cutting standard that says ingress rate limiting should protect public auth endpoints.
- **Per-user rate limiting does not currently activate.** `RateLimitingFilter` reads `exchange.getAttribute("userId")`, but the gateway code never writes that attribute; it only writes headers and MDC entries. In practice the filter falls back to IP-based limiting only.
- **Correlation IDs are not propagated into auth-service problem responses.** `auth-service` exception handlers generate a fresh random UUID for `correlationId` instead of reusing the gateway’s `X-Correlation-ID`, which weakens traceability when errors happen inside auth.
- **The gateway effectively has two JWT-validation stories.** `application.yml` configures Spring Security resource-server JWT validation, but there is also a custom JJWT-based `JwtAuthenticationFilter`. That duplication raises maintenance cost and increases the chance of logic drift.

## Trace-through

**Example: a customer logs in, then tries a protected route**

1. The client sends `POST /api/v1/auth/login` to the gateway.
2. `JwtAuthenticationFilter` sees `/api/v1/auth/login` in its public allowlist and skips Bearer-token validation.
3. `CorrelationIdFilter` preserves or creates `X-Correlation-ID`; `GatewayConfig` routes the request to `http://auth-service:8081` without stripping the `/api/v1/auth/login` path.
4. `AuthController.login()` calls `AuthService.authenticateUser()`.
5. `AuthService` loads the user with `UserRepository.findByUsername(...)`, verifies the BCrypt hash, updates `lastLoginAt`, and calls `JwtProvider.generateAccessToken(...)` plus `generateRefreshToken(...)`.
6. The raw refresh token is returned to the client, but only `SHA-256(refreshToken)` is persisted in `refresh_token.token_hash`.
7. The client later sends `GET /api/v1/medications/...` with `Authorization: Bearer <accessToken>`.
8. At the gateway, the custom JWT filter splits the token, reads the JOSE header, extracts `kid`, fetches the public key from `auth-service` JWKS via `JwtService`, and verifies the signature.
9. **Real-world gotcha:** current auth tokens do not set `kid`, so this protected request path is likely to fail before the request ever reaches the downstream service.
10. If that mismatch were fixed, the gateway would forward the request and the downstream service would still validate the JWT again as a separate trust boundary.
