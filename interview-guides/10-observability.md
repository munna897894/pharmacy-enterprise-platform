# 10 — Observability (Prompt 10)

## Summary

This area is **PARTIALLY IMPLEMENTED** in the real repo. The codebase has Actuator endpoints, Prometheus registry dependencies, some correlation-ID logging, a Compose overlay with Prometheus/Grafana/OpenTelemetry Collector, and Kubernetes scrape annotations — but it does **not** yet have a complete end-to-end tracing/logging/alerting implementation that matches Prompt 10's full ambition.

The most interview-worthy takeaway is the gap between **having observability libraries on the classpath** and **having a production-grade observability system**. This repo demonstrates the first part fairly well; the second part is still incomplete, especially around structured JSON logs, full trace propagation, business metrics, and meaningful dashboards/alerts.

## Diagram — current repo observability flow

```text
                    CURRENT REPO STATE (real, partial)

Client
  │
  │ HTTP + optional X-Correlation-ID
  ▼
api-gateway
  │  - CorrelationIdFilter creates/preserves X-Correlation-ID
  │  - LoggingFilter writes MDC-backed log lines
  │  - Actuator exposes /actuator/health,/metrics,/prometheus
  │  - OTLP endpoint property points at otel-collector
  ▼
downstream services
  │  - most services expose health/info/prometheus
  │  - some also expose metrics/readiness/liveness
  │  - Micrometer + Prometheus deps present in many modules
  │
  ├──────────────► Prometheus scrapes a subset of services
  │                (infra/compose/prometheus.yml)
  │
  ├──────────────► Grafana exists
  │                but dashboard/provisioning is minimal
  │
  ├──────────────► Prometheus alert rules exist
  │                but only one basic ServiceDown alert
  │
  └──────────────► OTel Collector receives OTLP
                   but currently exports to logging only
                   (no Jaeger/Tempo backend configured)
```

## Key files

| File | What it shows |
|---|---|
| `services/api-gateway/src/main/resources/application.yml` | The strongest real observability config in the repo: Actuator exposure, liveness/readiness probes, histogram for `http.server.requests`, common metric tags (`application`, `environment`), and an OTLP endpoint property pointing at `otel-collector:4317`. |
| `services/api-gateway/src/main/java/com/jagapathi/pharmacy/gateway/filter/CorrelationIdFilter.java` | Real correlation-ID handling at the platform edge: preserves incoming `X-Correlation-ID` or creates a UUID, forwards it downstream, and echoes it in responses. |
| `services/api-gateway/src/main/java/com/jagapathi/pharmacy/gateway/filter/LoggingFilter.java` | MDC-backed request logging with method, path, status code and duration. Useful for discussing correlation vs request logging, even though it is not structured JSON. |
| `services/api-gateway/src/test/java/com/jagapathi/pharmacy/gateway/filter/CorrelationIdFilterTest.java` | Verifies the gateway really generates and preserves `X-Correlation-ID`; this is one of the few concrete observability-focused tests in the repo. |
| `services/auth-service/src/main/resources/application.yml` | Shows another service exposing `health,info,prometheus,readiness,liveness`; useful because it reveals observability config is present but inconsistent across modules. |
| `services/auth-service/src/main/java/com/jagapathi/pharmacy/auth/api/exception/GlobalExceptionHandler.java` | A useful “negative example”: error responses include a `correlationId`, but it is generated fresh with `UUID.randomUUID()` rather than reusing request context. |
| `services/product-service/src/main/resources/application.yml` | Shows a lighter setup: `health,info,prometheus` enabled, but no explicit `metrics` exposure, no tracing section, and no structured logging config. |
| `infra/compose/compose.yml` | Real local observability stack skeleton: Prometheus, Grafana and OpenTelemetry Collector containers are defined with pinned images. |
| `infra/compose/prometheus.yml` | Scrape targets for a subset of services. Great example of “real config exists, but scope is incomplete” because only five services are scraped. |
| `infra/compose/otel-collector.yaml` | The collector currently receives OTLP traces/metrics and exports them to `logging`; that is useful for learning, but it is not a real trace-backend setup like Tempo or Jaeger. |
| `infra/prometheus/alerts.yml` | Real alerting file, but it currently defines only `ServiceDown`. This makes it a good study artifact for the difference between “some alerting exists” and “SRE-grade alert coverage exists.” |
| `infra/grafana/dashboards/overview.json` + `infra/kubernetes/base/api-gateway.yaml` | The dashboard file exists but has zero panels; the Kubernetes manifest shows real readiness/liveness probes and Prometheus scrape annotations. |

## Core concepts

