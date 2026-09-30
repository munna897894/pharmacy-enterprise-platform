# 12 — AWS and Terraform (Prompt 12)

## Current scope and verification boundary

The **current** AWS design is a temporary, independent, **full-fleet** non-production EKS sandbox for synthetic data. Terraform, manifest rendering, validation, cost review and guarded create/destroy scripts are prepared. The current full-fleet topology has **not** been live-verified: the sandbox SSO session was expired at the last documented attempt. Offline validation is not evidence that an EKS cluster ran or passed smoke tests. See `docs/aws-plan-review.md`, `docs/architecture-cloud.md` and `infra/terraform/README.md` for authoritative scope, costs and safety controls.

An **earlier three-service EKS sandbox exercise** (auth, product, gateway) was deployed, smoke-tested through an ALB and destroyed. Its observations below are explicitly *historical slice evidence*, not a full-fleet AWS result or proof of current scripts.

## Prepared full-fleet topology

```text
operator (AWS SSO; approved account/region; explicit public /32)
  -> EKS API -> private kubectl port-forward -> gateway ClusterIP :8080
                optional HTTPS-only ALB :443 with ACM + /32 allowlist
  EKS: two arm64 t4g.large workers in public subnets (no NAT)
    pharmacy namespace:
      gateway :8080 (management :9081)
      auth :8081, product :8082, customer :8083, pharmacy :8084,
      inventory :8085, prescription :8086, order :8087,
      payment :8088, notification :8089, audit :8090,
      external mock :8080
      Redis Deployment + private single-broker Kafka KRaft StatefulSet
      five domain topics + retry/DLT topics
      per-workload IRSA -> Secrets Manager -> memory-backed credential files
    pharmacy-observability namespace:
      private Prometheus/Grafana/Loki/Tempo/Alloy/OTel Collector
  private RDS MySQL 8.0: ten service-owned schemas/users
  ECR: twelve immutable-tag images; S3: remote Terraform state
```

RDS is in isolated private DB subnets and accepts MySQL from the EKS cluster. Nodes have public IPs to avoid NAT costs but no unsolicited public inbound access. No MSK or ElastiCache, no production data, no automatic cloud deployment from CI. The default gateway mode creates **no** ALB. This is a cost-controlled learning sandbox, not a high-availability or production security blueprint. Prompt 14 Dynatrace/Splunk integrations are disabled preparation, not live vendor deployments.

## Key files and operator workflow

| File | What it shows |
|---|---|
| `docs/aws-plan-review.md` | Current cost/threat analysis, budgets, security boundaries and live-verification status. |
| `docs/architecture-cloud.md` | Full-fleet diagram, namespaces, private RDS, Kafka/Redis and optional ingress. |
| `infra/terraform/bootstrap/`, `infra/terraform/envs/sandbox/`, `infra/terraform/modules/` | S3 state backend, EKS, VPC, RDS, ECR, Secrets Manager and IAM scope. |
| `infra/k8s/sandbox/` | AWS-rendered full-fleet workload sources, topic bootstrap, external mock and observability profile. |
| `scripts/aws-{bootstrap,validate,plan,apply,smoke-test,destroy,post-destroy-check}.sh` | Guarded lifecycle. `scripts/aws-cost-estimate.sh` is offline. |
| `infra/terraform/README.md` | Required account/region/SSO gates, provisioning and teardown order. |

The documented flow is: set independently verified `EXPECTED_AWS_ACCOUNT_ID`, region/profile/operator `/32` and state bucket; review budgets and cost estimate; run `./scripts/aws-validate.sh` and `./scripts/aws-plan.sh`; inspect the saved plan; **only after approval and valid credentials**, run `./scripts/aws-apply.sh`, `./scripts/aws-smoke-test.sh`, then `./scripts/aws-destroy.sh` and `./scripts/aws-post-destroy-check.sh`. Scripts require interactive confirmations; do not use `-auto-approve` or assume expired SSO credentials authorize a dry-run. Retire state history only via the documented guarded process after the sandbox is empty.

