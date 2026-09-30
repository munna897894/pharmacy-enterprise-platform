# Optional Dynatrace and Splunk integrations

Prompt 14 prepares commercial observability integrations without activating
them. The supported baseline remains Prometheus, Grafana, Loki, Tempo, Alloy
and the OpenTelemetry Collector described in
[`docs/11-observability.md`](11-observability.md).

Nothing in this guide is required to build, run or deploy the platform. Normal
Compose and Helm commands do not start Splunk, install Dynatrace Operator or
send telemetry to either vendor.

## Current status

| Item | Repository state | Live state |
|---|---|---|
| Dynatrace OTLP | Collector overlays prepared | No tenant, token or export enabled |
| Dynatrace Operator/OneAgent | Operator `1.10.2` values and DynaKube example prepared | Not installed; no namespace injection |
| Splunk Enterprise | Optional `splunk` Compose profile pinned to `10.4.3` | Not started; no license/trial activated |
| Splunk HEC | Alloy exporter overlays prepared | No HEC endpoint or token configured |
| AWS/EKS | Private, existing-Secret Helm overlays prepared | No AWS or vendor resource created |

Live validation is intentionally deferred until a user explicitly approves a
trial. Do not interpret offline rendering as proof that a vendor tenant,
ingestion entitlement, dashboard or alert works.

## Design decisions

### Dynatrace is OTLP-first

The applications already emit OpenTelemetry-compatible traces and propagate
W3C trace context over HTTP and Kafka. The preferred Dynatrace trial path fans
those traces out from the existing Collector:

```text
services -> OpenTelemetry Collector -> Tempo
                                  `-> Dynatrace OTLP/HTTP (optional)
```

This preserves the open-source baseline and avoids adding a second Java
instrumentation agent. The optional Collector configuration exports **traces
only** to control trial volume. Actuator metrics remain in Prometheus and logs
remain in Loki unless a later test explicitly expands the Dynatrace pipeline.

Dynatrace SaaS accepts the base endpoint:

```text
https://<environment-id>.live.dynatrace.com/api/v2/otlp
```

The Collector appends `/v1/traces`. Do not use a browser hostname containing
`.apps`. A classic token needs only `openTelemetryTrace.ingest` for this
prepared traces-only path. Add `metrics.ingest` or `logs.ingest` only if those
signals are deliberately enabled later.

### OneAgent is an alternative, not an additional default

`infra/helm/dynatrace-operator/` contains a pinned Operator values file and an
application-monitoring DynaKube example. It is not part of either application
Helm chart and no namespace carries its injection label.

Do not enable OneAgent Java code-module injection while the current Java
instrumentation is exporting the same spans. Before an Operator trial choose
one mode:

1. **OTLP mode (recommended):** keep current Micrometer/OpenTelemetry tracing;
   do not label `pharmacy` for OneAgent injection.
2. **OneAgent mode:** disable the overlapping application trace exporter or
   code-module instrumentation for the trial, then enable injection on the
   selected namespace/workloads.

Duplicate instrumentation usually appears as repeated server/client spans with
the same operation, doubled service-flow edges, two runtime instrumentation
libraries on spans, or nearly identical parent/child spans. Compare one
controlled order trace in Tempo and Dynatrace before increasing sampling.

### Splunk receives existing structured JSON logs

Outside the `local` Spring profile, every service emits structured console
JSON. The gateway's custom encoder renames fields to `timestamp`, `severity`,
`thread` and `logger`. Services using Spring Boot's Logstash format emit
`@timestamp`, `level`, `thread_name` and `logger_name`. Both include `message`,
`service`, `environment` and the MDC fields `correlationId`, `traceId` and
`spanId`. SPL examples below normalize the two real shapes with `coalesce`;
they do not assume a field name that the application does not emit.

The optional Alloy path tails only application containers/pods, keeps Loki as
the baseline destination and additionally sends the same records to Splunk HEC:

```text
container/pod stdout -> Alloy -> Loki
                             `-> Splunk HEC (optional)
```

Recommended non-production metadata:

| Setting | Value |
|---|---|
| Index | `pharmacy_nonprod` |
| Sourcetype | `pharmacy:service:json` |
| Source | `pharmacy-platform` |
| Retention | Shortest period sufficient for the lab (for example 1–3 days) |

