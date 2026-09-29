# AWS Plan Review — Prompt 12A (Cost & Threat Review)

> **Status:** Pre-Terraform review. No infrastructure code exists yet. This document must be
> explicitly approved by the developer before any Terraform is authored (Prompt 12B).

## 1. Scope of this exercise

This is a **temporary, minimal, single-region learning environment**, not a production deployment.

- **Region:** `us-east-1` only (a second region is an explicit future follow-up, out of scope here).
- **AWS account:** dedicated sandbox account `489597129790`, accessed via IAM Identity Center SSO
  (profile `pharmacy-sandbox`, role `AdministratorAccess`). No root credentials, no static long-lived
  IAM user access keys used for human login.
- **Service slice deployed to AWS (real, not conceptual):**
  - `auth-service` — issues/validates RSA-signed JWTs, exposes JWKS.
  - `api-gateway` — Spring Cloud Gateway (WebFlux), single public entry point.
  - `product-service` — one real business read/write path backed by MySQL (RDS) + Redis cache.
- **Everything else stays local**: customer, pharmacy, inventory, prescription, order, payment,
  notification, audit, external-mock services, and **Kafka** (no MSK — confirmed with user; the
  deployed slice has no Kafka dependency, so this has zero functional impact on the exercise).
- **Compute:** a real, minimal, temporary **EKS cluster** (see §4 for the conceptual-only tradeoff
  analysis — EKS was chosen deliberately for realism).

## 2. Local → AWS component mapping

