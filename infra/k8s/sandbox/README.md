# Full-fleet temporary AWS Kubernetes manifests

See the [AWS deployment diagram](../../../docs/architecture-cloud.md) for how
these rendered workloads connect to EKS, RDS, Secrets Manager and the private
observability stack.

These manifests are consumed by `scripts/aws-apply.sh`; do not apply placeholders directly. The
script renders AWS account-specific image URLs, RDS endpoints, secret ARNs and IRSA role ARNs into
a mode-restricted temporary directory. Secret values are fetched by short-lived pod init containers
and are written only to atomically renamed, mode-0600 files on memory-backed pod volumes. The
retrieval commands do not pipe secret output through `tee` or print it to init-container logs.
Secret values are not written into ConfigMaps, Kubernetes Secrets, or committed manifests.

| Manifest | Purpose |
|---|---|
| `00-service-accounts.yaml` | Namespace and workload/IRSA service accounts |
| `00-redis.yaml` | Ephemeral Redis cache |
| `01-auth-service.yaml` | Auth Deployment/Service and JWT/DB secret retrieval |
| `02-product-service.yaml` | Product Deployment/Service and DB secret retrieval |
| `03-api-gateway.yaml` | Gateway Deployment/ClusterIP Service |
| `04-alb-controller-service-account.yaml` | ALB controller IRSA ServiceAccount; rendered only for `alb-https` |
| `04-ingress.yaml` | HTTPS:443-only ALB Ingress (ACM certificate, host rule, `/32` allowlist) to the gateway; rendered only for `alb-https` |
| `05-db-bootstrap.yaml` | Temporary IRSA Job creating ten schemas and database users |
| `06-kafka.yaml` | Private single-broker KRaft StatefulSet (gp3 volume, topic auto-creation disabled), ClusterIP Services and NetworkPolicy |
| `06-kafka-topics.yaml` | Idempotent Job creating the 5 domain topics plus `.retry`/`.dlt` (15 total); apply waits for it before the fleet |
| `07-jvm-service-template.yaml` | Rendered for customer, pharmacy, inventory, prescription, order, payment, notification and audit |
| `08-external-mock-service.yaml` | DB-free internal mock Deployment/Service |
| `observability-values-aws.yaml` | AWS capacity overlay for `infra/helm/observability` (layered after `values-eks.yaml`) |

The default gateway exposure is `port-forward`: neither `04-*` manifest is rendered and nothing is
publicly reachable. The ALB never has an HTTP listener; see "Gateway exposure and transport
security" in `docs/aws-plan-review.md`.

All service workloads are single-replica and resource-limited for two `t4g.large` (arm64) workers. Redis is
intentionally non-persistent; Kafka keeps data on an 8 GiB gp3 volume for the session only; Kafka is one private in-cluster broker, never MSK. EKS VPC
CNI network-policy enforcement is enabled for the Kafka namespace isolation policy.