Do not ingest customer profiles, addresses, prescription text, payment details,
tokens or secrets. Those fields are prohibited by the platform logging
standard regardless of backend.

## Prepared artifacts

| File | Purpose |
|---|---|
| `infra/compose/compose-dynatrace-observability.yml` | Explicit Dynatrace Collector override |
| `infra/compose/compose-splunk-observability.yml` | Optional Splunk/HEC profile |
| `infra/compose/otel-collector-dynatrace.yaml` | Tempo + Dynatrace trace fan-out |
| `infra/compose/otel-collector-splunk-local.alloy` | Local Docker logs to Splunk HEC (generated-certificate TLS exception) |
| `infra/helm/observability/values-dynatrace.example.yaml` | EKS Collector trace fan-out using an existing Secret |
| `infra/helm/observability/values-splunk.example.yaml` | EKS Alloy Loki + HEC fan-out using an existing Secret |
| `infra/helm/dynatrace-operator/values.example.yaml` | Dynatrace Operator `1.10.2` settings |
| `infra/helm/dynatrace-operator/dynakube.example.yaml` | `dynatrace.com/v1beta5` application-monitoring example |
| `scripts/commercial-observability-validate.sh` | Offline/non-vendor validation |

## Safe preparation now (no activation)

Run:

```bash
scripts/commercial-observability-validate.sh
```

It uses placeholder values and local temporary files, renders Compose and Helm,
validates Collector/Alloy syntax, checks that baseline output contains no
commercial integration, and checks that optional Kubernetes output creates no
public Service or Ingress. It never calls a Dynatrace or Splunk endpoint.

The pinned container images and Helm dependencies may need to be downloaded
from their public registries on the first run. This is not vendor account
activation and sends no application telemetry.

## Later activation: Dynatrace OTLP

Perform these steps only during an approved trial window.

### Local Compose

1. Create a trace-ingest token in the Dynatrace tenant with minimum scope.
2. Store it in the gitignored path; never place it in `.env`:

   ```bash
   install -d -m 700 .local/secrets
   umask 077
   printf '%s' "$DYNATRACE_API_TOKEN" > .local/secrets/dynatrace-api-token
   chmod 0444 .local/secrets/dynatrace-api-token
   unset DYNATRACE_API_TOKEN
   ```

3. Put the real base endpoint in the gitignored `.env`.
4. Explicitly include the commercial override:

   ```bash
   docker compose \
     -f infra/compose/compose.yml \
     -f infra/compose/compose-observability.yml \
     -f infra/compose/compose-dynatrace-observability.yml \
     --env-file .env up -d otel-collector
   ```

5. Place one synthetic order and verify the trace in both Tempo and Dynatrace.
   Start with low sampling in shared environments and inspect Collector queue,
   retry and rejected-span metrics before increasing it.

Removal:

```bash
docker compose \
  -f infra/compose/compose.yml \
  -f infra/compose/compose-observability.yml \
  --env-file .env up -d --force-recreate otel-collector
rm -f .local/secrets/dynatrace-api-token
```

Restart the normal two-file Compose stack to restore the baseline Collector
configuration.

### EKS Collector overlay

Keep the endpoint and token in a Kubernetes Secret created at activation time.
Prefer loading values from AWS Secrets Manager through the approved secret
delivery mechanism. For a temporary manual trial, stream from local files and
avoid command-line literals:

```bash
kubectl -n pharmacy-observability create secret generic dynatrace-otlp \
  --from-file=api-token=.local/secrets/dynatrace-api-token \
  --from-file=endpoint=.local/secrets/dynatrace-otlp-endpoint \
  --dry-run=client -o yaml | kubectl apply -f -
```

Render before install:

```bash
helm template pharmacy-observability infra/helm/observability \
  --namespace pharmacy-observability \
  -f infra/helm/observability/values-eks.yaml \
  -f infra/helm/observability/values-dynatrace.example.yaml
```

Then use the same two values files with `helm upgrade --install`. The Collector
and all open-source backends remain ClusterIP-only. Remove the overlay with a
baseline Helm upgrade, then delete `secret/dynatrace-otlp`.

## Later activation: Dynatrace Operator/OneAgent

This path provides Kubernetes workload/process visibility, JVM health, service
flow, database dependencies, errors and traces but can ingest substantially
more data than the OTLP-first trial.

