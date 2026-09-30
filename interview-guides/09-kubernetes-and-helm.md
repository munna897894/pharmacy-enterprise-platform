# 09 — Kubernetes and Helm (Prompt 09)

## Summary

The canonical local deployment is the full application Helm chart on Docker Desktop Kubernetes. It renders all twelve JVM Deployments/ClusterIP Services and an in-cluster Redis; native host MySQL and Kafka remain external to Kubernetes. The separate `pharmacy-observability` Helm release collects metrics, logs and traces. Raw `k8s/*.yaml` files also exist, but `kubectl apply -f k8s/` is **not** the documented installation path: the local Secret must be generated, and the chart/`values-local.yaml` is the deployment source. AWS uses separate rendered `infra/k8s/sandbox/` manifests, not this application chart as its deployment source.

## Deployment diagram

```text
Docker Desktop Kubernetes
  pharmacy:
    Helm application chart -> 12 Deployments + ClusterIP Services
      gateway :8080 (management :9081), auth :8081, product :8082,
      customer :8083, pharmacy :8084, inventory :8085,
      prescription :8086, order :8087, payment :8088,
      notification :8089, audit :8090, external mock :8080
    Redis Deployment/Service; pharmacy-common ConfigMap
    pharmacy-secrets generated separately for local DB passwords + JWT RSA keys
    pods -> host.docker.internal:3308 MySQL / :29092 Kafka
  pharmacy-observability:
    separate Helm release -> Prometheus/Grafana/Loki/Tempo/Alloy/Collector/exporter
operator -> gateway only via localhost:18080 port-forward
```

## Key files

| File | What it shows |
|---|---|
| `infra/helm/pharmacy-platform/templates/{service,redis,common-configmap}.yaml` | Data-driven service rendering, Redis, probes, resources, secure container settings and per-service DB secret references. |
| `infra/helm/pharmacy-platform/values-local.yaml` | Full twelve-workload port/image list, host-native dependency addresses and resource defaults. |
| `scripts/k8s-local-secret.sh`, `scripts/k8s-validate.sh` | Local-only Secret generation and chart/render validation. |
| `infra/helm/observability/values-local.yaml` | Independent in-cluster telemetry release. |
| `infra/k8s/sandbox/`, `scripts/aws-render-manifests.sh` | AWS full-fleet manifest source and deployment rendering. |
| `docs/self-run-guide.md`, `docs/architecture-local.md` | Supported local run path and architecture. |

## Core concepts

| Concept | Current application |
|---|---|
| Deployment vs StatefulSet | JVM services and Redis are Deployments; AWS's private single-broker Kafka uses a StatefulSet. Durable DB state is outside the local cluster. |
| Startup/readiness/liveness | Chart probes Actuator health groups, giving JVM cold starts time before liveness applies and removing unready pods from traffic. |
| Resources and security | Chart sets default requests/limits, non-root/read-only-root filesystem, dropped capabilities and per-service DB password references. |
| ConfigMap vs Secret | Shared non-sensitive URLs live in `pharmacy-common`; local passwords/fixture RSA keys are generated separately. Do not apply the ignored older `k8s/pharmacy-secrets.yaml` sample. |
| DNS and exposure | Internal traffic uses Service names. Gateway is ClusterIP locally; port-forward exposes it only to the workstation. |
| Helm vs raw manifests | Helm chart is the local release source; raw resources are not an interchangeable one-command install. AWS renders its own manifests with Secrets Manager-backed init containers. |

## Common interview Q&A

**Q: Do the chart and raw YAML have equivalent deployment behavior?**
A: Do not assume parity. The local chart includes startup/readiness/liveness probes and resource requests/limits, and references a generated Secret; the documented install is `helm upgrade --install ... -f infra/helm/pharmacy-platform/values-local.yaml`, not `kubectl apply -f k8s/`.

**Q: How does a pod reach MySQL and Kafka locally?**
A: `host.docker.internal:3308` reaches the project MySQL instance; `host.docker.internal:29092` is the pod-facing Kafka advertised listener. Redis and the mock run in the `pharmacy` namespace.

**Q: How do you expose the gateway?**
A: Locally use `kubectl -n pharmacy port-forward svc/api-gateway 18080:8080`. AWS defaults to private port-forward through the EKS API; opt-in HTTPS ALB requires ACM and `/32` allowlisting. Neither is a production internet ingress.

**Q: What do probes and resource limits protect?**
A: Startup probes prevent premature JVM restarts, readiness gates traffic, liveness restarts stuck pods, and resource requests/limits bound scheduling/eviction behavior. One replica and finite node capacity still mean this is not HA.

## Gotchas / trace-through

- The Helm chart does not generate credential values; `scripts/k8s-local-secret.sh` provisions demo local secrets separately. Never assume a chart render proves credentials are available.
- `external-mock-service` is included at internal `8080`. Do not confuse its port with the separately named gateway `8080`.
- AWS full-fleet values can render, but the AWS deploy path uses sandbox manifests because its per-workload Secrets Manager fetches must not be replaced with credential copies in Kubernetes Secrets.

**Rollout:** rebuild `pharmacy/order-service:local`, restart `deploy/order-service`, check `kubectl -n pharmacy rollout status deploy/order-service --timeout=5m`, then verify readiness and the saga through the gateway. Kubernetes does not automatically prove a new image passes business E2E.
