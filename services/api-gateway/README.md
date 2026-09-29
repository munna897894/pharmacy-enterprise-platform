# API Gateway Service

The single entry point for all external HTTP traffic to the pharmacy-enterprise-platform. Handles JWT validation, rate limiting, correlation IDs, security headers, and request routing to backend microservices.

## Features

### 1. Request Routing
- Routes all `/api/v1/**` requests to appropriate backend services
- 10 backend service routes: auth, product, customer, pharmacy, inventory, prescription, order, payment, notification, audit
- Path-based routing with intelligent method matching

### 2. JWT Validation
- Validates JWT signatures using auth-service's RSA public key (JWKS)
- Caches JWKS for 1 hour with automatic refresh on 401 responses
- Extracts claims (sub, roles, exp, iat, jti) and passes to downstream services
- Public endpoints bypass JWT check: login, register, refresh, JWKS
- Private endpoints require valid JWT

### 3. Correlation ID Propagation
- Generates UUID if X-Correlation-ID not provided
- Propagates to all downstream HTTP calls
- Included in response headers for client tracing
- Added to logs for request tracing

### 4. Security Headers
- X-Content-Type-Options: nosniff
- X-Frame-Options: DENY
- X-XSS-Protection: 1; mode=block
- Strict-Transport-Security: max-age=31536000; includeSubDomains
- Content-Security-Policy: default-src 'self'

### 5. Rate Limiting (Redis-based)
- Per-user rate limit: 100 requests/minute (by userId from JWT)
- Per-IP rate limit: 1000 requests/minute (by client IP)
- Token bucket algorithm with 1-minute sliding window
- Returns 429 Too Many Requests with Retry-After header
- Exempts public auth endpoints and actuator/health

### 6. CORS Configuration
- Allowed origins: http://localhost:3000, https://app.example.com
- Allowed methods: GET, POST, PUT, PATCH, DELETE, OPTIONS
- Allowed headers: Content-Type, Authorization, X-Correlation-ID, X-Idempotency-Key
- Credentials: true
- Max age: 3600 seconds

### 7. Resilience
- Timeout: 30 seconds connect, 30 seconds read
- Retry: 2 retries for GET/HEAD/DELETE, 0 for POST/PUT/PATCH
- Circuit breaker: Opens after 5 consecutive failures, half-opens after 10 seconds
- Fallback responses with 503 Service Unavailable

### 8. Structured Logging
- JSON logging in production profile
- Includes correlationId, userId, response time, status code
- Never logs Authorization header (tokens)

## Architecture

```
Client
  |
  v
API Gateway (port 8080)
  |
  +-- JwtAuthenticationFilter (validates JWT signature)
  +-- CorrelationIdFilter (generates/propagates correlation ID)
  +-- SecurityHeaderFilter (adds security headers)
  +-- RateLimitingFilter (checks rate limits via Redis)
  +-- LoggingFilter (structured request/response logging)
  |
  v
Route Dispatcher
  |
  +-- /api/v1/auth/** → auth-service:8081
  +-- /api/v1/medications/** → product-service:8082
  +-- /api/v1/customers/** → customer-service:8083
  +-- /api/v1/pharmacies/** → pharmacy-service:8084
  +-- /api/v1/inventory/** → inventory-service:8085
  +-- /api/v1/prescriptions/** → prescription-service:8086
  +-- /api/v1/orders/** → order-service:8087
  +-- /api/v1/payments/** → payment-service:8088
  +-- /api/v1/notifications/** → notification-service:8089
  +-- /api/v1/audit/** → audit-service:8090
```

## Configuration

### Environment Variables

```
REDIS_HOST=localhost (default)
REDIS_PORT=6379 (default)
AUTH_SERVICE_HOST=auth-service (default)
AUTH_SERVICE_PORT=8081 (default)
ENVIRONMENT=local (default)
```

### Application Properties

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST}
      port: ${REDIS_PORT}
  cloud:
    gateway:
      server:
        webflux:
          metrics:
            enabled: true

auth-service:
  jwks-uri: http://${AUTH_SERVICE_HOST}:${AUTH_SERVICE_PORT}/api/v1/auth/.well-known/jwks.json

rate-limit:
  per-user-per-minute: 100
  per-ip-per-minute: 1000
```

## Testing

### Unit Tests
- JwtAuthenticationFilterTest: JWT validation, public endpoints
- CorrelationIdFilterTest: ID generation, propagation
- SecurityHeaderFilterTest: All security headers present
- RateLimitingFilterTest: Rate limit logic

### Run Tests
```bash
./mvnw test -pl services/api-gateway
```

### Build
```bash
./mvnw clean package -pl services/api-gateway
```

### Run
```bash
java -jar services/api-gateway/target/api-gateway-0.0.1-SNAPSHOT.jar
```

Or with Spring Boot Maven plugin:
```bash
./mvnw spring-boot:run -pl services/api-gateway
```

## Endpoints

### Health/Actuator
- GET /actuator/health - Application health (public)
- GET /actuator/health/readiness - Readiness probe
- GET /actuator/health/liveness - Liveness probe
- GET /actuator/info - Application info
- GET /actuator/prometheus - Prometheus metrics

### Public Auth Routes
- POST /api/v1/auth/login - User login
- POST /api/v1/auth/register - User registration
- POST /api/v1/auth/refresh - Token refresh
- GET /api/v1/auth/.well-known/jwks.json - Public key distribution

### Protected Routes (require JWT)
All other `/api/v1/**` routes require valid JWT in Authorization header:
```
Authorization: Bearer <access_token>
```

## Metrics

Exposed via Prometheus endpoint at `/actuator/prometheus`:
- http_server_requests_seconds: Request latency
- http_server_requests_seconds_count: Request count
- http_server_requests_seconds_max: Max response time
- spring_cloud_gateway_requests: Gateway request metrics
- jvm_memory_used: JVM heap memory
- jvm_gc_*: Garbage collection metrics

## Dependencies

- Spring Cloud Gateway (WebFlux)
- Spring Security OAuth2 Resource Server
- Spring Data Redis (Jedis)
- Spring Cloud Resilience4j (Circuit Breaker)
- JJWT (JWT parsing/validation)
- Micrometer Prometheus Registry
- OpenTelemetry (distributed tracing)

## Notes

- Gateway uses Kubernetes DNS for service discovery (e.g., http://auth-service:8081)
- No business logic - thin gateway pattern
- No database access from gateway
- CORS and security headers protect browser clients
- Rate limiting uses Redis to support multiple gateway instances
- JWT validation includes signature, issuer, expiry checks
- Every response includes X-Correlation-ID header
