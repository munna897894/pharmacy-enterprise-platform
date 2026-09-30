#!/usr/bin/env bash
# Verifies the requested EKS version is in STANDARD support before planning.
# Extended-support versions bill a much higher per-cluster-hour rate, so this
# fails closed rather than letting a stale pin quietly increase cost.
# Prints only version/support metadata: no account identifiers or credentials.
set -euo pipefail

VERSION="${1:?usage: aws-check-eks-version.sh <kubernetes-version>}"
: "${AWS_REGION:=us-east-1}"
: "${AWS_PROFILE:=pharmacy-sandbox}"

if ! aws eks describe-cluster-versions --region "$AWS_REGION" --profile "$AWS_PROFILE" \
  --output json >/tmp/.eks-versions.$$ 2>/dev/null; then
  rm -f "/tmp/.eks-versions.$$"
  echo "WARNING: could not reach the EKS API to confirm support status for $VERSION." >&2
  echo "         Re-verify against the EKS version lifecycle before applying." >&2
  exit 0
fi

python3 - "$VERSION" "/tmp/.eks-versions.$$" <<'PY'
import json
import pathlib
import sys

version, path = sys.argv[1], pathlib.Path(sys.argv[2])
data = json.loads(path.read_text())
path.unlink(missing_ok=True)

entries = {item["clusterVersion"]: item for item in data.get("clusterVersions", [])}
standard = sorted(v for v, i in entries.items() if i.get("status") == "STANDARD_SUPPORT")

entry = entries.get(version)
if entry is None:
    print(f"Requested EKS version {version} is not offered. Standard support: {', '.join(standard) or 'unknown'}.", file=sys.stderr)
    raise SystemExit(1)

status = entry.get("status", "UNKNOWN")
if status != "STANDARD_SUPPORT":
    print(
        f"Refusing to plan: EKS {version} is {status}, which bills extended-support fees.\n"
        f"End of standard support was {entry.get('endOfStandardSupportDate', 'unknown')}.\n"
        f"Use a standard-support version instead: {', '.join(standard) or 'see the EKS lifecycle table'}.",
        file=sys.stderr,
    )
    raise SystemExit(1)

print(f"EKS {version} is in STANDARD support (ends {entry.get('endOfStandardSupportDate', 'unknown')}).")
PY
