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
cd infra/terraform/bootstrap
terraform init
terraform plan \
  -var="state_bucket_name=pharmacy-sandbox-tfstate-<your-account-id>" \
  -var="owner=<your-name>"
terraform apply \
  -var="state_bucket_name=pharmacy-sandbox-tfstate-<your-account-id>" \
  -var="owner=<your-name>"
```

Note the `state_bucket_name` output — you'll pass it to `envs/sandbox`'s
`terraform init -backend-config="bucket=<name>"`.

## Cleanup (only at the very end of the whole exercise)

Do **not** delete this bucket until `envs/sandbox`'s state confirms fully
destroyed (empty) and the post-destroy inventory script shows no leftover
resources. Then:

```bash
terraform destroy \
  -var="state_bucket_name=pharmacy-sandbox-tfstate-<your-account-id>" \
  -var="owner=<your-name>"
```

The bucket has `prevent_destroy = true` in its lifecycle block as a safety
guard — you must remove that line (or use `terraform state rm` + manual S3
console deletion) once you are certain it's safe to delete.
