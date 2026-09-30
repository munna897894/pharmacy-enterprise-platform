# Kubernetes observability

The observability data paths are shown in the
[local](../../../docs/architecture-local.md) and
[AWS](../../../docs/architecture-cloud.md) deployment diagrams.

This optional Helm chart deploys an independent, private observability stack
for Docker Desktop Kubernetes or temporary AWS EKS. It includes Grafana,
Prometheus, Loki, Tempo, an OpenTelemetry Collector, Grafana Alloy and Kafka
Exporter. Docker Desktop's exporter targets the host broker. The EKS exporter
targets the private in-cluster broker and runs in namespace `pharmacy` to
respect its existing Kafka ingress NetworkPolicy. Optional commercial
overlays are documented in `docs/12-commercial-observability.md`; neither is
enabled by this chart's normal values.

Both profiles use single-replica stateful backends, bounded storage and short
data retention.
Docker Desktop uses PVCs; EKS uses bounded pod-local ephemeral storage so it
does not depend on a storage-class or CSI add-on that the temporary cluster
does not install. EKS history is lost when a pod is replaced. All UIs and
ingestion services are ClusterIP-only; no public LoadBalancer or Ingress is
created. Keep the release name `pharmacy-observability` because
datasource and collector addresses use that name.

## Optional commercial overlays

`values-dynatrace.example.yaml` fans the existing Collector's traces to a
Dynatrace OTLP/HTTP endpoint while retaining Tempo. It references the existing
Secret `dynatrace-otlp` (`endpoint`, `api-token` keys).

`values-splunk.example.yaml` fans application pod logs from Alloy to Splunk HEC
while retaining Loki. It references the existing Secret `splunk-hec`
(`endpoint`, `hec-token` keys). TLS verification remains enabled.

These files are never loaded by the commands in this README. Render one
explicitly only during an approved test:

```bash
helm template pharmacy-observability infra/helm/observability \
  --namespace pharmacy-observability \
  -f infra/helm/observability/values-eks.yaml \
  -f infra/helm/observability/values-dynatrace.example.yaml
```

Replace the final file with `values-splunk.example.yaml` for Splunk. Create
Secrets only at activation time, never commit them, and return to the baseline
with a Helm upgrade that omits the commercial overlay. Full token scopes,
private Secret creation, verification, cost controls and removal sequences are
in `docs/12-commercial-observability.md`.

## Application requirements

Install the stack in `pharmacy-observability` and deploy applications in
`pharmacy`. For every application Service that exposes Actuator metrics, add:

```yaml
prometheus.io/scrape: "true"
prometheus.io/path: /actuator/prometheus
prometheus.io/port: "<management-service-port>"
```

Use the port that actually serves `/actuator/prometheus` (for example,
`9081` for the API gateway); do not use the public gateway port. Prometheus
discovers annotated Kubernetes Services and rewrites the endpoint to the
annotated port. The `pharmacy-platform` Helm chart already emits these
annotations; deployments rendered from standalone Kubernetes manifests need
the same Service annotations.

Set each service's `OTEL_EXPORTER_OTLP_ENDPOINT` to:

```text
http://pharmacy-observability-opentelemetry-collector.pharmacy-observability.svc.cluster.local:4318/v1/traces
```

The `TRACING_SAMPLING_PROBABILITY` must be greater than zero for traces to be
sent (use `1.0` for local drills and a suitably lower value for shared test
clusters). Alloy tails pod logs from the `pharmacy` namespace and sends them
to Loki with `cluster`, `namespace`, `pod`, and `container` labels. Use the
correlation ID to find logs, then search Tempo by trace ID.

Kafka lag and recent DLT-topic activity are exported only when the exporter
can connect to the broker. The local profile uses
`host.docker.internal:29092`. EKS uses
`kafka.pharmacy.svc.cluster.local:9092`; its exporter is placed in namespace
`pharmacy` because the existing `kafka-private` NetworkPolicy allows broker
ingress only from that namespace. Neither profile opens Kafka publicly.

## Prepare dependencies and credentials

Run once after cloning or changing chart dependency versions:

```bash
helm dependency build infra/helm/observability
```

Create the private Grafana credential Secret before installing. Values files
only reference `existingSecret: grafana-admin`; they never contain credentials.
The helper is non-interactive and idempotent: it creates the namespace if
needed, generates a random 32-character password, never prints it, and leaves
an existing Secret unchanged on later runs.

```bash
infra/helm/observability/scripts/ensure-grafana-secret.sh
```

Optional environment variables:

| Variable | Default | Purpose |
|---|---|---|
| `KUBE_CONTEXT` | current context | Target context, for example `docker-desktop` |
| `OBSERVABILITY_NAMESPACE` | `pharmacy-observability` | Release namespace |
| `GRAFANA_ADMIN_USER` | `admin` | Admin login name |
| `GRAFANA_ADMIN_PASSWORD` | generated | Supply a password from a secret manager (16+ characters) |
| `GRAFANA_ADMIN_ROTATE` | `false` | `true` replaces the password and restarts Grafana |