1. Review current Dynatrace token requirements and estimate trial ingestion.
2. Install the pinned OCI chart:

   ```bash
   helm template dynatrace-operator \
     oci://public.ecr.aws/dynatrace/dynatrace-operator \
     --version 1.10.2 \
     --namespace dynatrace \
     -f infra/helm/dynatrace-operator/values.example.yaml
   ```

3. Create separate `apiToken` and `dataIngestToken` keys in an existing
   `dynakube` Secret. Do not reuse one over-privileged token.
4. Replace the placeholder `/api` URL in a gitignored copy of
   `dynakube.example.yaml`.
5. Install the Operator, apply the reviewed DynaKube, and only then label the
   selected namespace:

   ```bash
   kubectl label namespace pharmacy \
     observability.pharmacy/dynatrace-injection=enabled
   ```

6. Restart one service first, check for duplicate spans and injection errors,
   and expand only after the canary is correct.

Safe removal order:

1. Remove the namespace injection label and restart injected workloads.
2. Delete DynaKube resources and wait for managed resources to disappear.
3. `helm uninstall dynatrace-operator -n dynatrace`.
4. Delete tokens/namespace after inspection.
5. Remove CRDs only as a separate, explicit destructive action.

## Later activation: local Splunk Enterprise

The `splunk` profile is intentionally absent from normal commands. Splunk 10.x
requires explicit license and general-terms acceptance. Starting the profile is
that affirmative action and may start a time-limited license/trial.

Set unique values only in the gitignored `.env`, and place the same HEC token in
the mounted secret file:

```bash
install -d -m 700 .local/secrets
umask 077
printf '%s' "$SPLUNK_HEC_TOKEN" > .local/secrets/splunk-hec-token
chmod 0444 .local/secrets/splunk-hec-token
printf '%s' "$SPLUNK_HEC_TOKEN" | cmp -s - .local/secrets/splunk-hec-token || {
  echo "HEC token file mismatch" >&2
  exit 1
}
unset SPLUNK_HEC_TOKEN
```

Start only when approved:

```bash
docker compose \
  -f infra/compose/compose.yml \
  -f infra/compose/compose-observability.yml \
  -f infra/compose/compose-splunk-observability.yml \
  --env-file .env --profile splunk up -d
```

The UI (`127.0.0.1:8000`) and HEC (`127.0.0.1:8188`) bind only to loopback.
Port 8089 is not exposed. Confirm HEC health, send one synthetic order, and
verify the expected index before increasing volume. Including the override
recreates application services with the `commercial` profile so their existing
non-local structured JSON logging configuration is active. Return to the normal
two-file Compose command after the test to restore the `local` profile. Splunk's
first initialization can take several minutes; Alloy waits for HEC health and
retains bounded retry state for up to ten minutes.

Stop while retaining data:

```bash
docker compose \
  -f infra/compose/compose.yml \
  -f infra/compose/compose-observability.yml \
  -f infra/compose/compose-splunk-observability.yml \
  --env-file .env --profile splunk stop splunk alloy-splunk

docker compose \
  -f infra/compose/compose.yml \
  -f infra/compose/compose-observability.yml \
  --env-file .env up -d --force-recreate
```

Use `down` to remove containers. Deleting `splunk-data` and `splunk-etc` volumes
is destructive and must be an explicit separate decision.

## Later activation: Splunk Cloud/EKS

Create `secret/splunk-hec` in `pharmacy-observability` with `endpoint` and
`hec-token` keys, then render/install the observability chart with
`values-splunk.example.yaml`. The endpoint must be HTTPS with a valid
certificate; the EKS overlay never sets `insecure_skip_verify`.

No Ingress, LoadBalancer or NodePort is created. Splunk Cloud remains an
outbound HTTPS destination and the local Grafana/Prometheus UIs remain
port-forward-only.

## SPL searches

The examples assume index `pharmacy_nonprod` and JSON field extraction for
`pharmacy:service:json`.

Find one request by correlation ID:

```spl
index=pharmacy_nonprod sourcetype="pharmacy:service:json"
correlationId="<correlation-id>"
| eval event_time=coalesce(timestamp, '@timestamp'),
       log_level=coalesce(severity, level)
| table event_time log_level service traceId spanId message
| sort event_time
```

Pivot from a log to a trace:

