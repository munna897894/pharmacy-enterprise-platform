# 12 — AWS and Terraform (Prompt 12)

## Summary

Prompt 12 was **fully executed for real** in an isolated AWS sandbox account, end to
end: cost/threat review → approval gate → Terraform authoring → `terraform apply`
(real billable resources) → Docker builds pushed to ECR → Kubernetes manifests
applied to a real EKS cluster → full smoke test through a real ALB (register →
login → JWT → JWKS → authenticated product read against real RDS data) →
`terraform destroy` → mandatory post-destroy inventory check confirming **zero
leftover billable resources**.

This is unusual for a study repo: most "AWS chapters" in learning projects stay
conceptual. Here the cluster genuinely existed, cost real (small) money for
roughly an hour, and several **real bugs** were caught and fixed only because the
code was deployed against real infrastructure instead of docker-compose. Those
bugs are the most valuable interview material in this guide.

## What actually got built

```text
Terraform (infra/terraform/)
├── bootstrap/            one-off S3 remote-state bucket (versioned, SSE-KMS, no public access)
└── envs/sandbox/         root module wiring every module below + K8s namespace/SA/Helm release
    └── modules/
        ├── network/      VPC, 2 public + 2 private "db" subnets, IGW, route tables — NO NAT Gateway
        ├── ecr/          3 repos (auth-service, product-service, api-gateway) + lifecycle policies
        ├── secrets/       Secrets Manager: auth_db creds, product_db creds, JWT RSA keypair
        ├── rds/           single db.t4g.micro MySQL 8.0, private subnets, deletion_protection=false
        └── eks/           EKS 1.32 cluster + 1-node t3.small managed node group, OIDC, 3 IRSA roles,
                           AWS Load Balancer Controller (Helm)

infra/k8s/sandbox/        Deployment/Service/Ingress manifests for redis, auth-service,
                          product-service, api-gateway — placeholder tokens substituted
                          from `terraform output` at apply time (no secrets committed)

scripts/                  aws-validate.sh / aws-plan.sh / aws-apply.sh / aws-smoke-test.sh /
                          aws-destroy.sh / aws-post-destroy-check.sh
```

Design decisions (see `docs/aws-plan-review.md` for the full reasoning), all
confirmed with the user before spending a dollar:

- **No NAT Gateway** — nodes and the RDS subnet group live in subnets that route
  through the Internet Gateway directly (public-subnet-only design) specifically
  to avoid NAT's per-hour + per-GB cost, which is the #1 silent-cost trap in
  small AWS exercises.
- **Real EKS, not "conceptual only"** — the previous version of this guide (see
  git history) was aspirational; this run proves the whole path works, including
  the parts that never show up in a diagram (IRSA wiring, ALB controller IAM
  policy, Flyway-against-RDS, JWKS-over-ALB).
- **Self-hosted Redis** (plain Deployment/Service in-cluster) instead of
  ElastiCache — cheaper and sufficient for a short-lived learning exercise.
- **RDS with `deletion_protection=false`, `skip_final_snapshot=true`** — explicit,
  reviewed tradeoff because there is no production data, only synthetic
  Flyway-seeded rows.
- **No MSK** — Kafka intentionally stayed out of scope for this cloud exercise.

## Real bugs found only because this was deployed for real

These are the highest-value interview material from this exercise — each is a
class of bug that never surfaces against `docker-compose` with a forgiving local
setup, but breaks immediately against real AWS networking/managed services.

1. **Spring Boot 3 config-key rename bug (product-service).**
   `application.yml` had `spring.redis.host/port` — the **Spring Boot 2.x** key.
   Spring Boot 3.x renamed it to `spring.data.redis.*`. The old key is silently
   ignored (no startup error!), so Lettuce fell back to `localhost:6379` and the
   pod crash-looped with `RedisConnectionException: Connection refused`. Fixed
   by moving the block under `spring.data.redis`. **Lesson:** renamed
   Spring Boot properties fail *silently* — there's no validation error, just a
   wrong default at runtime. Always double check property paths after a major
   Spring Boot version bump.

