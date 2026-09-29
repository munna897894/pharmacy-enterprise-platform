# 09 — Kubernetes and Helm (Prompt 09)

## Summary

The platform ships two parallel Kubernetes deployment mechanisms: 14 raw per-resource YAML manifests under `k8s/` (one Deployment+Service pair per business service, plus a shared namespace/ConfigMap/Secret), and a Helm chart under `infra/helm/pharmacy-platform/` that generates the same shape of resources from a single templated `service.yaml` iterating over `values.yaml`'s `services` map. Both mechanisms are real and used (this is the layer where the earlier "audit/order/payment services missing from Kubernetes" bug was found and fixed in a prior session). Neither mechanism defines resource requests/limits, an Ingress, or an HPA — real, confirmed gaps worth knowing precisely.

## Diagram — the real K8s topology

```
Namespace: pharmacy  (k8s/namespace.yaml)
                                    │
        ┌───────────────────────────┼────────────────────────────┐
        ▼                           ▼                             ▼
  ConfigMap: pharmacy-common   Secret: pharmacy-secrets      11 Deployment+Service pairs
  (DB_HOST=host.docker.internal (per-service *_DB_PASSWORD    (one per business service,
   REDIS_HOST=redis             values, stringData)            api-gateway included;
   KAFKA_BOOTSTRAP_SERVERS=                                     external-mock-service has
   host.docker.internal:9092                                   NO k8s manifest — see gotcha)
   per-service DB_NAME/DB_USER
   pairs for ALL 11 services)

  Each Deployment (e.g. k8s/order-service.yaml):
    envFrom: [configMapRef: pharmacy-common, secretRef: pharmacy-secrets]
    env: DB_HOST/DB_NAME/DB_USER overrides + DB_PASSWORD via secretKeyRef
    readinessProbe: GET /actuator/health/readiness  (initialDelay 20s, period 10s)
    livenessProbe:  GET /actuator/health/liveness   (initialDelay 30s, period 15s)
    NO resources.requests/limits block anywhere
  Each Service: type ClusterIP, port == targetPort == the service's real app port

  api-gateway Service: ClusterIP, port 80 -> targetPort 8080
    (no Ingress resource anywhere in the repo routes external traffic to this —
     see gotcha: reaching the gateway from outside the cluster requires
     kubectl port-forward or an ad hoc LoadBalancer/NodePort, neither of which
     is codified in this repo)

Helm chart (infra/helm/pharmacy-platform/):
  templates/namespace.yaml       -> same Namespace object, templated name
  templates/common-configmap.yaml -> same ConfigMap, values pulled from values.yaml's commonConfig map
  templates/service.yaml         -> {{- range $name, $svc := .Values.services }} ... {{- end }}
                                     ONE shared template stamps out N Deployment+Service pairs
                                     from values-local.yaml / values-production-example.yaml
                                     ⚠ this templated Deployment does NOT include readiness/
                                       liveness probes at all — less complete than the raw
                                       k8s/*.yaml manifests (see gotcha)
  NO templates/secret.yaml exists — the Helm chart has no secret management story at all
  (the raw k8s/pharmacy-secrets.yaml exists only in the non-Helm manifest set)
```

## Key files