| Concept | Explanation | Why it matters |
|---|---|---|
| Metrics vs logs vs traces | Metrics summarize behavior over time, logs capture discrete events, and traces connect one request across multiple hops. | Interviewers often test whether you know when each signal is best: metrics for detection, traces for causality, logs for detail. |
| RED method | RED = **Rate, Errors, Duration** for request-driven services. Track request volume, failure rate, and latency percentiles. | This platform is HTTP/Kafka heavy, so RED is the most natural first dashboard for gateway and business APIs. |
| USE method | USE = **Utilization, Saturation, Errors** for resources like CPU, DB pools, Kafka consumers, and JVM heap. | RED tells you user symptoms; USE helps find the constrained resource causing them. |
| Four Golden Signals | Latency, traffic, errors, and saturation. Similar spirit to RED+USE, especially in Google SRE terminology. | Good interview answer: RED is usually app/request focused; Golden Signals are broader service SRE signals. |
| Correlation ID vs trace ID | A correlation ID is an operational search key chosen by the platform; a trace ID is generated by tracing instrumentation and identifies a distributed trace. | This repo explicitly mandates both. You often search logs with correlation ID and inspect spans by trace ID. They are related but not the same thing. |
| W3C trace context | Standard headers like `traceparent` and `tracestate` carry trace identity across network hops. | Standard propagation matters in polyglot/multi-tool environments and was explicitly intended in Prompt 10. |
| Low-cardinality metric tags | Tags like `service=order-service` or `environment=local` are safe. Tags like `orderId`, `customerId`, or `prescriptionId` explode time-series cardinality. | The repo rules explicitly ban resource IDs as metric tags because Prometheus costs and query performance can spiral badly. |
| Histogram vs average latency | Percentile histograms let you ask p95/p99 latency; averages hide tail pain. | `http.server.requests` histogram is enabled in the gateway config, which is the right direction for SLO-style latency analysis. |
| Actuator health groups | Liveness answers “should the process be restarted?” while readiness answers “should this instance receive traffic?” | Kubernetes probes depend on this split. A deadlocked app may fail liveness; a dependency outage may fail readiness only. |
| Context propagation | Trace/correlation context must move across HTTP headers and Kafka headers, not just stay in one process. | Without propagation, every service sees a different “root,” making distributed debugging almost impossible. |
| SLI/SLO thinking | An SLI is a measurable indicator (e.g., p95 latency); an SLO is the target (e.g., 95% of requests under 300 ms). | Strong observability is not only collection — it is also deciding what “good enough” service behavior means. |
| Structured JSON logging | Instead of free-form text, logs are emitted as JSON with stable fields like `service`, `environment`, `traceId`, `spanId`, `correlationId`. | Structured logs enable reliable search, dashboards and alerting in Splunk/ELK/Loki. This repo has not reached that state yet. |

## Common interview Q&A

**Q: Why is enabling `/actuator/prometheus` not the same as “we have observability”?**  
A: Because exposure is only one layer. You still need useful dashboards, alert rules, low-cardinality labels, meaningful business metrics, trace propagation, and searchable structured logs. This repo proves the endpoint exists; it does not yet prove a full observability practice exists.

**Q: What is the practical difference between correlation ID and trace ID?**  
A: Correlation ID is a platform-defined request key used in logs, error responses, and sometimes business support tooling. Trace ID comes from the tracing system and ties spans together automatically. In a mature system, logs contain both so you can jump from a log search to a trace view.

**Q: Why are high-cardinality tags dangerous in Prometheus?**  
A: Every unique label set becomes a new time series. If you tag with `orderId`, millions of orders can become millions of series, which increases RAM, storage, scrape cost, and query latency. That is why standards docs explicitly ban customer/order/prescription IDs as metric tags.

**Q: What should readiness include that liveness should not?**  
A: Readiness can depend on whether the app can safely serve traffic — e.g., Flyway complete, DB reachable, Kafka consumer initialized. Liveness should stay narrower and mostly answer whether the process itself is healthy enough not to be restarted pointlessly.

**Q: Why are percentile histograms more useful than averages for APIs?**  
A: Averages flatten out tail latency. If 95% of requests are fast and 5% are very slow, the average may look okay while real users still suffer. Histograms enable p95/p99 views and SLO-style alerting.

**Q: How should tracing work across Kafka?**  
A: The producer should inject trace context into record headers; the consumer should extract it and create child spans so the asynchronous hop still appears in one distributed trace. That is conceptually required by Prompt 10, but not clearly implemented end-to-end in this repo.

