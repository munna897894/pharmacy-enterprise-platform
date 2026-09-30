# API Gateway

The Spring Cloud Gateway WebFlux application is the public HTTP entry point.
It listens on port `8080`; its management endpoints use the internal port
`9081` by default. Current platform topology and public API behavior are
defined in [`../../docs/02-architecture.md`](../../docs/02-architecture.md)
and [`../../docs/06-api-contracts.md`](../../docs/06-api-contracts.md).

## Routes

| Route | Upstream |
|---|---|
| `POST /api/v1/auth/login` | `auth-service:8081` |
| `POST /api/v1/auth/register` | `auth-service:8081` |
| `POST /api/v1/auth/refresh` | `auth-service:8081` |
| `POST /api/v1/auth/logout` | `auth-service:8081` |
| `GET /api/v1/auth/.well-known/jwks.json` | `auth-service:8081` |
| `/api/v1/medications/**` (GET, POST, PUT, PATCH) | `product-service:8082` |
| `/api/v1/customers/**` | `customer-service:8083` |
| `/api/v1/pharmacies/**` | `pharmacy-service:8084` |
| `/api/v1/inventory/**` | `inventory-service:8085` |
| `/api/v1/prescriptions/**` | `prescription-service:8086` |
| `/api/v1/orders/**` | `order-service:8087` |
| `/api/v1/payments/**` | `payment-service:8088` |
| `/api/v1/notifications/**` | `notification-service:8089` |
| `GET /api/v1/audit/**` | `audit-service:8090` |
| `/api/v1/mock/**` | `external-mock-service:8080` |

The mock route is for local/test failure controls and must not be exposed in a
production-like gateway. Downstream services retain their own authorization.

## Request handling

- The custom JWT filter exempts login, registration, refresh and JWKS. Other
  routes require a valid JWT Authorization header; the filter accepts RS256,
  retrieves the matching public key from the auth-service JWKS endpoint,
  verifies the signature and expiry, then forwards user/role/token
  identifiers in `X-User-Id`, `X-User-Roles` and `X-Token-Jti` headers.
- The correlation filter keeps a valid `X-Correlation-ID` or creates a UUID,
  forwards it and adds it to the response on requests that reach that filter.
- Redis rate limiting uses 60-second counters. Configured limits are 100 per
  user and 1,000 per IP per minute. The rate-limit filter reads `userId` from
  an exchange attribute, but the current JWT filter does not populate that
  attribute; therefore the implemented limit is currently IP-scoped.
  Redis errors fail open.
- Configured connect and response timeouts are 30 seconds. The gateway
  configuration does not define per-route retries or circuit-breaker filters.
- CORS allows `http://localhost:3000` and `https://app.example.com`, the
  configured REST methods, and the configured authorization/correlation/
  idempotency headers. Security headers are added by the gateway filter.

## Configuration and operations

The main environment settings are `AUTH_SERVICE_HOST`,
`AUTH_SERVICE_PORT`, `REDIS_HOST`, `REDIS_PORT`, `MANAGEMENT_SERVER_PORT`,
`ENVIRONMENT`, `TRACING_SAMPLING_PROBABILITY` and
`OTEL_EXPORTER_OTLP_ENDPOINT`. Kubernetes uses service DNS; local values are
provided by the deployment configuration.

The application listens on `8080`. Health, info and Prometheus endpoints are
served on the management port (`9081` by default); Kubernetes and Prometheus
target that internal port. Other actuator endpoints are denied.

## Build and test

Run from the repository root:

```bash
./mvnw -pl services/api-gateway -am test
./mvnw -pl services/api-gateway -am package
```

The first command runs the current gateway test suite and required reactor
dependencies. A green unit/slice test run is not a live-cluster smoke test.