| Local (docker-compose) component | AWS equivalent | Notes |
|---|---|---|
| `auth-service`, `api-gateway`, `product-service` Docker images | **ECR** repositories (one per service) + **EKS** Deployments/Services | Images built locally/CI, pushed to ECR, pulled by EKS nodes. |
| Docker network routing between services | **EKS cluster networking** (VPC CNI, in-cluster Service DNS) | Same `http://<service>:<port>` style internal calls, just cluster-DNS instead of compose-DNS. |
| `docker-compose` gateway exposed on host port 8080 | **ALB** (Application Load Balancer) via AWS Load Balancer Controller + Ingress, targeting `api-gateway` Service | Only the gateway is internet-facing — mirrors "all public traffic enters through api-gateway" rule. |
| MySQL container (`mysql:8`, shared server, per-service schema/user) | **RDS for MySQL** — single small instance (e.g. `db.t4g.micro`), two schemas: `auth_service`, `pharmacy_product`, two distinct DB users (least privilege, matches existing Flyway-per-service model) | Multi-AZ **disabled** for this exercise (cost). Deletion protection **disabled** (temporary, must be destroyable) — explicitly confirmed at destroy time (§9). |
| Redis container (product-service cache) | Either **self-hosted Redis pod in EKS** (cheapest, no extra managed service) or **ElastiCache** (more realistic managed-service pattern) | **Decision: self-hosted Redis pod in EKS for this pass** — ElastiCache adds a billable component with limited additional learning value for a cache-only use case; can be a follow-up. |
| `JWT_PRIVATE_KEY_PATH` / `JWT_PUBLIC_KEY_PATH` mounted files, DB passwords in `.env` | **Secrets Manager** — RSA keypair and DB credentials stored as secrets, mounted into pods via the AWS Secrets Store CSI driver or fetched at startup via IRSA-scoped IAM role | Never baked into container images or plain Kubernetes Secrets/ConfigMaps in plaintext. |
| Local `logs/*.log`, Micrometer → local Prometheus/Grafana | **CloudWatch Logs** (container stdout/stderr via Fluent Bit/EKS logging add-on) + **CloudWatch Container Insights** for basic metrics | Full Prometheus/Grafana/OTel stack stays **local only** for this pass — out of scope here (that is Prompt 10's concern); CloudWatch is the minimal AWS-native substitute so the deployed slice still has *some* observability. |
| Terraform/deployment artifacts | **S3** bucket for Terraform remote state (see §8) | Versioned + encrypted, no other application data stored in S3 for this pass. |
| IAM users/roles for CI or humans | **IAM OIDC** — EKS's own OIDC provider for IRSA (pod-level AWS permissions), plus GitHub Actions OIDC federation *if/when* Prompt 11 wires this into CI (not yet — Terraform apply/destroy stays human-run for this pass) | No static AWS access keys committed anywhere. |

## 3. Exact billable components & major hidden-cost risks

| Component | Billable? | Est. hourly-ish driver | Hidden-cost risk |
|---|---|---|---|
| EKS control plane | **Yes** — flat fee (~$0.10/hr) regardless of usage | Runs whenever the cluster exists, even idle | **Biggest hidden cost**: if left running "just for a bit longer" for days, this adds up continuously. Must be destroyed promptly after the exercise (§9). |
| EKS worker nodes (EC2, e.g. 1–2× `t3.small`/`t3.medium` via managed node group or Fargate) | **Yes** — standard EC2 On-Demand pricing | Idle nodes still bill | Keep node count minimal (1–2 nodes, smallest size that runs 3 small Spring Boot pods); consider Fargate profile to avoid managing/forgetting EC2 instances entirely (evaluated in §4). |
| RDS MySQL instance | **Yes** — instance-hour + storage (gp3, min ~20GB) | Runs whenever the DB instance exists | Storage + automated backups persist even if compute is stopped; must be fully deleted (no "stopped" state left lying around) at destroy time. |
| ALB | **Yes** — hourly + LCU (load-balancer capacity unit) charges | Bills as long as it exists, even near-zero traffic | Low absolute cost for a short exercise, but easy to forget since it's "just a load balancer." |
| **NAT Gateway** | **Yes — explicitly avoided by design** (see §4/§5) | ~$0.045/hr + **per-GB data processing** | Classic AWS learning-exercise cost trap: NAT Gateway hourly charge *plus* per-GB egress processing charge compounds quickly and is easy to forget is running. **Not created in this exercise** unless explicitly re-approved. |
| ECR repositories | **Yes**, but negligible | Storage-GB/month for a handful of small images | Effectively free at this scale; still enable lifecycle policies to auto-expire old image tags so it never silently grows. |
| Secrets Manager secrets | **Yes**, small flat fee per secret/month (~$0.40/secret/month, prorated) + API call charges | Prorated for exercise duration | Negligible for 2–3 secrets over a short period, but not literally free — included in the budget model. |
| CloudWatch Logs/Container Insights | **Yes** — ingestion + storage per GB | Low volume for 3 small services over a short exercise | Set short retention (e.g. 3–7 days) on log groups so nothing lingers as a forgotten cost after destroy (log groups are **not** destroyed by `terraform destroy` unless explicitly managed as a Terraform resource — must be in scope). |
| S3 (Terraform state bucket) | **Yes**, negligible | KBs of state file | Effectively free; still versioned+encrypted per §8. |
| Data transfer (egress from ALB, cross-AZ) | **Yes**, usually negligible at this traffic volume | Smoke-test-level traffic only | Not a concern for a short manual smoke test; would matter at real scale. |
| Elastic IPs (EIP) | **Yes if allocated but unattached** | Free while attached to a running NAT/ALB; **billed hourly if left unattached** | Classic leftover-cost trap — must be checked explicitly in the post-destroy inventory (§10) since an EIP can survive a partial/failed destroy. |

**Budget alerts already configured** (done in account bootstrap, prior to any resource creation):
AWS Budget `pharmacy-platform-monthly`, $20 cap, ACTUAL-cost email notifications at 25%/50%/75%/100%
of cap → $5 / $10 / $15 / $20 thresholds, delivered to the developer's email.

## 4. Is EKS worth the temporary cost vs. a conceptual-only exercise?

**Decision: yes, real minimal/temporary EKS** (confirmed with user) — rationale:

- The whole point of this learning pass is to internalize *real* EKS control-plane/worker-node
  mechanics, ALB Ingress Controller wiring, IRSA, and how a Spring Boot service actually behaves
  under real Kubernetes scheduling/health-check semantics — none of that is learned from reading
  Terraform that's never applied.
- Cost is bounded and small for a short-lived exercise: EKS control plane (~$0.10/hr) + 1–2 small
  nodes, for what should be a few hours of hands-on time, is on the order of a few dollars — well
  within the $20 budget cap, and caught early by the $5/$10 alerts if the estimate is wrong.
- **Node strategy to minimize both cost and operational surface:** prefer a single small **Fargate
  profile** scoped to the 3 deployed Services' namespace over a managed EC2 node group, if Fargate
  pricing/setup proves simpler to tear down cleanly; managed node group with 1 node
  (`t3.small`/`t3.medium`) is the fallback if Fargate has compatibility issues with the ALB
  controller setup. This decision is finalized during Prompt 12B authoring, not here.
- **Mitigation for the "biggest hidden cost" risk (control plane running unnoticed):** the
  create → validate → learn → destroy runbook (§9) is time-boxed to a single sitting, and the
  post-destroy inventory script (§10) is mandatory, not optional.

## 5. Design that avoids NAT Gateway unless explicitly approved

**No NAT Gateway in the default design.** Rationale and approach:

- The only component that strictly needs outbound internet access from a private subnet is EKS
  worker nodes pulling images from ECR and reaching the EKS/STS/CloudWatch APIs — all of which are
  reachable via **VPC Interface Endpoints (PrivateLink)** for `ecr.api`, `ecr.dkr`, `s3` (gateway
  endpoint, free), `sts`, `logs`, and `eks`, instead of routing through a NAT Gateway to the public
  internet.
- **Simplification for this short-lived exercise:** worker nodes (or Fargate pods) run in **public
  subnets** with a restrictive security group (no inbound from the internet, only the ALB's
  security group and intra-cluster traffic allowed) rather than a full private-subnet + NAT/PrivateLink
  topology. This is a deliberate, explicitly-called-out tradeoff (see §6) that avoids NAT costs
  entirely for a temporary learning environment — **not** a recommended production pattern.
- If the user later wants the "correct" private-subnet-with-NAT production topology as a follow-up
  exercise, that requires **explicit approval** per the hard safety rule, and would be scoped as a
  separate, clearly cost-labeled change.

## 6. Public/private subnet tradeoffs

| Option | Pros | Cons | Decision |
|---|---|---|---|
| **A. Public subnets only** (nodes/pods in public subnets, restrictive SGs, ALB in same public subnets) | No NAT Gateway cost; simplest to build/destroy cleanly for a short exercise; still not directly internet-reachable because SGs deny inbound except from ALB | Not a production-appropriate pattern; nodes have public IPs (SG still blocks unwanted traffic, but the pattern itself is not defense-in-depth) | **Chosen for this pass** — cost/simplicity optimized, explicitly documented as non-production. |
| **B. Private subnets + NAT Gateway** | Production-correct isolation; nodes have no public IPs at all | NAT Gateway hourly + per-GB cost, explicitly against default safety rule unless approved | Rejected by default; available as an explicitly-approved follow-up. |
| **C. Private subnets + VPC endpoints only (no NAT)** | Production-correct isolation, no NAT cost | More Terraform complexity (5–6 interface endpoints), more moving pieces to debug in a short exercise, endpoint hourly costs partially offset the NAT savings at this small scale | Considered but deferred — good candidate for the "second pass / second region" follow-up once the basic flow is proven with option A. |

ALB itself always sits in public subnets (it must be internet-facing) regardless of which
node/pod option is chosen; RDS sits in **private subnets** (via a DB subnet group) with a security
group that **only** allows inbound from the EKS node/pod security group on port 3306 — RDS is never
publicly reachable.

## 7. IAM / OIDC least privilege design

- **Human access:** IAM Identity Center (SSO) only, `AdministratorAccess` permission set for this
  sandbox account — acceptable for a personal dedicated sandbox; would be scoped much more narrowly
  in a shared/production account.
- **EKS pod-level AWS access (IRSA — IAM Roles for Service Accounts):** each of the 3 deployed
  services gets its **own** narrowly-scoped IAM role bound to its own Kubernetes ServiceAccount via
  the cluster's OIDC provider — no shared "god role" for all pods:
  - `product-service` role: read access to its one Secrets Manager secret (DB credentials) only.
  - `auth-service` role: read access to its two Secrets Manager secrets (DB credentials + RSA
    keypair) only.
  - `api-gateway` role: no AWS API access needed at all (it only talks to other in-cluster
    services over HTTP) — no IAM role attached, or an empty-permission role if the ALB controller
    requires a ServiceAccount to exist.
  - The **AWS Load Balancer Controller** itself runs under its own dedicated IRSA role with the
    documented upstream minimal policy (ALB/ELBv2, EC2 describe, ACM describe) — this is a
    well-known, publicly documented least-privilege policy, not a custom broad one.
- **Terraform execution identity:** the human SSO `AdministratorAccess` session for this short,
  human-supervised exercise (acceptable for a dedicated personal sandbox with no `-auto-approve`
  anywhere). **Not** used as a pattern for shared/production accounts — a scoped CI OIDC role would
  be required there (that hardening is explicitly out of scope until/unless Prompt 11 wires
  Terraform into CI, which this plan does not do).
- **No static AWS access keys** exist anywhere in this exercise — not for the human (SSO-only) and
  not for any workload (IRSA-only).

## 8. Terraform remote-state design (documented; defaulted safely for this exercise)

- **Design (for future/shared use):** S3 bucket (versioned, SSE-KMS or SSE-S3 encrypted, public
  access blocked) + DynamoDB table for state locking, one workspace per environment.
- **Default for this temporary, single-operator learning exercise:** a dedicated, purpose-built S3
  bucket (e.g. `pharmacy-sandbox-tfstate-<account-id>`) with versioning + encryption + public-access
  block, **created first via a tiny bootstrap Terraform config (or one-off CLI command) before the
  main modules**, so state is still safely durable and recoverable if the local machine is lost —
  but a DynamoDB lock table is **optional/skipped** for this pass since there is only one human
  operator running `apply`/`destroy` sequentially, never concurrently. State bucket itself is
  **not** destroyed as part of the exercise's `terraform destroy` (it's outside the main root
  module) — it will be manually deleted at the very end once all state files confirm empty, to
  avoid a chicken-and-egg destroy-your-own-backend problem.