2. **Hibernate 6 + MySQL `UUID` binary-vs-string mismatch (auth-service).**
   `User.id` and `RefreshToken.id/userId` were declared `private UUID id` with
   `@Column(columnDefinition = "CHAR(36)")`. The Flyway migration created the
   column as `CHAR(36)` (a normal UUID *string*). But Hibernate 6's default JDBC
   binding strategy for `java.util.UUID` on this dialect is **binary** (16 raw
   bytes), not string — `columnDefinition` only affects DDL generation, not how
   Hibernate marshals parameters at insert time. Every insert failed with MySQL's
   `Incorrect string value: '\xC6%\xD0k\xC1\xDB...' for column 'id'` (raw binary
   bytes aren't valid UTF-8). Fixed with `@JdbcTypeCode(SqlTypes.CHAR)` on every
   UUID-typed column, forcing Hibernate to bind the string representation.
   **Lesson:** `columnDefinition` is cosmetic for schema generation; the actual
   wire-level type binding is controlled separately (`@JdbcTypeCode` /
   `hibernate.type.preferred_uuid_jdbc_type`). This class of bug is invisible in
   H2/Testcontainers tests that don't exercise the exact dialect+column combo.

3. **Missing `iss` (issuer) claim breaks cross-service JWT validation.**
   `auth-service`'s `JwtProvider` issued RS256 tokens with no `issuer`/`audience`
   claims. `product-service`'s resource-server config only used `jwk-set-uri`
   (no issuer check) so it worked fine. But `api-gateway`'s config set BOTH
   `issuer-uri` and `jwk-set-uri`, which makes Spring Security add a
   `JwtIssuerValidator` that checks the token's `iss` claim against
   `issuer-uri` — and failed for every token (no `iss` claim present), returning
   a bodyless `401`. Fixed by adding `.issuer(issuer).audience().add(audience)`
   to both `generateAccessToken`/`generateRefreshToken`, with `issuer`/`audience`
   now configurable via `JWT_ISSUER`/`JWT_AUDIENCE` env vars. **Lesson:** when
   two resource servers validate the same JWT differently (one checks issuer,
   one doesn't), a token can "work" against one service and be silently
   rejected by another — always test the token against every consumer, not just
   one.

4. **Terraform security-group design didn't match how EKS actually attaches
   ENIs.** `modules/network` created a custom `eks_nodes` security group and
   RDS's SG only allowed inbound 3306 from it. But an EKS managed node group
   *without* a custom launch template attaches the **cluster's auto-created
   security group** to node primary ENIs, not any custom SG referenced in
   `vpc_config.security_group_ids` (that field only affects the control-plane
   ENIs). Pods could not reach RDS at all — connections just hung until the
   client's `--connect-timeout` fired. Fixed by adding a second RDS ingress rule
   allowing 3306 from the VPC CIDR block (`var.vpc_cidr`), which is standard
   practice for intra-VPC-only access anyway. **Lesson:** don't assume a
   security group you attach to a `aws_eks_node_group` at plan-time is actually
   what traffic uses at runtime — verify with a real pod, not just `terraform
   plan`.

5. **`tls_private_key` default PEM format (PKCS1) didn't match what the Java
   code expected (PKCS8).** Terraform's `tls_private_key.private_key_pem`
   emits `-----BEGIN RSA PRIVATE KEY-----` (PKCS1) by default, but
   `JwtProvider.loadPrivateKey` strips only the PKCS8 header/footer
   (`-----BEGIN PRIVATE KEY-----`) before Base64-decoding into a
   `PKCS8EncodedKeySpec`. This silently produced garbage bytes that failed to
   parse as a valid RSA key at container startup (`Constructor threw
   exception`). Fixed by switching the secret to
   `tls_private_key.jwt.private_key_pem_pkcs8` (an attribute the `hashicorp/tls`
   provider added specifically for this mismatch). **Lesson:** PKCS1 vs PKCS8 is
   a classic PEM gotcha — always check which the consuming language/library
   expects before wiring key material through Terraform.

6. **`amazon/aws-cli:2.17.0` doesn't have `python3` on PATH** (only `python`,
   i.e. Python 2). The init-container script that parses the Secrets Manager
   JSON response used `python3 -c "..."` and crash-looped with
   `/bin/sh: python3: command not found`. Fixed by using `python` — the simple
   `json.load`/`.write()` calls happened to be Python-2/3 compatible as written.
   **Lesson:** don't assume "AWS's own CLI image" ships a modern Python runtime
   for general scripting use — it bundles just enough Python for the CLI itself,
   under the hood, not necessarily exposed the way you'd expect.

7. **Cold-start JVM timing vs. Kubernetes probe defaults on a small node.** A
   `t3.small` running 3 JVMs (auth-service, product-service, api-gateway) plus
   Redis is CPU/memory constrained; Spring Boot cold starts (Flyway + Hibernate +
   JPA repo scanning) took 60-70s, right at the edge of the original
   `livenessProbe.initialDelaySeconds: 30, periodSeconds: 15, failureThreshold: 3`
   window (30 + 3×15 = 75s). Pods were killed by the liveness probe *just* as they
   finished booting, causing an infinite crash-loop that looked like an
   application bug but was purely a probe-timing bug. Fixed by raising
   `initialDelaySeconds`/`failureThreshold` on both readiness and liveness probes
   across all 3 services. **Lesson:** on genuinely small/cheap nodes, default
   probe timings tuned for a laptop or a beefy CI runner are too aggressive —
   size probe windows to the actual node class, not to how fast the JVM starts
   on your dev machine.

8. **Single small node + rolling updates = scheduling deadlock.** With exactly
   one `t3.small` node and 4 running pods (redis + 3 services), a rolling update
   that needs the old pod *and* the new pod briefly alive at once (default
   `maxSurge`/`maxUnavailable`) has nowhere to schedule the new pod —
   `0/1 nodes are available: 1 Insufficient memory`. Recovered by manually
   scaling the old `ReplicaSet` to 0 before the new one could schedule. In a real
   environment this is solved with more nodes/headroom or
   `maxSurge: 0`/`maxUnavailable: 1` strategy tuning; for a short-lived, 1-node
   sandbox, manual intervention was the pragmatic choice. **Lesson:** a
   single-node cluster is fine for smoke testing but doesn't have room for
   zero-downtime rolling updates — that tradeoff is fine for a temporary
   exercise, but would be a real production gap.

9. **Wrong gateway route path in ad-hoc smoke testing.** The actual
   `GatewayConfig` route for reads is `/api/v1/medications/**`, not
   `/api/v1/products` — a reminder that the smoke-test script/manual curl
   commands must match the real `RouteLocator` definitions, not an assumed REST
   convention.

## Real infrastructure timings (this run, `us-east-1`, EKS 1.32)

| Step | Observed time |
|---|---|
| S3 remote-state bucket create | seconds |
| `terraform plan` (51 resources, native arm64 binary) | ~8s |
| `terraform apply` (network+ECR+secrets+RDS+EKS, first full run) | several minutes; EKS control plane ~7 min, node group ~2 min |
| EKS node-group AMI deprecation recovery (targeted destroy + full re-apply at bumped k8s version) | ~10-12 min total (one-time, avoidable if starting on a current version) |
| `docker build --platform linux/amd64` (auth-service, cross-arch under QEMU) | ~5 min |
| `docker build --platform linux/amd64` (product-service + api-gateway, native-ish/cached) | ~5 min combined |
| Full `terraform destroy` (47 resources) | EKS node group ~9m12s, EKS cluster ~1m12s, everything else seconds — **~11 min total** |
| Post-destroy inventory check | seconds, confirmed zero leftovers |

## Other real, non-bug lessons

- **AWS Budgets CLI shorthand quirk:** `--notifications-with-subscribers` needs
  ALL notification+subscriber objects combined into **one** JSON array file —
  multiple separate `file://` args are not supported the way you'd expect.
- **IAM Identity Center's home region is fixed at creation and independent of
  your default resource region.** This account's Identity Center is in
  `us-east-2` while all real infrastructure is in `us-east-1` — don't assume
  they must match; the SSO region and the resource region are two separate
  settings (`aws configure sso` asks for the SSO region explicitly).
- **EKS in-place upgrades only support one minor version at a time.** Jumping
  1.30 → 1.32 in a single `terraform apply` fails with
  `InvalidParameterException: Unsupported Kubernetes minor version update`. For
  an empty/fresh cluster with no workloads yet, a targeted destroy
  (`terraform destroy -target=module.eks.aws_eks_cluster.this`) and a clean
  re-apply at the target version is faster than sequential single-step upgrades.
- **Homebrew's `terraform` package was pulled from `homebrew-core`** after
  HashiCorp's license change — you now need
  `brew tap hashicorp/tap && brew install hashicorp/tap/terraform`.
- **Apple-Silicon + Intel-only Homebrew = Rosetta emulation trap.** If your
  Homebrew install path is `/usr/local` (Intel) rather than `/opt/homebrew`
  (ARM), every installed binary runs translated — fine for small tools, but
  catastrophic for `terraform validate`/`plan` against the huge AWS provider
  schema (minutes to seemingly-hung). Fix: download the native
  `darwin_arm64` release directly from HashiCorp and put it first on `PATH`.
- **`security_group_ids` in `aws_eks_cluster.vpc_config` only affects
  control-plane ENIs, not node/pod traffic** (see bug #4 above) — a subtlety
  that's easy to miss reading the Terraform docs casually.
- **RDS in private subnets has no path for local `psql`/`mysql` schema setup.**
  Since `publicly_accessible = false`, per-service schema/user creation
  (`CREATE DATABASE ...; CREATE USER ...`) had to run from *inside* the VPC —
  solved here with a short-lived `kubectl run mysql-client --image=mysql:8`
  pod that used the RDS master credentials pulled straight from
  `terraform output`, then was deleted immediately after.

## Common interview Q&A

**Q: Why avoid a NAT Gateway in a short-lived learning exercise?**
A: NAT Gateway bills per-hour *and* per-GB processed regardless of whether it's
truly needed — for a sandbox that mostly needs outbound package/image pulls, a
public-subnet design (or VPC endpoints for the specific AWS services needed)
avoids that ongoing cost entirely, at the cost of nodes having public IPs (an
acceptable tradeoff for a temporary, non-production exercise with a tight
security group).

**Q: Why did the same JWT work against one service but not another?**
A: Because the two resource servers validated different claim sets — one only
checked the signature via JWKS, the other also validated the `iss` claim via
`issuer-uri`. A token missing an expected claim can pass validation in a
"looser" resource server config and fail in a "stricter" one. Always confirm
every consuming service's actual validator configuration, not just one.

**Q: Why does `columnDefinition = "CHAR(36)"` not guarantee a UUID column binds
as a string at runtime?**
A: `columnDefinition` is a DDL-generation hint for schema tooling (Hibernate's
auto-DDL or documentation for Flyway authors) — it does not control how
Hibernate's JDBC type descriptors marshal a Java type to a JDBC parameter at
runtime. Those are two separate concerns; the actual wire format for `UUID` is
controlled by the dialect's default JDBC type or an explicit
`@JdbcTypeCode`/converter override.

**Q: How would you avoid the "one node can't do a rolling update" problem in a
real (non-sandbox) cluster?**
A: Run enough nodes/headroom for at least one extra pod's worth of
CPU/memory, or explicitly set `maxSurge: 0, maxUnavailable: 1` so Kubernetes
kills the old pod before scheduling the new one (accepting a brief gap instead
of needing double capacity) — appropriate for a small, cost-constrained
cluster, at the cost of a short window of reduced availability during
deploys.

**Q: What's the mandatory safety checklist before/after destroying real cloud
infrastructure in an exercise like this?**
A: Before: re-verify account/region/workspace and get explicit typed
confirmation (not `-auto-approve`) plus an explicit RDS
no-final-snapshot acknowledgment. After: run a scripted, multi-service
inventory check (EKS clusters, EC2 instances, load balancers, RDS instances,
NAT gateways, unattached Elastic IPs) and confirm every list is empty before
considering the exercise closed.

## Trace-through (what actually happened, in order)

1. Cost/threat review written and approved (`docs/aws-plan-review.md`) before any
   `apply`.
2. `infra/terraform/bootstrap` created the S3 remote-state bucket.
3. `infra/terraform/envs/sandbox` (network → ECR → secrets → RDS → EKS modules,
   plus K8s namespace/ServiceAccount/Helm resources) was planned then applied —
   hit and recovered from a security-group port-range bug and an EKS node-group
   AMI-deprecation issue mid-apply.
4. Docker images for auth-service, product-service, api-gateway built
   `--platform linux/amd64` (matching the `t3.small`/x86_64 node group) and
   pushed to the 3 ECR repos.
5. RDS schemas/users created from inside the cluster via a short-lived
   `mysql:8` pod using real Secrets Manager-issued credentials, then deleted.
6. Kubernetes manifests rendered (placeholder substitution from
   `terraform output`) and applied in order: redis → auth-service →
   product-service → api-gateway → ingress.
7. Hit and fixed, in order: the Redis config-key bug, the Hibernate UUID/CHAR
   bug, the missing-issuer-claim bug, and probe-timing/node-capacity issues —
   each one caught only because the code ran against real MySQL/real EKS
   scheduling constraints, not docker-compose.
8. Full smoke test passed through the real ALB hostname: gateway health, JWKS,
   register, login, and an authenticated `GET /api/v1/medications` read backed
   by real RDS data.
9. `terraform destroy` (47 resources, ~11 minutes, EKS teardown dominates) plus
   `kubectl delete` of the namespace/ingress first so the ALB controller could
   clean up its own ALB/target groups before the VPC disappeared.
10. Post-destroy inventory check confirmed zero leftover EKS/EC2/ELB/RDS/NAT/EIP
    resources; ECR repos and Secrets Manager entries were also gone since they
    were Terraform-tracked and destroyed in the same run.
