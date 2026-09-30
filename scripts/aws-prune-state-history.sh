#!/usr/bin/env bash
# Permanently deletes Terraform state object versions from the state bucket.
#
# Why this exists: a versioned bucket keeps every prior state object. Historic
# versions written before credentials were moved out of Terraform (see
# infra/terraform/modules/secrets/main.tf) still contain the generated database
# passwords and the JWT signing key in plaintext, and `terraform destroy` does
# not touch them. Run this when retiring the exercise, after a successful
# destroy and post-destroy check.
#
# This is irreversible: state history cannot be recovered afterwards. It refuses
# to run unless the current state has no managed resources left, so it cannot
# strand a live environment that still needs its state.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=scripts/aws-common.sh
source "$ROOT_DIR/scripts/aws-common.sh"

STATE_BUCKET="${STATE_BUCKET:-}"
STATE_KEY="${STATE_KEY:-pharmacy-sandbox/terraform.tfstate}"

if [[ -z "$STATE_BUCKET" ]]; then
  echo "STATE_BUCKET must be set to the Terraform state bucket name." >&2
  echo "Refusing to guess: deleting versions from the wrong bucket is irreversible." >&2
  exit 1
fi

aws_sandbox_guard

ENV_DIR="$ROOT_DIR/infra/terraform/envs/sandbox"
if [[ -d "$ENV_DIR/.terraform" ]]; then
  RESOURCE_COUNT=$(terraform -chdir="$ENV_DIR" state list 2>/dev/null | grep -c . || true)
  if [[ "${RESOURCE_COUNT:-0}" -gt 0 ]]; then
    echo "Refusing to prune: Terraform state still tracks $RESOURCE_COUNT resource(s)." >&2
    echo "Run scripts/aws-destroy.sh and scripts/aws-post-destroy-check.sh first." >&2
    exit 1
  fi
fi

if ! aws s3api head-bucket --bucket "$STATE_BUCKET" --profile "$AWS_PROFILE" >/dev/null 2>&1; then
  echo "State bucket '$STATE_BUCKET' is not reachable with profile '$AWS_PROFILE'." >&2
  exit 1
fi

echo "This permanently deletes ALL versions of:"
echo "  s3://${STATE_BUCKET}/${STATE_KEY}"
echo "Historic state may contain credentials generated before the environment"
echo "stopped writing them to state. This cannot be undone."
read -r -p "Type 'prune' to confirm: " CONFIRM
if [[ "$CONFIRM" != "prune" ]]; then
  echo "Aborted."
  exit 1
fi

umask 077
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

# Page through versions and delete markers for this key only. Other keys in the
# bucket are left untouched.
aws s3api list-object-versions \
  --bucket "$STATE_BUCKET" \
  --prefix "$STATE_KEY" \
  --profile "$AWS_PROFILE" \
  --output json >"$WORK_DIR/versions.json"

python3 - "$WORK_DIR/versions.json" "$STATE_KEY" "$WORK_DIR/delete.json" <<'PY'
import json
import pathlib
import sys

source, key, dest = pathlib.Path(sys.argv[1]), sys.argv[2], pathlib.Path(sys.argv[3])
data = json.loads(source.read_text())

objects = [
    {"Key": entry["Key"], "VersionId": entry["VersionId"]}
    for group in ("Versions", "DeleteMarkers")
    for entry in data.get(group, []) or []
    # Exact key match only: a prefix search could match unrelated objects.
    if entry["Key"] == key
]

if not objects:
    print("NONE")
    raise SystemExit(0)

# The S3 delete API accepts at most 1000 objects per call.
batches = [objects[i:i + 1000] for i in range(0, len(objects), 1000)]
dest.write_text(json.dumps([{"Objects": batch, "Quiet": True} for batch in batches]))
print(len(objects))
PY

COUNT=$(python3 - "$WORK_DIR/versions.json" "$STATE_KEY" <<'PY'
import json
import pathlib
import sys

data = json.loads(pathlib.Path(sys.argv[1]).read_text())
key = sys.argv[2]
print(sum(
    1
    for group in ("Versions", "DeleteMarkers")
    for entry in data.get(group, []) or []
    if entry["Key"] == key
))
PY
)

if [[ "$COUNT" == "0" ]]; then
  echo "No versions found for that key; nothing to prune."
  exit 0
fi

BATCHES=$(python3 -c 'import json,sys; print(len(json.load(open(sys.argv[1]))))' "$WORK_DIR/delete.json")
for ((i = 0; i < BATCHES; i++)); do
  python3 -c 'import json,sys; json.dump(json.load(open(sys.argv[1]))[int(sys.argv[2])], open(sys.argv[3], "w"))' \
    "$WORK_DIR/delete.json" "$i" "$WORK_DIR/batch.json"
  aws s3api delete-objects \
    --bucket "$STATE_BUCKET" \
    --profile "$AWS_PROFILE" \
    --delete "file://${WORK_DIR}/batch.json" >/dev/null
done

echo "Deleted $COUNT state object version(s)."
echo "Verify with: aws s3api list-object-versions --bucket $STATE_BUCKET --prefix $STATE_KEY"