| File | What it shows |
|---|---|
| `k8s/namespace.yaml` | The `pharmacy` namespace all resources live in. |
| `k8s/common-configmap.yaml` | Shared non-secret config: DB host/port, Redis host/port, Kafka bootstrap servers, auth-service host/port, and every service's `DB_NAME`/`DB_USER` pair — one ConfigMap consumed by every Deployment via `envFrom`. |
| `k8s/pharmacy-secrets.yaml` | A single `Secret` (type `Opaque`, `stringData`) holding every service's `DB_PASSWORD` in plaintext-in-YAML form — real for local/dev use, but the kind of file that must never be committed with real credentials in a genuine environment (see 12-aws-terraform guide for how Secrets Manager would replace this). |
| `k8s/order-service.yaml` (representative of all 11) | `Deployment` + `Service` pair: `containerPort: 8087` matching the real app port, `envFrom` referencing the shared ConfigMap/Secret plus per-service `env:` overrides, and both readiness (`/actuator/health/readiness`) and liveness (`/actuator/health/liveness`) HTTP probes with distinct initial-delay/period tuning. |
| `k8s/pharmacy-service.yaml`, `k8s/customer-service.yaml`, `k8s/prescription-service.yaml` | Proof that the previously-missing audit/order/payment coverage gap (fixed in a prior session) is resolved — all 11 business services now have a manifest; **external-mock-service still has none** (see gotcha). |
| `infra/helm/pharmacy-platform/Chart.yaml` | Standard Helm chart metadata (name, version, description). |
| `infra/helm/pharmacy-platform/templates/service.yaml` | The single shared template: `{{- range $name, $svc := .Values.services }}` stamps out a Deployment (image, port, `envFrom: pharmacy-common` only — no secretRef here) and a matching ClusterIP Service for every entry in `values.yaml`'s `services` map, in one file. |
| `infra/helm/pharmacy-platform/values-local.yaml` | Per-environment values: `commonConfig` map (mirrors the raw ConfigMap) and a `services` map listing every service's image + port — **`product-service.port` is set to `8081`, identical to `auth-service.port`**, baking the known port-collision issue directly into the Helm values (see gotcha). |
| `infra/helm/pharmacy-platform/values-production-example.yaml` | A second values file showing what a "production" override would look like (different image tags/registry, presumably), demonstrating Helm's per-environment values-file pattern. |
| `scripts/k8s-validate.sh` | A validation script (likely `kubectl apply --dry-run` or `kubeval`/`kustomize` style checks) used to sanity-check the raw manifests before applying them — confirms the raw `k8s/` manifests are the actively-maintained/validated path, with Helm as a parallel/newer effort. |

## Core concepts

| Concept | Explanation | Why it matters |
|---|---|---|
| Deployment (not StatefulSet) for every service | All 11 business services + api-gateway are plain `Deployment` objects. | Every service here is stateless at the application-process level (all durable state lives in MySQL/Redis/Kafka, not the pod's local disk or identity) — StatefulSets are only needed when pods need stable network identity or per-pod persistent storage, neither of which applies to these services. |
| Readiness vs liveness probes | Readiness (`/actuator/health/readiness`) controls whether the pod receives traffic via the Service; liveness (`/actuator/health/liveness`) controls whether Kubernetes restarts the container. | These answer two different questions — "is this pod ready to serve traffic right now" (readiness) vs "has this pod's process become unrecoverably stuck and needs a restart" (liveness) — using Spring Boot Actuator's dedicated health *groups* (not the general `/actuator/health` endpoint) is the correct way to split these concerns. |
| ConfigMap vs Secret | Non-sensitive shared config (`DB_HOST`, `KAFKA_BOOTSTRAP_SERVERS`, per-service `DB_NAME`/`DB_USER`) lives in a `ConfigMap`; sensitive values (`*_DB_PASSWORD`) live in a `Secret`. | Kubernetes Secrets are base64-encoded (not encrypted at rest by default without additional cluster configuration) and have access-control/audit semantics distinct from ConfigMaps — using the right object type for the right data is a baseline security hygiene practice, even though this repo's Secret values are plaintext dev passwords. |
| Kubernetes DNS-based service discovery | Every service reaches another purely by Kubernetes Service name (`http://inventory-service:8085`, `http://auth-service:8081`) — no Eureka/Consul. | Kubernetes' built-in DNS (CoreDNS) resolves `<service-name>.<namespace>.svc.cluster.local` (or just `<service-name>` within the same namespace) automatically for every Service object — this is why the platform explicitly avoided adding a separate service-registry tool, per the repository's own stated architecture constraint. |
| `envFrom` + targeted `env:` overrides | Each Deployment pulls the bulk of its config via `envFrom: [configMapRef, secretRef]`, then layers a few service-specific `env:` entries (like `DB_NAME`) on top. | A clean way to share 90% of config across all services while still letting each Deployment override the handful of values unique to it, without duplicating the entire config block per service. |
| Rolling update strategy (Deployment default) | Not explicitly customized in any manifest here — Kubernetes' default `RollingUpdate` strategy applies. | Worth knowing the default behavior (`maxUnavailable: 25%`, `maxSurge: 25%`) even when a repo doesn't override it, since "what's the default rollout strategy and would you change it" is a common follow-up question. |
| Helm templating (`range` over a values map) | `templates/service.yaml` uses one `{{- range $name, $svc := .Values.services }}` block to generate N Deployment+Service pairs from a single template, driven entirely by `values.yaml`'s `services` map. | This is the "shared template, data-driven" Helm pattern — as opposed to one template file per service — trading some flexibility (can't easily give one specific service an extra field without adding a conditional) for much less duplication across 11+ near-identical services. |
| Multiple values files per environment | `values-local.yaml` vs `values-production-example.yaml` represent Helm's standard "one values file per target environment" pattern (`helm install -f values-production.yaml`). | This is how the same chart/templates produce different images, resource sizing, or replica counts per environment without duplicating the templates themselves. |
| No Ingress in this repo | Nothing in `k8s/` or the Helm chart defines an `Ingress` resource; api-gateway's own Service is just `ClusterIP`. | In a real cluster you'd add an Ingress (or a `LoadBalancer`-type Service) in front of api-gateway to expose it outside the cluster — today, reaching it locally requires `kubectl port-forward svc/api-gateway 8080:80` or similar, which is a real, confirmed gap if the goal is "the gateway is the single public entry point," since nothing in the repo actually codifies *how* external traffic reaches it. |