**Q: What is a good first dashboard for this platform?**  
A: Start with gateway and core business services using RED metrics: request rate, 4xx/5xx rate, p50/p95/p99 latency, then add JVM, Hikari, Kafka lag, outbox age, DLT volume, and a few low-cardinality business counters.

**Q: Why do structured logs matter if we already have traces?**  
A: Traces show timing and causal path, but logs capture payload-independent detail: validation failures, retries, external response codes, and branch decisions. Structured logs make that detail searchable without fragile text parsing.

**Q: What is the difference between a symptom alert and a cause metric?**  
A: A symptom alert tells you user-facing pain exists, like 5xx rate or p95 latency. A cause metric helps narrow the reason, like Hikari pending connections, JVM heap pressure, or Kafka lag. Good observability uses both.

**Q: Why is “business metric” instrumentation different from auto-instrumentation?**  
A: Auto-instrumentation gives you generic HTTP/JVM/Kafka timings, but business metrics tell you outcomes that matter to the domain: orders confirmed, reservations released, payment failures, DLT counts, outbox backlog. Those are mostly missing here.

## Gotchas / real findings

- **Actuator is real, but inconsistent.** Some services expose `health,info,metrics,prometheus`; others expose only `health,info,prometheus`; auth-service exposes readiness/liveness explicitly; product-service does not expose `metrics` in the same way as the gateway.
- **Tracing dependencies exist more often than tracing configuration.** Several modules include `micrometer-tracing-bridge-otel` and `opentelemetry-exporter-otlp`, but the clearest OTLP endpoint property is only visible in `api-gateway/application.yml`.
- **No explicit `management.tracing.*` or baggage propagation config was found** in service `application*.yml` files.
- **Custom metrics are basically absent.** No meaningful `Counter.builder`, `Timer.builder`, `@Timed`, or business/outbox/DLT instrumentation was found in the code. That means the repo is relying mostly on Spring/Micrometer auto-instrumentation.
- **Grafana is mostly a placeholder today.** `infra/grafana/dashboards/overview.json` contains an empty `panels` array, so the “dashboard exists” claim is technically true but operationally thin.
- **Prometheus alerting is barely started.** `infra/prometheus/alerts.yml` only defines `ServiceDown`; Prompt 10 asked for much richer alerting like latency, 5xx rate, Hikari pressure, Kafka lag, DLT, and outbox age.
- **The OTel Collector has no real backend exporter.** It exports to `logging`, not to Tempo/Jaeger/Dynatrace/etc., so traces are not landing in a normal trace UI.
- **Structured JSON logging is not implemented.** Real configs use pattern-based plain text like `%d{ISO8601} %p %c [%t] ...`, not JSON encoders.
- **Some services generate fresh correlation IDs inside exception handlers** instead of reusing the request correlation ID. For example, `auth-service` and `order-service` `GlobalExceptionHandler`s set `correlationId` to a new `UUID.randomUUID()`, which breaks end-to-end searchability.
- **Kafka trace propagation is not demonstrably wired.** Kafka event classes may carry a `correlationId`, but that is not the same as true trace header propagation.
- **Prometheus scrape coverage is incomplete.** The checked-in Compose scrape config only lists gateway, auth, inventory, order, and payment — not the whole 11-service platform.
- **The observability docs are thinner than the code suggests.** `docs/11-observability.md` is only a short stub, so the best truth source here is still the actual configs and code.

## Trace-through

**Real current-state example: a request enters the gateway and becomes observable at the edge**

1. A client calls the gateway and may or may not send `X-Correlation-ID`.
2. `CorrelationIdFilter` checks the header. If missing, it creates a UUID, stores it in the exchange, forwards it downstream, and adds it to the response.
3. `LoggingFilter` uses MDC values like `correlationId`, `method`, `path`, `statusCode`, and `duration` to write request log lines.
4. Because the gateway enables `http.server.requests` histogram and Actuator endpoints, Micrometer exposes request metrics that Prometheus can scrape from `/actuator/prometheus`.
5. In local Compose, Prometheus can scrape the gateway target defined in `infra/compose/prometheus.yml`.
6. Grafana could visualize those metrics, but the checked-in dashboard is currently empty, so the operator still has to build most panels.
7. The gateway also has an OTLP endpoint property aimed at `otel-collector:4317`, but the collector currently logs received telemetry instead of sending it to a true trace backend.
8. A Kubernetes deployment of the same gateway would also benefit from readiness/liveness probe paths and Prometheus scrape annotations shown in `infra/kubernetes/base/api-gateway.yaml`.
9. Result: **edge correlation and basic metrics are real**, but the full “metric → trace UI → correlated structured logs” workflow described in Prompt 10 is not yet complete.
