#!/usr/bin/env bash
# Populates the empty Secrets Manager containers created by Terraform.
#
# Credentials are generated HERE rather than by Terraform on purpose: any value
# produced by a Terraform resource is written in plaintext to the state file,
# and the state bucket is versioned, so those values would remain readable in
# noncurrent object versions long after `terraform destroy`. Nothing written by
# this script ever reaches Terraform state.
#
# Values are passed to the AWS CLI via file:// references, never as command-line
# arguments, so they do not appear in the process table. Files live in a
# private temporary directory removed on exit.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=scripts/aws-common.sh
source "$ROOT_DIR/scripts/aws-common.sh"

OUTPUTS_FILE="${1:?usage: aws-seed-secrets.sh <filtered-outputs.json>}"

aws_sandbox_guard

umask 077
WORK_DIR="$(mktemp -d)"
cleanup() { rm -rf "$WORK_DIR"; }
trap cleanup EXIT

# Never echo a generated value; only the secret's name is ever printed.
put_secret() {
  local secret_arn="$1"
  local value_file="$2"
  aws secretsmanager put-secret-value \
    --region "$AWS_REGION" --profile "$AWS_PROFILE" \
    --secret-id "$secret_arn" \
    --secret-string "file://${value_file}" >/dev/null
}

echo "== Seeding per-service database credentials =="
# Alphanumeric only: these values are interpolated into CREATE USER statements
# by the db-bootstrap Job, and the Job rejects anything else.
SERVICE_ARNS=$(python3 - "$OUTPUTS_FILE" <<'PY'
import json
import pathlib
import sys

data = json.loads(pathlib.Path(sys.argv[1]).read_text())
for service, arn in sorted(data["database_secret_arns"]["value"].items()):
    print(f"{service}\t{arn}")
PY
)

while IFS=$'\t' read -r service arn; do
  [[ -z "$service" ]] && continue
  password_file="$WORK_DIR/${service}.pw"
  aws secretsmanager get-random-password \
    --region "$AWS_REGION" --profile "$AWS_PROFILE" \
    --password-length 32 \
    --exclude-punctuation \
    --require-each-included-type \
    --query RandomPassword --output text >"$password_file"
  # Strip the trailing newline the CLI adds; it would become part of the value.
  printf '%s' "$(cat "$password_file")" >"${password_file}.trimmed"
  mv "${password_file}.trimmed" "$password_file"
  put_secret "$arn" "$password_file"
  rm -f "$password_file"
  echo "  seeded ${service} database credential"
done <<<"$SERVICE_ARNS"

echo "== Generating the auth-service JWT signing keypair =="
JWT_PRIVATE_ARN=$(python3 - "$OUTPUTS_FILE" <<'PY'
import json
import pathlib
import sys
print(json.loads(pathlib.Path(sys.argv[1]).read_text())["jwt_keypair_secret_arn"]["value"])
PY
)
JWT_PUBLIC_ARN=$(python3 - "$OUTPUTS_FILE" <<'PY'
import json
import pathlib
import sys
print(json.loads(pathlib.Path(sys.argv[1]).read_text())["jwt_public_key_secret_arn"]["value"])
PY
)

# PKCS#8 PEM, matching what the auth-service expects.
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 \
  -outform PEM -out "$WORK_DIR/jwt-private.pem" 2>/dev/null
openssl rsa -in "$WORK_DIR/jwt-private.pem" -pubout \
  -out "$WORK_DIR/jwt-public.pem" 2>/dev/null

if ! grep -q "BEGIN PRIVATE KEY" "$WORK_DIR/jwt-private.pem"; then
  echo "Generated signing key is not in PKCS#8 form; refusing to store it." >&2
  exit 1
fi

put_secret "$JWT_PRIVATE_ARN" "$WORK_DIR/jwt-private.pem"
put_secret "$JWT_PUBLIC_ARN" "$WORK_DIR/jwt-public.pem"
shred -u "$WORK_DIR/jwt-private.pem" 2>/dev/null || rm -f "$WORK_DIR/jwt-private.pem"
rm -f "$WORK_DIR/jwt-public.pem"
echo "  seeded the JWT signing keypair"

echo "Secrets seeded. No credential value passed through Terraform state."
