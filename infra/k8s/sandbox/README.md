# Kubernetes manifests — Prompt 12 sandbox slice

Plain manifests for the 3 services deployed to the temporary EKS cluster (auth-service,
api-gateway, product-service) plus a self-hosted Redis pod for product-service's cache.

**Do not `kubectl apply` these files directly** — they contain `__PLACEHOLDER__` tokens that
`scripts/aws-apply.sh` substitutes with real ECR image URIs, the RDS endpoint, and Secrets Manager
ARNs (all sourced from `terraform output`) before applying. See `infra/terraform/README.md` for
the full runbook.

| File | Purpose |
|---|---|
| `00-redis.yaml` | Self-hosted Redis (product-service cache) |
| `01-auth-service.yaml` | auth-service Deployment/Service — init container fetches DB creds + JWT RSA keypair from Secrets Manager via IRSA |
| `02-product-service.yaml` | product-service Deployment/Service — init container fetches DB creds from Secrets Manager via IRSA |
| `03-api-gateway.yaml` | api-gateway Deployment/Service — no AWS API access needed |
| `04-ingress.yaml` | ALB Ingress, internet-facing, fronting **only** api-gateway |

The Namespace and ServiceAccounts (with IRSA role annotations) are created by Terraform
(`infra/terraform/envs/sandbox/main.tf`), not by these manifests.
