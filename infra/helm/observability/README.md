# AWS/EKS open-source observability

This chart is optional and is **not applied automatically**. It installs a separate Grafana/Prometheus, Loki, Tempo, OpenTelemetry Collector, and Grafana Alloy stack into EKS. Dynatrace and Splunk are not included.

The chart provisions short-retention, single-replica storage as a learning baseline. EBS volumes and EKS compute are billable. Review the rendered manifests and AWS cost impact before installation. The chart creates no public LoadBalancer or Ingress; use `kubectl port-forward` for UI access.

## Prerequisites and secret

Use the intended AWS profile, region, and EKS context. Verify before running Helm:

```bash
aws sts get-caller-identity
kubectl config current-context
kubectl get nodes
```

Create the Grafana credentials interactively/separately; do not commit the Secret:

```bash
kubectl create namespace pharmacy-observability
kubectl -n pharmacy-observability create secret generic grafana-admin \
  --from-literal=admin-user=admin \
  --from-literal=admin-password='REPLACE_WITH_A_UNIQUE_PASSWORD'
```

## Render and install

```bash
helm dependency update infra/helm/observability
helm lint infra/helm/observability
helm template pharmacy-observability infra/helm/observability \
  --namespace pharmacy-observability \
  -f infra/helm/observability/values-eks.yaml
```

Review the rendered resources, requested PVC sizes, image tags, and current AWS budget before installation. If approved:

```bash
helm upgrade --install pharmacy-observability infra/helm/observability \
  --namespace pharmacy-observability \
  --create-namespace \
  -f infra/helm/observability/values-eks.yaml
```

The application services must export traces to the in-cluster Collector at
`http://pharmacy-observability-opentelemetry-collector:4318/v1/traces` and expose their internal `/actuator/prometheus` endpoint. For application deployments with annotations, use:

```yaml
prometheus.io/scrape: "true"
prometheus.io/path: /actuator/prometheus
prometheus.io/port: "<container-port>"
```

The Prometheus endpoint discovery is annotation-based. Kafka lag alerts require a Kafka exporter that can reach the actual broker; add it only where the broker is network-reachable. The current project keeps Kafka local, so EKS cannot report its lag unless network access is explicitly configured. Do not open Kafka publicly for monitoring.

## Access the UIs privately

Find the actual service names first:

```bash
kubectl -n pharmacy-observability get svc
```

Then port-forward Grafana (replace service name if Helm rendered a different fullname):

```bash
kubectl -n pharmacy-observability port-forward svc/pharmacy-observability-kube-prometheus-stack-grafana 3001:80
```

Open `http://localhost:3001`. Prometheus may be reached using a separate port-forward to its ClusterIP service. Do not create a public ingress for Grafana, Prometheus, Loki, Tempo, or the Collector.

After checking the rendered service names, open a second terminal and run:

```bash
kubectl -n pharmacy-observability port-forward \
  svc/pharmacy-observability-kube-prometheus-stack-prometheus 9090:9090
```

Open `http://localhost:9090` to inspect targets and alerts. If the rendered
Prometheus service name differs, use the name shown by `kubectl get svc`.

## Removal

Helm uninstall removes the chart-managed workloads but PVC retention/removal behavior depends on chart settings and cluster policy. Inspect resources and snapshots first; delete retained PVCs only after confirming they contain no data you need:

```bash
helm uninstall pharmacy-observability -n pharmacy-observability
kubectl -n pharmacy-observability get pvc
```

Do not delete the namespace or PVCs blindly. Confirm the cluster context and AWS costs before cleanup.