## Common interview Q&A

**Q: Why are all these services deployed as Kubernetes `Deployment`s instead of `StatefulSet`s?**
A: `StatefulSet` exists for workloads that need stable, ordered pod identity and/or per-pod persistent storage (databases, Kafka brokers, etc.) — every business service here is stateless at the pod level; all durable data lives in the shared MySQL, Redis, and Kafka, none of which are actually deployed as Kubernetes-managed StatefulSets in this repo (in fact, they're not deployed to Kubernetes at all here — the ConfigMap's `DB_HOST: host.docker.internal` shows the app pods reach MySQL/Kafka running via Docker Compose *outside* the cluster, not inside it).

**Q: What's the practical difference between the readiness and liveness probes configured here?**
A: Readiness probes gate whether the Service's Endpoints include this pod at all — if it fails, the pod is temporarily removed from load balancing but not restarted (useful for "I'm alive but temporarily overloaded/warming up"). Liveness probes, if they fail repeatedly, cause Kubernetes to kill and restart the container (useful for "I'm permanently stuck/deadlocked"). Using Spring Boot's dedicated `/actuator/health/readiness` and `/actuator/health/liveness` groups (rather than the aggregate `/actuator/health`) lets the app itself decide which specific health indicators feed each probe.

**Q: Why does `host.docker.internal` appear in the Kubernetes ConfigMap?**
A: Because local development targets a single-node cluster (Docker Desktop Kubernetes or similar) running alongside — not replacing — the docker-compose-managed MySQL/Kafka/Redis containers; `host.docker.internal` is Docker Desktop's special DNS name for reaching the host machine's other containers from inside the Kubernetes cluster's pod network. This reflects a real, pragmatic local-dev decision (documented in a prior "switching to local DB" fix) rather than a fully self-contained cluster.

**Q: This repo has both raw `k8s/*.yaml` manifests and a Helm chart — why, and are they equivalent?**
A: They largely produce the same shape of resources (Deployment + Service per business service, shared ConfigMap, namespace), but they're not identical: the Helm chart's shared `service.yaml` template does not include readiness/liveness probes at all (the raw manifests do), and the Helm chart has no equivalent of the raw `pharmacy-secrets.yaml` — there's no `templates/secret.yaml`. This suggests the raw manifests are the more mature, actively-validated path (backed by `scripts/k8s-validate.sh`), while the Helm chart is either a newer, still-catching-up effort or intended for a narrower use case.

**Q: How would you expose api-gateway outside the cluster in a real deployment?**
A: Add an `Ingress` resource (with an Ingress controller like NGINX or an AWS ALB Ingress Controller) routing a hostname/path to api-gateway's Service, or change api-gateway's Service `type` to `LoadBalancer` in a cloud environment. Neither exists in this repo today — locally, the only way to reach api-gateway from outside the cluster is `kubectl port-forward`, which is fine for local dev but not a real production ingress story.

