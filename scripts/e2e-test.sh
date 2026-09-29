#!/usr/bin/env sh
# Full end-to-end test entrypoint: runs the smoke test first (fail fast if the
# stack isn't up), then drives the complete Postman/Newman collection
# (happy paths, negative cases, and choreography-saga scenarios) against it.
set -eu
base_url="${1:-http://localhost:8080}"
script_dir="$(cd "$(dirname "$0")" && pwd)"

echo "== Step 1/2: smoke test =="
"${script_dir}/smoke-test.sh" "$base_url"

echo
echo "== Step 2/2: Newman E2E collection =="
"${script_dir}/newman.sh"
