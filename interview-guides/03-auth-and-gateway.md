# 03 — Auth and Gateway (Prompt 03)

## Summary

`auth-service` issues RSA-signed JWTs and publishes JWKS; `api-gateway` is the WebFlux edge for JWT validation, routing, correlation IDs and Redis-backed rate limiting. Business services validate JWTs again. The current route table preserves `/api/v1` and targets the actual internal ports; old warnings about `stripPrefix(2)`, missing `kid`/`iss`, and product/customer/audit port collisions describe earlier code, not the present gateway.

## Login and protected request

```text
POST /api/v1/auth/login -> gateway :8080 -> auth :8081
  -> BCrypt password match -> RS256 access/refresh JWTs
     JOSE kid + claims including iss/aud/sub/roles/iat/exp
  -> JWKS at /api/v1/auth/.well-known/jwks.json

GET /api/v1/medications/... -> gateway JWT checks -> product :8082
  -> product independently validates JWT -> application/repository -> response
  gateway management/Prometheus :9081 (internal), not the public API port
```

## Key files

| File | What to study |
|---|---|
| `services/auth-service/.../infrastructure/jwt/JwtProvider.java` | RSA PEM loading, `kid`, configurable issuer/audience, signed tokens and JWKS. |
| `services/auth-service/.../application/service/AuthService.java` | BCrypt login and refresh-token fingerprint persistence. |
| `services/api-gateway/.../config/GatewayConfig.java` | `/api/v1` route predicates with internal auth 8081, product 8082, customer 8083, audit 8090 and mock 8080. |
| `services/api-gateway/.../filter/{JwtAuthenticationFilter,RateLimitingFilter,CorrelationIdFilter}.java` | Edge auth, Redis rate limiter, correlation propagation. |
| `services/api-gateway/src/main/resources/application.yml` | JWKS/issuer URLs and separate management port 9081. |

## Core concepts

| Concept | Explanation |
|---|---|
| RS256 vs HS256 | Only auth holds the private signing key; gateway and downstream resource servers use JWKS public keys. |
| Defense in depth | The gateway authenticates first, but internal services still validate the forwarded JWT. |
| JWKS and `kid` | `kid` selects a published public key; issuer/audience claims align with validators. Rotation requires an overlapping key window, not just a new key ID. |
| Refresh fingerprint | Only a hash of the refresh token is persisted; never log raw tokens. |
| Correlation | Gateway creates/preserves `X-Correlation-ID`, propagates it and includes it in the response. |
| Rate limiting | A Redis-backed ingress counter constrains requests; review public-auth exemptions separately from the general policy. |

## Common interview Q&A

**Q: Why revalidate tokens downstream?**
A: A bad route or direct internal call should not bypass service authorization. Gateway validation is not a substitute for resource-server validation in each business service.

**Q: How does the gateway obtain the key?**
A: It fetches the auth service's JWKS, caches public keys keyed by the token header's `kid` and checks the RSA signature. The issued tokens now include `kid` and `iss`; the prior mismatch was fixed.

**Q: Why use WebFlux only at the gateway?**
A: The edge proxies concurrent I/O; business services use MVC and blocking JPA where a reactive stack would not improve the persistence path.

**Q: What is the practical difference between 401 and 403?**
A: Invalid/missing credentials yield 401; a valid identity without a required role/ownership yields 403. Preserve correlation IDs without echoing tokens.

**Q: How is a timed-out or retried login different from refreshing a token?**
A: Login checks credentials and issues a new token pair; refresh uses a stored token fingerprint to mint tokens. Do not describe refresh as a server session or assume revocation without verifying that path.

## Remaining review points

- The gateway has both custom JWT filtering and Spring resource-server configuration; keep their validation behavior aligned.
- Check public-auth rate-limit exemptions against the desired brute-force policy rather than describing all public routes as protected by the Redis filter.
- `auth-service` exposes register/login/refresh/validate/JWKS; a configured gateway logout route is not evidence that auth implements a logout controller.
- Only the gateway is normal ingress. In local Kubernetes access is via `localhost:18080` port-forward; the application's `8080` and management `9081` are distinct.