## 9. Create → validate → learn → destroy runbook (outline)

1. **Create:** `terraform init` → `terraform plan` (review) → `terraform apply` (interactive
   confirmation, never `-auto-approve`) for network → ECR → EKS → RDS → Secrets Manager → ALB
   Ingress, in that dependency order (modular, see Prompt 12B).
2. **Populate:** push the 3 service images to ECR (via existing Dockerfiles), apply Kubernetes
   manifests/Helm values referencing those ECR images, secrets, and the Ingress.
3. **Validate/learn (smoke test):** hit the real ALB DNS name → `api-gateway` → confirm
   `auth-service` login issues a JWT, JWKS is fetchable, gateway validates it, and `product-service`
   read/write works end-to-end against RDS (+ Redis cache pod). Capture command output as evidence
   for the interview-guide writeup.
4. **Learn:** record actual EKS bring-up time, actual cost incurred (checked via Cost Explorer /
   the Budget's `CalculatedSpend`), and any real gotchas hit (this becomes the final interview-guide
   update, step 10 of the overall plan).
5. **Destroy:** before running `terraform destroy`, explicitly re-verify — **account ID, region,
   and Terraform workspace match this exercise** (never destroy blind) — and **explicitly confirm
   the RDS deletion-protection/snapshot choice** (deletion protection disabled, `skip_final_snapshot
   = true` is acceptable *only* because this is confirmed-non-production, throwaway data — this
   must be a conscious, logged decision each time, not a silent default). Then `terraform destroy`
   (interactive confirmation).