Terraform does not generate application credentials into its versioned state: RDS manages its master password and `scripts/aws-seed-secrets.sh` supplies per-service credentials and RSA keys after apply. IRSA-scoped init containers fetch only the workload's secrets into memory-backed files, rather than copying them to Kubernetes Secrets. State and saved plans still contain infrastructure metadata and must remain private.

## Core concepts

| Concept | Interview explanation |
|---|---|
| Temporary EKS vs production | Two cost-limited workers and one Kafka broker demonstrate scheduling and choreography, not high availability. |
| RDS ownership | One private instance hosts ten isolated schema/user pairs; logical isolation is not physical instance isolation. |
| IRSA and Secrets Manager | Pods use narrowly scoped roles to fetch only their own credentials without static AWS keys or committed secret values. |
| Terraform state | Sensitive outputs are not encryption; state is encrypted/versioned in S3 and credentials are kept out of Terraform-generated values. |
| Ingress | Default private port-forward; opt-in HTTPS ALB requires issued ACM certificate and restricted client `/32`s. |
| Budgets and destroy | Alerts do not cap spend. Inventory after destroy is required, including EKS/EC2/ELB/RDS/NAT/EIP/ECR leftovers. |

## Common interview Q&A

**Q: Has the full platform passed on AWS?**
A: No. Current full-fleet EKS deployment has passed offline preparation/validation but has not been applied and smoke-tested live. A **previous three-service** EKS slice did run and was destroyed; do not conflate those results.

**Q: Why run a broker inside EKS instead of MSK?**
A: The goal is a short synthetic-data exercise without the cost of MSK. A private single-broker Kafka StatefulSet is sufficient to rehearse the event flow but has no broker redundancy.

**Q: How do pods get DB credentials without committing Secrets?**
A: Secrets Manager holds per-service values; workload-specific IRSA limits reads, and init containers fetch to memory-backed files. Terraform intentionally does not mint secret values into its versioned state.

**Q: Why no NAT Gateway, and is public-subnet worker placement production-safe?**
A: Avoiding NAT's recurring cost is a deliberate temporary-sandbox tradeoff. Worker security groups restrict inbound access; RDS remains private. A production design needs different networking, redundancy and a formal threat review.

**Q: What must happen before and after `apply`?**
A: Before: verify account, region, workspace, budgets, operator CIDR, access mode, plan and billable scope. After the exercise: destroy with explicit confirmation and run the post-destroy resource inventory. Budget alerts alone do not clean up anything.

## Historical three-service sandbox evidence (not current full-fleet validation)

The prior auth/product/gateway EKS exercise did apply resources, run an ALB smoke path against RDS, and destroy them with a recorded post-destroy inventory. It used three images, a smaller node group and different Terraform/security/manifest choices. Do **not** reuse its ~11-minute teardown, historical resource counts, prices, Kubernetes version or ALB-default assumption as current estimates.

Useful lessons from that bounded run:

- Spring Boot 3 Redis properties belong under `spring.data.redis`, not `spring.redis`; the old key silently fell back to localhost and broke product startup.
- Hibernate's UUID JDBC binding needs to agree with Flyway's `CHAR(36)` columns; `@Column(columnDefinition = "CHAR(36)")` alone does not control parameter binding.
- An issuer validator rejects a token without the expected `iss` claim even when a less strict service accepts its signature. Test JWTs at both gateway and downstream services.
- A custom security group expected on node traffic did not match the managed node group's actual ENIs; the **current** design restricts RDS ingress to the EKS cluster security group. Confirm traffic from a pod during any new live run.
- PKCS#1 vs PKCS#8 PEM format, init-image tooling and JVM cold-start/probe timing caused genuine deployment failures in that historical slice. Current Secrets Manager seeding and probe settings differ; verify them afresh instead of claiming they were exercised by the old run.
- A single tiny node had insufficient headroom for rolling updates. The current two-node sizing is a plan, not measured proof of zero-downtime rollouts.

## Remaining risks

Current AWS full-fleet smoke, Newman, observability, compensation and destroy/recreate evidence are pending a valid SSO session and explicit billable approval. Optional ALB and Prompt 14 vendor hooks have not been live-verified. Use the current cost estimate and runbooks rather than the historical slice's timings.
