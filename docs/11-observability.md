# Observability

The platform uses vendor-neutral, open-source instrumentation and backends:
Micrometer and Spring Boot Actuator for metrics, W3C/OpenTelemetry tracing,
Prometheus and Grafana for metrics, Tempo for traces, and Loki/Alloy for logs.
Dynatrace and Splunk are intentionally documented separately in Prompt 14.

## Local Compose stack

Copy `.env.example` to `.env`, provide the required local application settings,
and set a unique `GRAFANA_ADMIN_PASSWORD`. The sample sets trace sampling to
`1.0` so local order traces are consistently available; lower it in shared or
production environments as appropriate. From the repository root, start the
application and observability stack together:

```bash
docker compose \
  -f infra/compose/compose.yml \
  -f infra/compose/compose-observability.yml \
  up -d
```

The overlay provisions Prometheus scrape targets, alert rules, Grafana
datasources and the Pharmacy dashboard. Alloy collects Docker container logs;
the Collector receives OTLP traces and exports them to Tempo.

| UI | Local URL |
|---|---|
| Grafana | `http://localhost:3000` |
| Prometheus | `http://localhost:9090` |

Sign in to Grafana with `GRAFANA_ADMIN_USER` (default `admin`) and
`GRAFANA_ADMIN_PASSWORD` from `.env`. Select the **Pharmacy Platform
Observability** dashboard. Prometheus is also available as a direct UI for
checking scrape targets and alerts. Loki and Tempo are provisioned as Grafana
datasources and are not published on host ports.

## Following an order across services

1. Send an order request through the API gateway. Supply a valid
   `X-Correlation-ID` or let the gateway create one; the value is returned in
   the response.
2. In Grafana Explore, select Loki and search for that ID, for example:
   `{environment="local"} |= "<id>"`. Local console logs include correlation,
   trace, and span IDs in their text; non-local JSON logs expose those values
   as fields.
3. Copy the `traceId` from a log line and search for it using the Tempo
   datasource. The same W3C trace context and correlation ID are stored with
   order, inventory, and payment outbox events and restored to Kafka headers
   when those events are published.
4. Use the overview dashboard's RED, JVM, Hikari, Kafka and business-outcome
   panels to identify the affected service, then inspect its logs and trace
   spans for the cause.

Outbox relays restore the stored context around each Kafka send
(`TraceContextHeaders.runInCapturedContext`), and scheduled-task observations
are disabled in the relaying services, so one order produces one Tempo trace
spanning the gateway, order, inventory, payment, external-mock, notification
and audit services. Loki streams carry `service_name` and `container` labels,
so `{service_name="order-service"} |= "<id>"` narrows a search to one service.

## Actuator exposure

Services expose `/actuator/health`, `/health/liveness`, `/health/readiness`,
`/info` and `/prometheus` without authentication and deny every other
`/actuator/**` path. The API gateway serves only a detail-free
`/actuator/health` on its public port 8080; its full actuator, including
Prometheus, runs on internal management port 9081, which Prometheus, probes
and Kubernetes scrape annotations target.

## Drills

`scripts/resilience-lab.sh` reuses a Newman-exported environment (it runs the
Postman suite first if needed):

- `./scripts/resilience-lab.sh slow-payment 1500` delays the mock payment
  gateway, places an order, looks up its trace ID in Loki by correlation ID and
  prints the slowest Tempo spans. Expect `external-mock` `process-payment` at
  roughly the configured delay.
- `./scripts/resilience-lab.sh hikari 45 120` sustains concurrent reads on
  order-service (bypassing gateway rate limiting) and prints Hikari pending,
  active and acquire-time maxima. A local run reached 85 pending requests with
  a 10-connection pool.

Correlation IDs are for log search; trace IDs identify a distributed trace.
They serve different purposes and should be retained together. Metric labels
remain low-cardinality and never contain order, customer, prescription, or
payment identifiers.

The local Kafka exporter supplies consumer lag and recent DLT-topic activity.
The DLT alert detects offset increases over a recent window rather than
re-alerting forever on historical records. Container restart alerts are
available only when a Kubernetes metrics source is present; ordinary Compose
does not expose Kubernetes pod restart metrics.

## AWS/EKS configuration (prepared, not installed)

`infra/helm/observability` is an optional, separate EKS stack. It does not
create a public LoadBalancer or Ingress and has not been installed. Review the
chart's README, rendered resources, storage requests, and AWS costs before
using it. Grafana, Prometheus, Loki, Tempo, and the Collector should remain
private; access their UIs with `kubectl port-forward`.

The chart's Prometheus configuration discovers Kubernetes services annotated
with `prometheus.io/scrape: "true"`. Application deployments must expose their
internal Actuator endpoint and set the correct metrics port. Set each
application's `OTEL_EXPORTER_OTLP_ENDPOINT` to the in-cluster Collector
`http://pharmacy-observability-opentelemetry-collector:4318/v1/traces`.
Port-forward Grafana using the service name rendered by Helm; the README
contains example commands. Kafka lag and DLT metrics require a Kafka exporter
that can reach the broker. The platform's broker is currently local, so the
EKS stack cannot monitor its Kafka topics unless private, network-reachable
broker connectivity is separately configured. Do not expose Kafka publicly.

## Stopping local services

From the repository root:

```bash
docker compose \
  -f infra/compose/compose.yml \
  -f infra/compose/compose-observability.yml \
  down
```

Persistent Prometheus, Grafana, Loki and Tempo data volumes are retained by
default. Remove them only when you intentionally want to discard the local
observability history.
