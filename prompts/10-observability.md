# Prompt 10 — Metrics, logs and distributed traces

> **Status (2026-09-30): Complete.** Current baseline and deployment-specific observability are documented in [observability](../docs/11-observability.md).

## Give Copilot these files

- `docs/02-architecture.md`
- `docs/08-cross-cutting-standards.md`
- `learning/incident-labs.md`

## Prompt 10A — Application instrumentation

```text
Instrument every service consistently without changing business behavior.

Add/configure:
- Spring Boot Actuator health/info/prometheus;
- Micrometer HTTP/JVM/Hikari/Kafka/cache metrics;
- custom low-cardinality business, outbox and DLT metrics from docs/08-cross-cutting-standards.md;
- OpenTelemetry-compatible tracing with W3C context propagation over gateway, HTTP clients and Kafka;
- structured JSON logs outside local profile with service/environment/correlationId/traceId/spanId;
- sanitized exception logging and no sensitive fields;
- separate liveness/readiness health groups.

Add tests for correlation ID propagation and log/metric tag safety where feasible. Do not use resource IDs as metric tags.
```

## Prompt 10B — Local observability stack

```text
Add an observability Compose overlay containing Prometheus, Grafana and OpenTelemetry Collector. Add only free/open-source components and pin image versions.

Create:
- Prometheus scrape configuration/service discovery suitable for Compose;
- OTel Collector receivers/processors/exporters for local traces and metrics;
- a local trace backend such as Jaeger or Grafana Tempo;
- provisioned Grafana datasources and dashboards;
- alert rules for high 5xx rate, p95 latency, pod/container restart signal where available, Hikari pending connections, Kafka lag/DLT, old outbox records and JVM heap pressure;
- a dashboard with RED metrics, JVM, Hikari, Kafka and business outcomes.

Validate configuration and document how to follow one order using correlation ID and trace ID.
```

## Verification

- One order trace crosses gateway, order, Kafka, inventory, payment and notification where async propagation supports it.
- The same correlation ID appears in searchable logs.
- Grafana shows request rate/errors/latency, JVM, Hikari and Kafka panels.
- Force slow verification and identify the slow span.
- Force DB pool contention and see pending connections/latency.
- No secrets or fictional sensitive profile/prescription fields appear in logs.

## Study

- Logs vs metrics vs traces
- RED and USE methods
- Span context over asynchronous messaging
- Cardinality cost
- Symptom alert vs cause
- Service-level indicator basics

Vendor-specific integrations for Dynatrace and Splunk are intentionally out of scope for this prompt; implement them separately under Prompt 14.
