# Prompt 12 — Temporary AWS/Terraform learning phase

This phase is optional and must not block local completion. It creates billable resources. Configure AWS Budgets first, use a dedicated sandbox account when possible, and destroy resources after the exercise.

## Give Copilot these files

- `docs/02-architecture.md`
- `docs/04-technology-versions.md`
- `.github/copilot-instructions.md`

## Prompt 12A — Cost and threat review before code

```text
Do not create Terraform yet. Produce docs/aws-plan-review.md for a minimal temporary AWS learning environment mapping local components to AWS.

Include:
- exact billable components and major hidden-cost risks;
- design that avoids NAT Gateway unless explicitly approved;
- whether EKS is worth the temporary cost versus a conceptual-only exercise;
- Budget alerts at $5, $10, $15 and $20;
- IAM/OIDC least privilege;
- public/private subnet tradeoffs;
- ECR, optional EKS, RDS MySQL, ALB, Secrets Manager, CloudWatch and S3 mapping;
- data/security boundaries;
- create -> validate -> learn -> destroy runbook;
- resource inventory command to check for leftovers.

Assume Kafka stays local and no MSK is created. Wait for explicit approval before producing infrastructure code.
```

## Prompt 12B — Terraform only after approval

```text
Create modular Terraform for only the approved AWS scope under infra/terraform. Use a remote-state design document but default the learning exercise safely. Pin provider constraints and commit .terraform.lock.hcl; never commit state, keys or secrets.

Include tags for project, owner, environment, expiration and cost center. Add validation, formatting, lint/security scanning and plan commands. Prefer OIDC roles instead of static AWS keys. Place database credentials in Secrets Manager and configure security groups narrowly. If EKS is approved, keep node capacity minimal/temporary and use ECR plus ALB controller only for the selected service slice.

Provide scripts/runbooks for plan/apply, smoke validation, destroy and post-destroy inventory. Every apply instruction must be paired with a destroy/verification instruction. Do not auto-apply from CI.
```

## Hard safety rules

- No MSK.
- No permanent EKS environment.
- No NAT Gateway unless the user explicitly accepts its cost.
- No production data.
- No `terraform apply -auto-approve` in default instructions.
- Never destroy a target until account, region, workspace and plan are verified.
- Confirm RDS deletion protection/snapshot choice before destroy.
- Check EKS, EC2, ELB, RDS, NAT, EIP and ECR resources after destroy.

## Study

- IAM identity vs resource policy
- VPC, route table, IGW, NAT and security group
- ECR image flow
- EKS control plane/worker responsibilities
- RDS managed responsibilities
- ALB/Ingress relationship
- Terraform state, plan and drift