6. **Verify:** run the post-destroy inventory command (§10) and confirm zero leftover billable
   resources before considering the exercise closed.

## 10. Resource inventory command (post-destroy leftover check)

Run against the sandbox account/region after every destroy, before considering the exercise closed:

```bash
export AWS_PROFILE=pharmacy-sandbox
export AWS_REGION=us-east-1

echo "--- EKS clusters ---"
aws eks list-clusters --region $AWS_REGION

echo "--- EC2 instances (non-terminated) ---"
aws ec2 describe-instances --region $AWS_REGION \
  --filters "Name=instance-state-name,Values=pending,running,stopping,stopped" \
  --query 'Reservations[].Instances[].[InstanceId,State.Name,Tags]'

echo "--- Load balancers (ALB/ELB) ---"
aws elbv2 describe-load-balancers --region $AWS_REGION --query 'LoadBalancers[].[LoadBalancerArn,DNSName]'

echo "--- RDS instances ---"
aws rds describe-db-instances --region $AWS_REGION --query 'DBInstances[].[DBInstanceIdentifier,DBInstanceStatus]'

echo "--- NAT Gateways ---"
aws ec2 describe-nat-gateways --region $AWS_REGION \
  --filter "Name=state,Values=pending,available,deleting" \
  --query 'NatGateways[].[NatGatewayId,State]'

echo "--- Unattached Elastic IPs ---"
aws ec2 describe-addresses --region $AWS_REGION --query 'Addresses[?AssociationId==`null`]'

echo "--- ECR repositories (informational — low/no cost, not auto-deleted) ---"
aws ecr describe-repositories --region $AWS_REGION --query 'repositories[].repositoryName'

echo "--- Current budget spend ---"
aws budgets describe-budgets --account-id $(aws sts get-caller-identity --query Account --output text) \
  --query 'Budgets[].[BudgetName,CalculatedSpend.ActualSpend]'
```

