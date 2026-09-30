# Temporary full-fleet AWS sandbox

This non-production environment is an independent, ephemeral copy of all 12 JVM workloads. It is
intended for synthetic-data sessions only and must be destroyed afterward. Read
[`docs/aws-plan-review.md`](../../docs/aws-plan-review.md) before provisioning
and use the [AWS deployment diagram](../../docs/architecture-cloud.md) as the
topology reference.

## Scope

- One EKS cluster with two `t4g.large` (arm64) public-subnet worker nodes; worker security groups
  do not accept direct internet ingress. EKS's public API accepts traffic only from the configured
  operator `/32`.
- One private RDS MySQL instance in isolated DB subnets, with ten schemas and ten distinct
  per-service users. RDS is reachable only from the EKS cluster security group.
- Twelve immutable-tag ECR repositories, short-retention control-plane logs, Secrets Manager
  credentials, RSA JWT keys and narrowly scoped IRSA roles.
- One single-broker Kafka StatefulSet in EKS (session-lifetime gp3 volume), exposed only by ClusterIP,
  with explicit domain/retry/DLT topics created by an idempotent Job, plus in-cluster Redis. No MSK, NAT Gateway, or production data.
- Gateway exposure defaults to `port-forward`: **no public load balancer**; the gateway is reached
  with `kubectl port-forward` over the authenticated EKS API. The opt-in `alb-https` mode creates one
  HTTPS-only ALB Ingress (ACM certificate, TLS 1.2+/1.3 policy, no HTTP listener or redirect) that
  routes only to `api-gateway` and admits only explicit `/32`s. Plain-HTTP public exposure is not
  supported: login passwords and JWTs must never cross the internet in plaintext, synthetic data or
  not.

`infra/k8s/sandbox/07-jvm-service-template.yaml` is rendered for the eight database-backed JVM
services beyond auth/product. `external-mock-service` has its own DB-free manifest. The apply
script builds and pushes all twelve images from the repository Dockerfiles.

## Prerequisites

- Terraform 1.9+, AWS CLI, Docker, `kubectl`, `helm`, Python 3 and `curl`.
- AWS IAM Identity Center/SSO profile `pharmacy-sandbox`.
- An independently confirmed 12-digit sandbox account ID. Never derive the expected ID from the
  currently selected profile; set it explicitly for every AWS script invocation.
- AWS Budgets alerts and access to the short-lived session's operator public IPv4 address.
  Budgets notify only; they never cap or stop spend. Run `./scripts/aws-cost-estimate.sh <hours>`
  for the offline time-boxed estimate (roughly $0.29/hour, about $1-$2 for a short attended
  session, and past $20 after roughly three days of continuous runtime). Destroying the
  environment is the only reliable cost control.
- At least two availability zones in `us-east-1`.

## One-time state backend

The bootstrap stack creates a versioned, encrypted, public-access-blocked S3 state bucket. It uses
local state itself, so protect the bootstrap state file. Only remove the backend after the sandbox
state is empty and the post-destroy inventory is clear.

```bash
export AWS_PROFILE=pharmacy-sandbox
export AWS_REGION=us-east-1
export EXPECTED_AWS_ACCOUNT_ID='<independently-verified-12-digit-account-id>'
export STATE_BUCKET='pharmacy-sandbox-tfstate-<account-id>'
export OWNER='<you>'
./scripts/aws-bootstrap.sh
```

## Create, learn and destroy

Copy `envs/sandbox/terraform.tfvars.example` to the gitignored `envs/sandbox/terraform.tfvars`.
Set `owner` and an upcoming `expiration`. Set `OPERATOR_CIDR` in the shell to your current public
IPv4 address in `/32` notation; it is required and has no default. The runbook enforces two
`t4g.large` worker nodes even if a stale ignored tfvars file contains the former three-service
sizing.

The gateway is private by default (`GATEWAY_EXPOSURE=port-forward`). To opt into the HTTPS ALB you
must already have an ISSUED ACM certificate in `us-east-1` covering a hostname you control, then set
`GATEWAY_EXPOSURE=alb-https`, `GATEWAY_CERTIFICATE_ARN`, `GATEWAY_HOSTNAME` and `ALB_INGRESS_CIDRS`
(comma-separated `/32`s). `aws-plan.sh` verifies the certificate status, account/region, validity
and hostname coverage and fails closed on any problem; Terraform rejects partial or mismatched
settings. After apply, point a DNS CNAME for the hostname at the ALB. Destroy reads the mode from
state; an `alb-https` stack also needs `ALB_INGRESS_CIDRS` set.

**Do not expose this environment to the open internet.** `auth-service` registration currently
accepts caller-supplied roles, so anyone who can reach the gateway can create a privileged account.
Keep the default port-forward mode, or a narrow HTTPS allowlist, until registration is gated. See
`docs/aws-plan-review.md`.

```bash
export AWS_PROFILE=pharmacy-sandbox
export AWS_REGION=us-east-1
export EXPECTED_AWS_ACCOUNT_ID='<independently-verified-12-digit-account-id>'
export STATE_BUCKET='pharmacy-sandbox-tfstate-<account-id>'
export NAME_PREFIX=pharmacy-sbx
export OPERATOR_CIDR='<your-current-public-ip>/32'
# Optional HTTPS ALB instead of the default port-forward:
# export GATEWAY_EXPOSURE=alb-https
# export GATEWAY_CERTIFICATE_ARN='arn:aws:acm:us-east-1:<account-id>:certificate/<id>'
# export GATEWAY_HOSTNAME='api.sandbox.<your-domain>'
# export ALB_INGRESS_CIDRS='<your-current-public-ip>/32'

./scripts/aws-validate.sh
./scripts/aws-plan.sh
# Inspect the saved plan with: terraform -chdir=infra/terraform/envs/sandbox show aws-full-fleet.tfplan
./scripts/aws-apply.sh
./scripts/aws-smoke-test.sh   # port-forward by default; pass GATEWAY_EXPOSURE/GATEWAY_HOSTNAME for alb-https
# Newman runs by default through the private tunnel; RUN_NEWMAN=0 skips it.

# At the end of the session:
./scripts/aws-destroy.sh
./scripts/aws-post-destroy-check.sh

# Only when retiring the exercise for good (irreversible):
# STATE_BUCKET=<bucket> ./scripts/aws-prune-state-history.sh
```