```spl
index=pharmacy_nonprod sourcetype="pharmacy:service:json"
traceId="<trace-id>"
| stats earliest(timestamp) as firstSeen latest(timestamp) as lastSeen
        values(service) as services values(message) as messages by traceId
```

Error trend by service:

```spl
index=pharmacy_nonprod sourcetype="pharmacy:service:json"
((severity=ERROR OR severity=FATAL) OR (level=ERROR OR level=FATAL))
| eval log_level=coalesce(severity, level)
| timechart span=5m count by service
```

Async delivery/outbox symptoms:

```spl
index=pharmacy_nonprod sourcetype="pharmacy:service:json"
("outbox" OR "DLT" OR "delivery failed" OR "consumer lag")
| stats count values(message) as examples by service severity
```

Do not build searches or dashboards around customer IDs, order IDs,
prescription IDs or payment IDs. Use `service`, environment, severity and
bounded endpoint/status dimensions for aggregate views; use correlation/trace
IDs only for targeted incident investigation.

Suggested dashboard panels:

- errors by service and severity;
- service log volume and ingestion gaps;
- top sanitized error messages;
- outbox/DLT evidence count;
- correlation-to-trace investigation table.

Suggested alerts:

- no logs from an expected service for 10 minutes while traffic exists;
- ERROR/FATAL count above the lab baseline for 5 minutes;
- any DLT or old-outbox evidence;
- repeated notification delivery failure evidence.

## Cross-signal order triage

Use synthetic data only.

1. **Alert:** start with the Prometheus/Grafana symptom alert. If a vendor
   integration is enabled, compare its alert time window rather than assuming
   the clocks and evaluation windows are identical.
2. **Metric:** identify the failing service using RED, JVM, Hikari, Kafka lag,
   DLT and outbox metrics. Dynatrace OneAgent views are optional equivalents;
   Splunk log counts are evidence but not a replacement for request metrics.
3. **Trace:** place one order with a known `X-Correlation-ID`. Copy the
   `traceId` from Loki/Splunk and open it in Tempo or Dynatrace.
4. **Correlated log:** search the correlation ID across gateway, order,
   inventory, payment, notification and audit. Never search by sensitive
   customer/prescription/payment content.
5. **Root cause:** distinguish the synchronous HTTP leg from Kafka/outbox legs.
   The outbox captures W3C trace context and correlation ID and restores them
   when publishing. Consumers remain idempotent via `eventId`.
6. **Mitigation:** use the relevant incident lab: restore Kafka, release DB
   pressure, reset the external mock, roll back a bad deployment, or replay a
   reviewed DLT record.
7. **Validation:** repeat a synthetic order, verify the terminal order/payment
   state, notification and audit records, confirm the alert clears, and check
   that outbox/DLT/lag return to normal.

Verification points:

- HTTP response echoes `X-Correlation-ID`.
- HTTP client spans share the expected trace.
- Kafka headers contain `traceparent` and `X-Correlation-ID`.
- scheduled relays continue the captured trace instead of creating a new root.
- one order may span several seconds because three outbox relays poll at
  intervals; missing spans can also result from sampling.
- correlation ID is an operational search key; trace ID identifies a trace.
  Preserve both and do not treat them as interchangeable.

## Cost and licensing controls

- Keep integrations disabled between labs.
- Start with traces only and low sampling; do not export all three signals
  until their value and ingestion volume are understood.
- Use one synthetic workflow, a short time window and the smallest namespace
  selector.
- Use short non-production index retention and avoid high-volume DEBUG logs.
- Watch Collector sent/failed/retried counts and vendor-reported ingestion.
- Destroy temporary EKS resources through the existing AWS teardown workflow.
- Stop Splunk and remove vendor Secrets/tokens when the lab ends.
- Review current vendor trial/license terms at activation time; repository
  preparation does not grant or activate a license.

## Official references

- [Dynatrace OTLP API endpoints](https://docs.dynatrace.com/docs/ingest-from/opentelemetry/otlp-api)
- [Dynatrace Operator 1.10.2](https://github.com/Dynatrace/dynatrace-operator/releases/tag/v1.10.2)
- [OpenTelemetry Collector configuration](https://opentelemetry.io/docs/collector/configuration/)
- [Splunk Docker 10.4.3](https://github.com/splunk/docker-splunk/releases/tag/10.4.3)
- [Splunk HEC exporter](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/v0.161.0/exporter/splunkhecexporter)
