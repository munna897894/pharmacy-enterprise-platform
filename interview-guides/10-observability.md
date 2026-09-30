# 10 — Observability (Prompt 10)

## Summary

This area is implemented for the canonical local Docker Desktop Kubernetes fleet, with observability in a separate `pharmacy-observability` namespace. Local Kubernetes run evidence is recorded in `docs/known-gaps.md`; the earlier Compose measurements below are **historical Compose lab evidence**, not a live AWS result. Services export Micrometer metrics; W3C trace context crosses HTTP and Kafka outbox hops into Tempo, Alloy ships logs to Loki, and Grafana provisions the **Pharmacy Platform Observability** dashboard. Rules cover availability, 5xx, p95 latency, Hikari pressure, Kafka lag, DLT, outbox age and heap.

The most interview-worthy takeaways are the non-obvious fixes found only by running the stack: Kafka headers arriving as `byte[]`, the outbox relay breaking traces because the producer span was parented to the scheduler, and actuator endpoints silently returning 401 to Prometheus.

## Diagram — observability flow

```text
Client ──HTTP (+X-Correlation-ID)──► api-gateway :8080 (public)   actuator :9081 (internal)
                                        │ CorrelationIdFilter, W3C traceparent
                                        ▼
                               order-service ──outbox──► Kafka ──► inventory ──outbox──► Kafka ──► payment ──HTTP──► external-mock
                                    │   stored traceparent/baggage + X-Correlation-ID restored per record         │
                                    │   (TraceContextHeaders.runInCapturedContext)                                   ▼
                                    │                                                         Kafka ──► notification, audit
                                    ▼
 metrics:  /actuator/prometheus (allow-listed)  ──► Prometheus ──► Grafana dashboard + alerts.yml
 traces:   OTLP http ──► OpenTelemetry Collector ──► Tempo ──► Grafana (one trace, 7 services)
 logs:     container stdout ──► Alloy (service_name/container labels) ──► Loki ──► Grafana
```

## Key files

| File | What it shows |
|---|---|
| `platform/observability/.../CorrelationIdContext.java` | MDC correlation handling; `fromHeader(Object)` decodes Kafka headers delivered as `String` or `byte[]`. |
| `platform/observability/.../TraceContextHeaders.java` | Captures W3C context + baggage into outbox headers and `runInCapturedContext` restores it when the relay publishes, so the producer span continues the original request trace. |
| `platform/observability/.../PharmacyBusinessMetrics.java`, `RecordBusinessMetric.java`, `OutboxMetricsRecorder.java` | Low-cardinality business outcome counters and outbox backlog/age gauges. |
| `services/{order,inventory,payment}-service/...` outbox publishers | `readOutboxHeaders()` + `runInCapturedContext(...)` around `kafkaTemplate.send`; scheduler observations disabled so the relay does not create an unrelated parent span. |
| `services/api-gateway/.../api/PublicHealthController.java` | Detail-free public `/actuator/health` on 8080; the real actuator (including Prometheus) lives on management port 9081. |
| Service `SecurityConfig` classes | Actuator allow-list: health, liveness, readiness, info, prometheus are public; any other `/actuator/**` is denied. |
| `infra/compose/prometheus.yml`, `infra/prometheus/alerts.yml` | Scrape config for all services (gateway via `api-gateway:9081`) and alert rules. |
| `infra/compose/alloy.alloy` | Docker log discovery with relabeling to `service_name` and `container`. |
| `infra/compose/otel-collector.yaml`, `tempo.yaml`, `loki.yaml` | Trace and log backends. |
| `infra/helm/observability/values-local.yaml`, `docs/11-observability.md` | Canonical local Kubernetes observability release and private access. |
| `infra/grafana/dashboards/overview.json` | RED, JVM, Hikari, Kafka lag, business-outcome and outbox panels. |
| `scripts/resilience-lab.sh` | Reproducible slow-span and Hikari contention drills. |

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
| Structured JSON logging | Instead of free-form text, logs are emitted as JSON with stable fields like `service`, `environment`, `traceId`, `spanId`, `correlationId`. | Structured logs enable reliable search, dashboards and alerting in Splunk/ELK/Loki. Services emit Logstash-format JSON outside the local profile. |

## Common interview Q&A

**Q: Why is enabling `/actuator/prometheus` not the same as “we have observability”?**  
A: Because exposure is only one layer. You still need useful dashboards, alert rules, low-cardinality labels, meaningful business metrics, trace propagation, and searchable structured logs. This repo backs the endpoint with dashboards, alerts, Tempo traces and Loki logs, all verified at runtime.

**Q: What is the practical difference between correlation ID and trace ID?**  
A: Correlation ID is a platform-defined request key used in logs, error responses, and sometimes business support tooling. Trace ID comes from the tracing system and ties spans together automatically. In a mature system, logs contain both so you can jump from a log search to a trace view.