Terraform generates no credential at all. Every value a Terraform resource produces is stored in
**plaintext** in state (`sensitive` only hides CLI output), and the state bucket is versioned, so
such values would outlive `terraform destroy` in noncurrent object versions. The RDS master password
is therefore created by AWS via `manage_master_user_password`, and the per-service database
passwords and JWT keypair are generated after apply by `scripts/aws-seed-secrets.sh`; Terraform only
creates empty Secrets Manager entries. The `random` and `tls` providers are intentionally not
declared, and validation fails if credential-generating resources reappear.

State still holds non-secret but useful infrastructure detail, so the backend stays encrypted and
private; saved plans are mode-restricted and removed by the apply script. Never upload, commit or
share them. If this environment was applied before credentials were moved out of state, prune the
history with `scripts/aws-prune-state-history.sh` when retiring the exercise; a lifecycle rule also
expires noncurrent state versions after `state_history_retention_days` (default 7). Root outputs expose no credentials, and apply pipes `terraform output -json`
through `scripts/aws-filter-outputs.py` so only identifiers, endpoints, ARNs and tags reach disk;
the filter fails closed if an allowlisted output is ever marked sensitive. Credentials are never
passed via `argv` or environment variables such as `MYSQL_PWD`. Scripts verify the expected account, region, default Terraform workspace and
Kubernetes context; apply and destroy require exact interactive confirmation. No script uses
`-auto-approve`.

The apply script creates the ten schemas and users through a short-lived IRSA-scoped Kubernetes Job.
Database secrets are fetched directly to memory-backed pod storage and never printed. The job is
deleted after completion. Worker and database networking is private from unsolicited Internet
ingress, but worker nodes intentionally receive public IPs for this no-NAT cost model. This is not
a production network design.

Destroying RDS skips the final snapshot and removes Secrets Manager values immediately. Confirm
there is no data worth retaining. Destroy the application namespace first so that, in `alb-https`
mode, the ALB controller deletes its load balancer; then destroy Terraform resources and run the
post-destroy check.

## Observability

`aws-apply.sh` installs the private observability stack (`infra/helm/observability` with its EKS
profile and `infra/k8s/sandbox/observability-values-aws.yaml`) into `pharmacy-observability`;
`aws-destroy.sh` removes it before Terraform. Access Grafana with
`kubectl -n pharmacy-observability port-forward --address 127.0.0.1 svc/pharmacy-observability-grafana 3001:80`.
See `docs/aws-plan-review.md` for capacity and credential handling.

## Persistent storage

EKS ships no working default StorageClass: the legacy `gp2` class uses the in-tree
provisioner removed in Kubernetes 1.31, so PVCs bound to it never provision. This
environment installs the `aws-ebs-csi-driver` addon with a dedicated IRSA role scoped to
`kube-system:ebs-csi-controller-sa`, and defines an encrypted `gp3` default class
(`infra/k8s/sandbox/09-storage.yaml`) with `reclaimPolicy: Delete` and
`WaitForFirstConsumer`. Kafka uses an 8 GiB claim from it.

CSI-provisioned volumes are created at runtime and are invisible to Terraform, so
`scripts/aws-destroy.sh` releases PVCs before the cluster is torn down and
`scripts/aws-post-destroy-check.sh` fails if any volume survives. See
`docs/aws-plan-review.md` for the node-capacity arithmetic behind `t4g.large`.

## Worker CPU architecture

`var.node_architecture` (default `arm64`, Graviton `t4g.large`) drives the node
`ami_type`, is validated against `node_instance_type` by a Terraform precondition, and is
exported as an output that `scripts/aws-apply.sh` uses to build images with an explicit
`--platform`. The pushed image manifest and the live node architecture are both verified
before deployment, so an image built for the wrong architecture cannot silently reach the
cluster and crash-loop with `exec format error`. Override with
`NODE_ARCH=amd64 NODE_INSTANCE_TYPE=t3.large ./scripts/aws-plan.sh`; see
`docs/aws-plan-review.md`.

## Kubernetes version

The EKS control plane is pinned to a **standard-support** version (currently `1.35`).
Versions past end-of-standard-support roll into extended support, which is billed at a
much higher per-cluster-hour rate and is enabled by default, so a stale pin raises cost
silently. Terraform validation rejects known extended-support versions, the cluster sets
`upgrade_policy { support_type = "STANDARD" }`, and `scripts/aws-check-eks-version.sh`
re-checks the live support status before every plan. The lifecycle table, the date it was
last verified and the upgrade policy live in `docs/aws-plan-review.md`.

## Terraform layout

```text
bootstrap/       encrypted S3 state bucket (one-time, local state)
envs/sandbox/    full temporary AWS environment
modules/         network, ECR, EKS/IRSA, RDS and Secrets Manager
```

Kafka, Redis and Kubernetes workload manifests live in `infra/k8s/sandbox/`. The AWS Load Balancer
Controller is installed by `aws-apply.sh` only in `alb-https` mode and only after EKS is ready; Terraform does not configure
Kubernetes providers against a cluster it is creating in the same plan.
