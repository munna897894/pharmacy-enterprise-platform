# Prompt 12 — Temporary AWS/Terraform learning environment

**Read `docs/aws-plan-review.md` first** — it is the approved cost/threat review this
infrastructure implements. This README is the execution runbook.

## Layout

```
infra/terraform/
├── bootstrap/        one-off: creates the S3 remote-state bucket (local state)
├── envs/sandbox/     the actual environment (network, ecr, secrets, rds, eks, alb-controller)
└── modules/          network, ecr, secrets, rds, eks (reusable building blocks)
infra/k8s/sandbox/    plain Kubernetes manifests for the 3 deployed services + redis + ingress
scripts/aws-*.sh      runbook scripts (plan/apply/smoke-test/destroy/post-destroy-check)
```

## Prerequisites

- `terraform` and `aws` CLI installed (`brew install hashicorp/tap/terraform awscli`).
  **If on Apple Silicon:** install a native arm64 terraform binary instead of relying on
  Homebrew's Rosetta-translated one — it is dramatically faster for the AWS provider's large
  schema. `scripts/aws-*.sh` automatically prefer `~/bin/terraform` if present.
- AWS SSO configured: `aws configure sso` → profile `pharmacy-sandbox` (see docs/aws-plan-review.md
  for the account bootstrap steps already completed).
- AWS Budgets alerts already configured at $5/$10/$15/$20 (done once, account-wide).
- `docker`, `kubectl`, `helm` available locally (helm is driven via Terraform's `helm` provider,
  but `kubectl` is used directly by the scripts for app manifests and smoke testing).

## One-time: create the remote-state bucket

```bash
cd infra/terraform/bootstrap
terraform init
terraform apply -var="state_bucket_name=pharmacy-sandbox-tfstate-<your-account-id>" -var="owner=<you>"
```

Note the bucket name from the output — every other command below needs it as `STATE_BUCKET`.

## Validate (safe, offline-ish, run anytime)

```bash
./scripts/aws-validate.sh
```

## Plan → Apply → Smoke test → Destroy → Verify

Every step below is paired with its teardown/verification counterpart. Never run apply without
knowing you will run destroy + the post-destroy check afterward.

```bash
cp infra/terraform/envs/sandbox/terraform.tfvars.example infra/terraform/envs/sandbox/terraform.tfvars
# edit terraform.tfvars: owner, expiration date, etc.

export STATE_BUCKET=pharmacy-sandbox-tfstate-<your-account-id>
./scripts/aws-plan.sh        # review the plan — no changes made yet
./scripts/aws-apply.sh       # interactive confirmation required, builds/pushes images, applies k8s manifests
./scripts/aws-smoke-test.sh  # hits the real ALB: login, JWKS, gateway-validated product read

# ... when done learning ...
./scripts/aws-destroy.sh              # interactive confirmation required, tears everything down
./scripts/aws-post-destroy-check.sh   # MANDATORY — confirms zero leftover billable resources
```

## Hard safety rules (from prompts/12-aws-terraform.md — do not relax these)

- No MSK. Kafka stays local for this exercise.
- No permanent EKS — this cluster is temporary; destroy promptly after the smoke test.
- No NAT Gateway (public-subnet-only design, see docs/aws-plan-review.md section 5/6).
- No production data — RDS holds only Flyway-migrated schema + synthetic test data.
- No `terraform apply -auto-approve` anywhere in these scripts.
- `aws-destroy.sh` requires typing an exact confirmation phrase and re-verifies caller identity
  before doing anything.
- `aws-post-destroy-check.sh` is mandatory after every destroy — checks EKS/EC2/ELB/RDS/NAT/EIP/ECR.
