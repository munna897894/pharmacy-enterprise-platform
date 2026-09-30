# Cross-cutting standards

This document is the platform standard. The
[resilience matrix](generated-resilience-matrix.md) records what the current
implementation actually wires; circuit-breaker and bulkhead items below remain
target controls where the matrix does not list them.

## Correlation and trace context

- Gateway accepts a valid `X-Correlation-ID` or creates a UUID.
- Return the same value in every HTTP response.
- Propagate it on internal HTTP calls and Kafka headers.
- Put it in logging MDC and clear MDC after the request/record.
- Use W3C Trace Context for distributed tracing. Correlation ID is for operations/search; trace ID is for tracing. Preserve both.

## Error handling

- Use Spring `ProblemDetail` with `errorCode`, `correlationId` and `timestamp` extensions.
- Maintain a small stable error-code catalog in each service.
- Never return Java class names, stack traces, SQL, hostnames, internal URLs or secrets.
- Log the exception once at the correct boundary. Avoid duplicate stack traces from controller and service layers.

## Security

- BCrypt passwords with an appropriate cost for local learning.
- RSA private key exists only in auth-service secrets; services receive the public key/JWKS.
- Validate token signature, issuer, expiry and expected audience.
- Default-deny authorization; explicitly permit login/JWKS/health routes.
- Method security for ownership or role-based business checks.
- Strict CORS allowlist from configuration.
- Gateway rate limits login and public-facing endpoints using Redis.
- Reject oversized payloads and validate all inputs.
- Never commit credentials, `.env`, private keys, Terraform state or kubeconfigs.
- Run dependency and image scans in CI.

## Logging

Required fields: timestamp, severity, service, environment, thread, logger, message, correlationId, traceId, spanId and selected non-sensitive identifiers.

Never log:

- passwords or JWT/refresh tokens
- prescription instructions/text
- date of birth, address, email or full phone number
- payment token or provider secret
- JDBC URLs containing passwords

Use parameterized logs. Avoid INFO logs inside tight loops or for every cache hit in normal operation.

## Resilience

- Set connect/read timeouts for every client.
- Retry only transient failures and only when the operation is safe or idempotent.
- Use exponential backoff plus jitter and bounded attempts.
- Circuit breakers protect external verification; fallbacks must not invent success.
- Bulkheads prevent a slow dependency from consuming all request threads.
- Gateway rate limits protect ingress; service-level concurrency limits protect dependencies.
- Graceful shutdown stops accepting traffic, completes in-flight requests and closes resources within a configured timeout.

## Database

- Flyway-only schema evolution; `ddl-auto=validate`.
- HikariCP pool size is explicit per environment.
- Transactions are short and do not include slow external HTTP calls.
- Add indexes based on query paths.
- Use optimistic locking for inventory/order/payment state.
- Pagination is mandatory for collections.

## Caching

- Cache-aside for medication reads/search.
- Explicit TTLs.
- Invalidate affected ID/search keys after writes.
- Cache outage must not make product reads unavailable.
- Do not cache authorization decisions or mutable order state in the core build.

## Health and probes

- Liveness indicates the process can continue; it must not depend on MySQL/Kafka.
- Readiness indicates ability to serve traffic and may include critical dependencies.
- Startup probe gives JVM applications time to initialize.
- Expose only required Actuator endpoints.

## Metrics

At minimum:

- HTTP request rate, error rate and latency histograms
- JVM heap, GC, threads and process CPU
- Hikari active/idle/pending connections
- Kafka producer/consumer errors, consumer lag and DLT count
- cache hit/miss/error count
- outbox unpublished count and oldest age
- business counters: prescriptions verified/rejected, inventory reservation result, payments completed/failed, orders confirmed/cancelled

Metrics must use low-cardinality tags such as service, endpoint template, status group and outcome. Never use resource IDs as metric tags.

## Configuration precedence

Use `application.yml` defaults, profile YAML, then environment variables/secrets. Fail fast when mandatory configuration is absent. Document every variable in `.env.example` without real secret values.