**Q: Why are high-cardinality tags dangerous in Prometheus?**  
A: Every unique label set becomes a new time series. If you tag with `orderId`, millions of orders can become millions of series, which increases RAM, storage, scrape cost, and query latency. That is why standards docs explicitly ban customer/order/prescription IDs as metric tags.

**Q: What should readiness include that liveness should not?**  
A: Readiness can depend on whether the app can safely serve traffic — e.g., Flyway complete, DB reachable, Kafka consumer initialized. Liveness should stay narrower and mostly answer whether the process itself is healthy enough not to be restarted pointlessly.

**Q: Why are percentile histograms more useful than averages for APIs?**  
A: Averages flatten out tail latency. If 95% of requests are fast and 5% are very slow, the average may look okay while real users still suffer. Histograms enable p95/p99 views and SLO-style alerting.

**Q: How should tracing work across Kafka?**  
A: The producer should inject trace context into record headers; the consumer should extract it and create child spans so the asynchronous hop still appears in one distributed trace. This repo additionally stores the context in the outbox row so the relay can continue the original trace.

**Q: What is a good first dashboard for this platform?**  
A: Start with gateway and core business services using RED metrics: request rate, 4xx/5xx rate, p50/p95/p99 latency, then add JVM, Hikari, Kafka lag, outbox age, DLT volume, and a few low-cardinality business counters.

**Q: Why do structured logs matter if we already have traces?**  
A: Traces show timing and causal path, but logs capture payload-independent detail: validation failures, retries, external response codes, and branch decisions. Structured logs make that detail searchable without fragile text parsing.

**Q: What is the difference between a symptom alert and a cause metric?**  
A: A symptom alert tells you user-facing pain exists, like 5xx rate or p95 latency. A cause metric helps narrow the reason, like Hikari pending connections, JVM heap pressure, or Kafka lag. Good observability uses both.

**Q: Why is “business metric” instrumentation different from auto-instrumentation?**  
A: Auto-instrumentation gives you generic HTTP/JVM/Kafka timings, but business metrics tell you outcomes that matter to the domain: orders confirmed, reservations released, payment failures, DLT counts, outbox backlog. This repo records them through `@RecordBusinessMetric` and `OutboxMetricsRecorder`.

## Gotchas / real findings

- **Actuator 401s hide from dashboards.** Prometheus showed targets DOWN until each service explicitly permitted `/actuator/prometheus`. The fix is an allow-list plus `denyAll()` for the rest, not a blanket `permitAll`.
- **Multi-segment `additional-path` is rejected.** `management.endpoint.health.group.*.additional-path` accepts only one segment, so the gateway uses a tiny `PublicHealthController` for its public health check.
- **Kafka headers are bytes.** Spring Cloud Stream delivers custom headers as `byte[]`; `getHeaders().get(name, String.class)` throws. Use `CorrelationIdContext.fromHeader`.
- **Outbox relays break traces by default.** `KafkaTemplate` observation parents the producer span to whatever is current, which is the `@Scheduled` task. Restore the stored context per record and disable `tasks.scheduled.execution` observations.
- **DLT partition mismatch.** Source topics have 3 partitions, DLTs have 1; bindings need `dlqPartitions: 1` or DLQ publishing fails.
- **Loki needs labels.** Without relabeling, every container shares one stream; Alloy now maps the Compose service to `service_name`.
- **Structured JSON logs** are enabled outside the local profile (`logging.structured.format.console=logstash`); the local console pattern still includes `[correlationId,traceId,spanId]`.
- **Log hygiene was checked.** Container logs were scanned for bearer tokens, JWTs, passwords, emails and card data with no hits. Prescription-service's default Spring Security DEBUG logging was lowered to INFO.

## Trace-through

**Historical Compose lab: `./scripts/resilience-lab.sh slow-payment 1500` (not AWS evidence)**

1. The script sets the mock payment gateway to `DELAY 1500ms`, then posts an order through the gateway with its own `X-Correlation-ID`.
2. order-service stores `OrderCreated` plus `traceparent`, baggage and correlation headers in its outbox; the relay restores that context and publishes.
3. inventory reserves stock and publishes `InventoryReserved` the same way; payment consumes it and calls external-mock over HTTP.
4. The order reaches `CONFIRMED`; notification and audit consume the payment events.
5. The script finds the trace ID in Loki by correlation ID, then reads the trace from Tempo: **51 spans across 7 services**, slowest being `external-mock http post /api/v1/mock/process-payment` (~1.5 s) under `payment-service pharmacy.inventory.events.v1 receive`.

**Historical Compose Hikari lab: `./scripts/resilience-lab.sh hikari 45 120`**

- 120 workers for 45 s against order-service (~740 req/s, all 200) pushed `hikaricp_connections_pending` to 85 with 9–10 of 10 connections active and max acquire time ~360 ms. `HikariConnectionsPending` fires if this persists for 2 minutes.
