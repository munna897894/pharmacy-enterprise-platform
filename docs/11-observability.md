# Observability

The platform uses vendor-neutral, open-source instrumentation and backends:
Micrometer and Spring Boot Actuator for metrics, W3C/OpenTelemetry tracing,
Prometheus and Grafana for metrics, Tempo for traces, and Loki/Alloy for logs.
The baseline remains fully functional without commercial vendors. Optional,
disabled-by-default Dynatrace and Splunk hooks, activation safeguards and
cross-signal triage are documented in
[`docs/12-commercial-observability.md`](12-commercial-observability.md).

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

## Local Docker Desktop Kubernetes

The Kubernetes stack is separate from Compose and is installed only when
needed. It runs in `pharmacy-observability` with short retention, one replica,
and ClusterIP-only services. Alloy tails pod logs from namespace `pharmacy`;
the Collector forwards OTLP traces to Tempo. The local values enable Kafka
Exporter against the host broker at `host.docker.internal:29092`.
The profile is compact (about 0.8 GiB requested, 2.6 GiB limits): it disables
Alertmanager, node-exporter, control-plane scrapers and Loki canary pods, and
keeps rules visible in Prometheus. Scraping is limited to annotated Services
in `pharmacy` and `pharmacy-observability`. Loki streams carry `cluster`,
`namespace`, `app`, `pod` and `container` labels.

Run `infra/helm/observability/scripts/ensure-grafana-secret.sh` to create
the `grafana-admin` Secret non-interactively. It generates a random password
and keeps an existing Secret unchanged, so it is safe to rerun. The Secret is
applied from a stdin manifest, so the password never appears in process
arguments. The same
`existingSecret` reference is used locally and on EKS, and no values file
contains a password. Then follow `infra/helm/observability/README.md` to build
dependencies, render/lint, and install the local profile. Forward Grafana
with `kubectl -n pharmacy-observability port-forward
svc/pharmacy-observability-grafana 3001:80` and open
`http://localhost:3001`. Prometheus is available at `http://localhost:9090`
using `svc/pharmacy-observability-kub-prometheus 9090:9090`. Loki and Tempo are Grafana
datasources; optional API port-forwards are documented in the chart README.

The application workloads are in namespace `pharmacy`. Their Kubernetes
Services must carry `prometheus.io/scrape: "true"`,
`prometheus.io/path: /actuator/prometheus`, and
`prometheus.io/port: "<management-service-port>"`. The `pharmacy-platform`
Helm chart already emits these annotations. For traces, set
`OTEL_EXPORTER_OTLP_ENDPOINT` on each application to
`http://pharmacy-observability-opentelemetry-collector.pharmacy-observability.svc.cluster.local:4318/v1/traces`;
set `TRACING_SAMPLING_PROBABILITY` above zero (use `1.0` for local drills).
These application deployment settings are requirements for the parent
deployment work and are not changed by the observability-only chart.

## AWS/EKS configuration (prepared, not installed)

`infra/helm/observability` is an optional, separate EKS stack. It does not
create a public LoadBalancer or Ingress. The EKS profile uses one replica and
bounded Prometheus (3 days/4 GB), Loki (7 days), and Tempo (3 days) retention.
It uses bounded pod-local ephemeral storage rather than EBS PVCs because the
temporary cluster does not install an EBS CSI driver; history is lost when a
pod is replaced or the cluster is destroyed. Review its rendered resources
and AWS cost impact before installing. Grafana, Prometheus, Loki, Tempo, and
the Collector remain ClusterIP-only; access UIs with `kubectl port-forward`.
Before an approved install, run `ensure-grafana-secret.sh` against the EKS
context; the chart only references `existingSecret: grafana-admin`.

The chart's Prometheus configuration discovers Kubernetes services annotated
with `prometheus.io/scrape: "true"` and the correct management port. Set each
application's `OTEL_EXPORTER_OTLP_ENDPOINT` to
`http://pharmacy-observability-opentelemetry-collector.pharmacy-observability.svc.cluster.local:4318/v1/traces`
and enable a nonzero trace sampling probability. Alloy ships `pharmacy`
namespace logs to Loki. Port-forward Grafana to `http://localhost:3001` and
Prometheus to `http://localhost:9090`; commands and optional Loki/Tempo API
forwards are in the chart README.

The EKS Kafka broker is `kafka.pharmacy.svc.cluster.local:9092`. The current
`kafka-private` NetworkPolicy allows ingress only from namespace `pharmacy`,
so the EKS profile deploys its Kafka Exporter in that namespace and exposes
its metrics only through a ClusterIP Service. It reports consumer lag and
DLT-topic offset increases without opening Kafka publicly. The EKS profile
is prepared only; no AWS resources were installed as part of this work.

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
