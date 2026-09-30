# Audit Service Quick Start

Run commands from the repository root. Java 21 and the Maven Wrapper are
required.

## Build and test

```bash
./mvnw -pl services/audit-service -am test
./mvnw -pl services/audit-service -am verify
```

The `test` phase runs Surefire tests, including `AuditServiceIntegrationTest`.
That test class may skip its Testcontainers checks when Docker is unavailable.
The audit-service POM does not bind Maven Failsafe goals to `verify`.

## Run locally

The application requires its configured MySQL schema and Kafka broker. Set
the connection values through environment variables or use the repository's
local deployment setup; do not place credentials in this guide or source
control.

```bash
./mvnw -pl services/audit-service -am spring-boot:run
```

The default application port is `8090`. Check health with:

```bash
curl --fail http://localhost:8090/actuator/health
```

## Configuration

The service reads `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`,
`KAFKA_BOOTSTRAP_SERVERS`, `JWT_ISSUER_URI`, `JWT_JWK_SET_URI`,
`OTEL_EXPORTER_OTLP_ENDPOINT` and `TRACING_SAMPLING_PROBABILITY`. Defaults are
defined in `src/main/resources/application.yml`. For the canonical full-fleet
local Kubernetes topology, MySQL and Kafka are host services; Redis and the
external mock service run in the cluster. The audit service does not require
Redis.

The API routes are ADMIN-only and use `/api/v1/audit`. See
[`ARCHITECTURE.md`](ARCHITECTURE.md) for active topic bindings, DLT/retry
settings, endpoint details and payload limitations.