The Secret manifest is streamed to `kubectl apply` on stdin, so the password
never appears in process arguments. Grafana stores the admin password in its
database on first start; with `GRAFANA_ADMIN_ROTATE=true` the helper restarts
Grafana and resets the stored password from the pod's Secret-backed
environment through `grafana cli ... --password-from-stdin`.

## Docker Desktop Kubernetes

Confirm the active context and local storage class before rendering:

```bash
kubectl config current-context
kubectl get nodes
kubectl get namespace pharmacy
kubectl get storageclass
```

The local profile uses Docker Desktop's `hostpath` StorageClass and connects
Kafka Exporter to the host Kafka advertised at `host.docker.internal:29092`.
If the cluster uses a different default provisioner, override the
`hostpath` storage class in the local values before installation.

The local profile is sized for a shared Docker Desktop VM: Alertmanager,
node-exporter, control-plane scrapers and the Loki canary/test pods are
disabled. Total requests are about 0.8 GiB and total limits about 2.6 GiB.
Alerts are still evaluated and visible under Prometheus **Alerts**.

```bash
KUBE_CONTEXT=docker-desktop infra/helm/observability/scripts/ensure-grafana-secret.sh
helm lint infra/helm/observability -f infra/helm/observability/values-local.yaml
helm template pharmacy-observability infra/helm/observability \
  --namespace pharmacy-observability \
  -f infra/helm/observability/values-local.yaml
helm upgrade --install pharmacy-observability infra/helm/observability \
  --namespace pharmacy-observability \
  --create-namespace \
  -f infra/helm/observability/values-local.yaml
```

## Temporary AWS EKS

First verify the intended AWS identity, region, and cluster context. EKS
worker nodes and data transfer are billable; inspect rendered resource
requests/limits and costs before installing. This temporary profile uses
bounded node-local ephemeral storage rather than EBS, so it does not require
an EBS CSI driver or a `gp3` StorageClass. Prometheus, Loki, and Tempo history
is lost if their pods are replaced or the cluster is destroyed.

```bash
aws sts get-caller-identity
kubectl config current-context
kubectl get nodes
kubectl get namespace pharmacy
```

Render and review without installing:

```bash
helm lint infra/helm/observability -f infra/helm/observability/values-eks.yaml
helm template pharmacy-observability infra/helm/observability \
  --namespace pharmacy-observability \
  -f infra/helm/observability/values-eks.yaml
```

When installation is explicitly approved, use the same release namespace and
create the Grafana Secret first:

```bash
KUBE_CONTEXT="$(kubectl config current-context)" \
  infra/helm/observability/scripts/ensure-grafana-secret.sh
helm upgrade --install pharmacy-observability infra/helm/observability \
  --namespace pharmacy-observability \
  --create-namespace \
  -f infra/helm/observability/values-eks.yaml
```

The EKS profile targets `kafka.pharmacy.svc.cluster.local:9092` and places the
exporter Deployment/Service in namespace `pharmacy`, which is allowed by the
current `kafka-private` NetworkPolicy. The application namespace must exist
before installing the stack. This does not open any broker port outside the
cluster. Never expose Kafka publicly for monitoring.

## Private access

Use the same port-forwards for either Kubernetes profile. Run each command
in a separate terminal and keep the session open:

```bash
kubectl -n pharmacy-observability port-forward \
  svc/pharmacy-observability-grafana 3001:80
```

Open Grafana at `http://localhost:3001`. Sign in with `admin` and the password
from the `grafana-admin` Secret:

```bash
kubectl -n pharmacy-observability get secret grafana-admin \
  -o jsonpath='{.data.admin-password}' | base64 --decode; echo
```

All services are ClusterIP only; there is no Ingress, LoadBalancer or NodePort.

```bash
kubectl -n pharmacy-observability port-forward \
  svc/pharmacy-observability-kub-prometheus 9090:9090
```

Open Prometheus at `http://localhost:9090` to inspect targets and alerts.
Optional direct backend/API access:

```bash
kubectl -n pharmacy-observability port-forward svc/pharmacy-observability-loki 3100:3100
kubectl -n pharmacy-observability port-forward svc/pharmacy-observability-tempo 3200:3100
```

Loki's API is then at `http://localhost:3100`; Tempo's HTTP API is at
`http://localhost:3200`. Neither is a standalone UI; use Grafana Explore for
logs and traces. The Collector's OTLP endpoint is cluster-internal and Alloy
has no published Service.

## Removal

Confirm the current context and inspect PVCs before cleanup. Helm uninstall
does not necessarily delete retained data volumes:

```bash
helm uninstall pharmacy-observability -n pharmacy-observability
kubectl -n pharmacy-observability get pvc
```

Docker Desktop PVCs may remain after Helm uninstall; inspect them and delete
only after confirming the data is no longer needed. EKS uses pod-local
ephemeral data and creates no chart PVCs.
