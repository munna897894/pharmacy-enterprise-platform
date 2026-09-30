# Terraform bootstrap — remote state bucket

This is a **one-off** configuration, applied exactly once (per AWS account),
before the main `envs/sandbox` environment is ever initialized. It creates
the S3 bucket that `envs/sandbox` uses as its remote state backend.

It intentionally uses **local state** (a `terraform.tfstate` file on your
machine) because it creates the very bucket the main environment would
otherwise depend on — a chicken-and-egg problem. Keep that local state file
safe; do not delete it casually, and do not re-run `apply` here unless you
are intentionally recreating the state bucket.

## Usage

```bash
export AWS_PROFILE=pharmacy-sandbox
export AWS_REGION=us-east-1
export EXPECTED_AWS_ACCOUNT_ID=<independently-verified-12-digit-account-id>
export STATE_BUCKET=pharmacy-sandbox-tfstate-<account-id>
export OWNER=<your-name>
./scripts/aws-bootstrap.sh
```

The script verifies caller account and region, requires the bucket name to contain the expected
account ID, reviews a saved plan, and requires an exact confirmation before applying. It never
uses `-auto-approve`. Pass `STATE_BUCKET` to the sandbox scripts afterward.

## Cleanup (only at the very end of the whole exercise)

Do **not** delete this bucket until `envs/sandbox`'s state confirms fully
destroyed (empty) and the post-destroy inventory script shows no leftover
resources. Then:

```bash
terraform -chdir=infra/terraform/bootstrap destroy \
  -var="aws_region=us-east-1" \
  -var="state_bucket_name=pharmacy-sandbox-tfstate-<account-id>" \
  -var="owner=<your-name>"
```

The bucket has `prevent_destroy = true` in its lifecycle block as a safety
guard — you must remove that line (or use `terraform state rm` + manual S3
console deletion) once you are certain it's safe to delete.

## State history retention

Versioning is enabled so state can be recovered, but every version is a full copy of the state
file. A lifecycle rule expires noncurrent versions after `state_history_retention_days` (default 7,
maximum 90) so history does not accumulate indefinitely.

The sandbox environment no longer writes credentials into state (see
`infra/terraform/modules/secrets/main.tf`), but state written *before* that change still contains
generated database passwords and the JWT private key. When retiring the exercise, run
`scripts/aws-prune-state-history.sh` after a successful destroy to permanently delete all versions
of the state object. That is irreversible and refuses to run while state still tracks resources.

The bucket itself has `prevent_destroy = true`; remove it manually only at final cleanup, after the
state is empty and the history has been pruned.
