# Prompt 14 — Dynatrace and Splunk integrations

## Give Copilot these files

- `docs/02-architecture.md`
- `docs/08-cross-cutting-standards.md`
- `docs/11-observability.md`
- `learning/incident-labs.md`
- `prompts/10-observability.md`

## Prompt 14A — Dynatrace learning integration

```text
Add an optional Dynatrace integration for learning and trial use. Keep the normal local and AWS application deployments runnable without Dynatrace, OneAgent, tenant URLs, or credentials.

Document and provide safe configuration hooks for:
- Dynatrace Operator/OneAgent deployment to the project's AWS EKS environment, including required permissions, secret placeholders and a clearly documented installation/removal procedure;
- monitoring local Docker Compose services when supported by the chosen Dynatrace setup, without exposing the Dynatrace tenant or local UI publicly;
- alternatively exporting OpenTelemetry data to Dynatrace where that is a better fit;
- inspecting Kubernetes workloads, JVM health, service flow, database dependencies, errors and distributed traces;
- usage/licensing considerations and a minimal-scope trial rollout.

Do not commit tenant URLs, API tokens, OneAgent installers, or secrets. Do not enable overlapping Java auto-instrumentation by default if OpenTelemetry instrumentation is already active. Explain how to detect and avoid duplicate spans.
```

## Prompt 14B — Splunk learning integration

```text
Add an optional Splunk integration for structured application logs and observability learning. Keep Splunk optional: do not make a Splunk account, local installation, or license necessary to build or run the platform.

Document and provide safe configuration hooks for a local/temporary Splunk Enterprise environment or Splunk Cloud:
- supported log ingestion path for the platform's structured JSON logs, including an OpenTelemetry Collector/exporter option where appropriate;
- index and sourcetype recommendations, retention considerations, and required secret/configuration placeholders;
- searching by service, environment, correlationId, traceId and spanId;
- example SPL searches, dashboards and alerts that use the actual emitted JSON field names;
- secure setup/teardown guidance and cost/licensing considerations.

Do not commit HEC tokens, tenant URLs, credentials, or sample sensitive pharmacy/customer data. Do not expose Splunk endpoints or UI publicly without authentication and network controls.
```

## Prompt 14C — Cross-signal incident triage

```text
Document an end-to-end triage workflow that works with the project's open-source baseline and describes corresponding Dynatrace/Splunk views when those integrations are enabled:

alert -> metric -> trace -> correlated log -> root cause -> mitigation -> validation.

Use a real order workflow as the example. Show how to carry correlationId and traceId between Grafana/Prometheus, Dynatrace and Splunk, while clearly identifying which links require optional integrations. Include verification steps for HTTP and Kafka/asynchronous propagation and note any current platform limitations.
```

## Verification

- The platform builds and runs when Dynatrace and Splunk are absent.
- Optional integrations are disabled unless explicitly configured.
- No secrets, tenant-specific URLs, or private customer/prescription/payment data are committed or logged.
- Documented setup can be followed for local learning and AWS/EKS without making observability endpoints publicly accessible.
- A sample order triage can be followed across metrics, trace context and structured logs; asynchronous gaps are called out rather than assumed away.

## Study

- OneAgent versus OpenTelemetry instrumentation
- Vendor-neutral telemetry and backend-specific integrations
- Splunk indexes, sourcetypes and SPL
- Telemetry volume, retention, licensing and cost controls
- Secure observability access in AWS/EKS