Any non-empty result for EKS/EC2/ALB/RDS/NAT/EIP after destroy means manual cleanup is required
before the exercise is considered fully closed — per the hard safety rule, these are checked every
time, not just "when something feels wrong."

## 11. Data / security boundaries

- **No production data anywhere** — the RDS instance is seeded only with the same
  Flyway-migration-driven schema and synthetic/test data already used in local integration tests.
  No real customer, prescription, or payment data ever touches this AWS environment.
- **No prescription text, payment details, or PII** flow through CloudWatch Logs (same logging
  redaction rules as local, per repository-wide standards — nothing about being "in AWS" relaxes
  this).
- **Cross-service data ownership rule still applies**: `auth_service` and `pharmacy_product` remain
  separate RDS schemas with separate DB users — no shared tables, matching the local
  one-schema/one-service rule.
- **Secrets boundary:** RSA keypair and DB credentials only ever exist in Secrets Manager + the pod's
  memory at runtime (via IRSA-scoped fetch) — never in Terraform variables committed to git, never
  in plain Kubernetes ConfigMaps/Secrets base64 (which is not encryption), never logged.
- **Network boundary:** RDS security group allows inbound **only** from the EKS
  node/pod security group on 3306 — not from `0.0.0.0/0`, not from the ALB.

## 12. Tags applied to every resource (Prompt 12B requirement, recorded here for approval)

`project=pharmacy-enterprise-platform`, `owner=<developer-identifier>`, `environment=sandbox-temp`,
`expiration=<planned-destroy-date>`, `cost-center=learning-exercise`.

## 13. Explicit approval checklist

Before Prompt 12B (Terraform authoring) begins, the developer confirms:

- [ ] Approve public-subnet-only topology (§6, option A) for this pass — no NAT Gateway.
- [ ] Approve real EKS (not conceptual-only) with the minimal node/Fargate strategy in §4.
- [ ] Approve self-hosted Redis pod in EKS (not ElastiCache) for this pass.
- [ ] Approve RDS with deletion protection disabled + `skip_final_snapshot = true` (non-production,
      throwaway data only).
- [ ] Confirm budget alerts ($5/$10/$15/$20) are live — **done**, verified via
      `aws budgets describe-budgets`.
- [ ] Confirm no MSK / Kafka stays local — **already confirmed**.
- [ ] Confirm willingness to run `terraform apply`/`destroy` interactively (no `-auto-approve`) and
      to run the post-destroy inventory check every time.