**Q: What's missing from these manifests that you'd insist on before calling this production-ready?**
A: `resources.requests`/`resources.limits` on every container (none are set anywhere, so there's no CPU/memory guarantee or ceiling, and every pod gets Kubernetes' `BestEffort` QoS class — the first to be evicted under node pressure), an `Ingress` for api-gateway, a `PodDisruptionBudget` for safe voluntary evictions, and probably a `HorizontalPodAutoscaler` if traffic is expected to vary — none of these exist in either the raw manifests or the Helm chart today.

**Q: What Quality-of-Service (QoS) class do these pods get, and why does it matter?**
A: With no `resources.requests`/`limits` set anywhere, every pod defaults to the `BestEffort` QoS class — the lowest priority tier, meaning these pods are the first candidates for eviction if a node runs low on memory or CPU, regardless of how critical the service is (e.g., payment-service gets the same eviction priority as external-mock-service). Setting at least `requests` would move pods to `Burstable`, and setting `requests == limits` would achieve `Guaranteed` for the most critical services.

## Gotchas / real findings

- **`external-mock-service` has no Kubernetes manifest at all** (neither raw `k8s/` nor Helm) — it can only be exercised locally via Maven/Docker directly, not through the same Kubernetes deployment path as the other 11 services. Since payment-service's `PaymentGatewayClient` depends on it, any Kubernetes-only deployment of this platform cannot exercise the full payment flow without deploying that mock service by some other means.
- **No `resources.requests`/`limits` anywhere** — every pod across both the raw manifests and the Helm chart is `BestEffort` QoS, the lowest eviction priority, with no protection against noisy-neighbor resource contention.
- **No `Ingress` resource anywhere** — api-gateway's Service is `ClusterIP` only; there is no codified way to reach it from outside the cluster in this repo (would require `kubectl port-forward` or a manually-created LoadBalancer/Ingress not present in source).
- **The Helm chart's shared `service.yaml` template omits readiness/liveness probes entirely**, unlike the raw `k8s/*.yaml` manifests — meaning a Helm-based deployment today gets weaker rollout/self-healing guarantees than the raw-manifest deployment path for the exact same services.
- **The Helm chart has no Secret template** (`templates/secret.yaml` doesn't exist) — there's no Helm-native way to supply DB passwords via this chart alone; it would need to be paired with a manually-applied Secret (like the raw `k8s/pharmacy-secrets.yaml`) or a separate secrets-management integration.
- **`values-local.yaml` bakes the known port collision directly into the Helm chart**: `product-service.port: 8081` is identical to `auth-service.port: 8081` — confirming the port-collision issue found earlier in this learning series isn't just a raw-`application.yml` quirk, it's also present in the Helm values that would drive a real deployment.
- **No `HorizontalPodAutoscaler` or `PodDisruptionBudget`** exists for any service in either mechanism — every Deployment is a fixed `replicas: 1`, meaning zero horizontal scaling and zero protection against a voluntary node drain taking down every replica of a service at once (since there's only one replica to begin with).

## Trace-through: rolling out a new `order-service` image via the raw manifests

1. A new image is built and pushed (e.g., `docker.io/munna897/pharmacy-order-service:local-20260917-3`, following the same naming pattern already used in `k8s/order-service.yaml`).
2. `k8s/order-service.yaml`'s `image:` field is updated to the new tag, and `kubectl apply -f k8s/order-service.yaml` is run (or the whole `k8s/` directory is applied).
3. Kubernetes' default `RollingUpdate` strategy creates a new ReplicaSet with the updated pod template and begins terminating the old pod while starting a new one (bounded by the default `maxUnavailable`/`maxSurge` of 25%, unquestioned in this manifest).
4. The new pod starts; Kubernetes waits for its `readinessProbe` (`GET /actuator/health/readiness` on port 8087, first check after a 20s `initialDelaySeconds`) to succeed before considering it "ready" and adding it to the `order-service` Service's Endpoints.
5. Only once the new pod is ready does Kubernetes finish terminating the old pod — this is what prevents a brief window of zero available replicas during the rollout (though with `replicas: 1` everywhere, there genuinely is at least a brief moment of reduced capacity, since there's no second replica to absorb traffic during the swap).
6. If the new pod's `livenessProbe` ever fails after that (30s initial delay, 15s period), Kubernetes restarts just that container in place rather than rolling back the Deployment automatically — a full automatic rollback on failed rollout would require additional configuration (e.g., a `Deployment` rollout status check in CI) not present in this repo today.
