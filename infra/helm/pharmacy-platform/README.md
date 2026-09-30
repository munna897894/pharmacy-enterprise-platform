# pharmacy-platform Helm chart

See the [local deployment diagram](../../../docs/architecture-local.md) and
[AWS deployment diagram](../../../docs/architecture-cloud.md) for the
environment boundaries around this chart.

The application chart deploys the complete 12-workload fleet and disposable
in-cluster Redis. It does not create credentials or databases: provision the
per-service Secret and host-native MySQL/Kafka before installing locally.
The `values-local.yaml` file is the Docker Desktop configuration. Never use
the local JWT fixtures or demo passwords in an AWS installation.
The old three-service `values-production-example.yaml` was removed because
it did not configure a usable production environment; use a separately
reviewed, full-fleet AWS sandbox values file rather than renaming local values.

Render locally with:

```bash
helm lint infra/helm/pharmacy-platform
helm template pharmacy infra/helm/pharmacy-platform -f infra/helm/pharmacy-platform/values-local.yaml
helm upgrade --install pharmacy infra/helm/pharmacy-platform \
  --kube-context docker-desktop -n pharmacy \
  -f infra/helm/pharmacy-platform/values-local.yaml --wait
```

ConfigMap values are non-secret; each database-backed service loads only its
own `DB_PASSWORD` key from a named Secret. `auth-service` additionally mounts
the RSA keypair from a Secret at `/etc/auth`. Service and Pod annotations let
Prometheus scrape the internal Actuator endpoint, including the gateway's dedicated port 9081. The local
values export sampled traces to the private in-cluster Collector; install
`infra/helm/observability` before the application to avoid losing startup
traces.

Changing ConfigMap values in a Helm upgrade rolls the affected Pods. Changing
the separately managed database/JWT Secret does not change Helm values; after
rotating it, restart the affected Deployment and verify readiness.
