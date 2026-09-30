#!/usr/bin/env sh
# Usage: sh scripts/local-mysql-verify.sh [--env-file PATH]
# Checks each service user against its own schema and denies cross-schema access. Accepts --env-file PATH.
set -eu
cd "$(dirname "$0")/.."
case "${1:-}" in -h|--help) sed -n '2,3p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;; esac
exec python3 scripts/local-mysql.py verify "$@"
