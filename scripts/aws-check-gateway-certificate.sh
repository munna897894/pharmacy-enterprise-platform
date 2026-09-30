#!/usr/bin/env bash
# Fails closed unless the ACM certificate for the HTTPS gateway ALB is ISSUED,
# lives in the sandbox account/region, is currently valid, and covers the
# gateway hostname (exact name or a single-label wildcard). Certificate
# metadata is public information; no key material is ever retrievable here.
#
# Usage: aws-check-gateway-certificate.sh <certificate-arn> <hostname>
#        aws-check-gateway-certificate.sh --offline <describe-certificate.json> <hostname> <account> <region>
set -euo pipefail

if [[ "${1:-}" == "--offline" ]]; then
  [[ "$#" -eq 5 ]] || { echo "Usage: $0 --offline <json> <hostname> <account> <region>" >&2; exit 2; }
  DESCRIPTION_FILE="$2"; HOSTNAME_TO_CHECK="$3"; ACCOUNT="$4"; REGION="$5"
  DESCRIPTION=$(cat "$DESCRIPTION_FILE")
else
  [[ "$#" -eq 2 ]] || { echo "Usage: $0 <certificate-arn> <hostname>" >&2; exit 2; }
  : "${AWS_PROFILE:?AWS_PROFILE must be set (run through aws_sandbox_guard).}"
  : "${AWS_REGION:?AWS_REGION must be set.}"
  : "${EXPECTED_AWS_ACCOUNT_ID:?EXPECTED_AWS_ACCOUNT_ID must be set.}"
  HOSTNAME_TO_CHECK="$2"; ACCOUNT="$EXPECTED_AWS_ACCOUNT_ID"; REGION="$AWS_REGION"
  if ! DESCRIPTION=$(aws acm describe-certificate --certificate-arn "$1" \
      --region "$AWS_REGION" --profile "$AWS_PROFILE" --output json); then
    echo "Could not describe the gateway certificate; refusing to plan an HTTPS ALB." >&2
    exit 1
  fi
fi

CERT_DESCRIPTION="$DESCRIPTION" python3 - "$HOSTNAME_TO_CHECK" "$ACCOUNT" "$REGION" <<'PY'
import datetime
import json
import os
import sys

hostname, account, region = sys.argv[1:]
cert = json.loads(os.environ["CERT_DESCRIPTION"])["Certificate"]
problems = []

arn = cert.get("CertificateArn", "")
if not arn.startswith(f"arn:aws:acm:{region}:{account}:certificate/"):
    problems.append("certificate is not in the sandbox account and region")
if cert.get("Status") != "ISSUED":
    problems.append(f"certificate status is {cert.get('Status')!r}, not ISSUED")

now = datetime.datetime.now(datetime.timezone.utc)
def parse(value):
    if isinstance(value, (int, float)):
        return datetime.datetime.fromtimestamp(value, datetime.timezone.utc)
    return datetime.datetime.fromisoformat(str(value).replace("Z", "+00:00"))
not_before, not_after = cert.get("NotBefore"), cert.get("NotAfter")
if not_before is None or not_after is None:
    problems.append("certificate validity window is unknown")
else:
    if parse(not_before) > now:
        problems.append("certificate is not yet valid")
    if parse(not_after) <= now + datetime.timedelta(days=1):
        problems.append("certificate expires within one day")

names = {cert.get("DomainName", "").lower(), *(n.lower() for n in cert.get("SubjectAlternativeNames", []))}
def covers(name, host):
    if name == host:
        return True
    # RFC 6125: a wildcard matches exactly one left-most label.
    if name.startswith("*.") and "." in host:
        return host.split(".", 1)[1] == name[2:]
    return False
if not any(covers(name, hostname.lower()) for name in names if name):
    problems.append(f"certificate names do not cover {hostname}")

if problems:
    print("Gateway certificate rejected: " + "; ".join(problems) + ".", file=sys.stderr)
    raise SystemExit(1)
print(f"Gateway certificate verified: ISSUED, valid, covers {hostname}.")
PY
