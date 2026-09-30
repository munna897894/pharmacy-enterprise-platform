#!/usr/bin/env sh
# Usage: sh scripts/local-mysql-stop.sh
# Gracefully stops the project-scoped MySQL on 3308; data is preserved.
set -eu
cd "$(dirname "$0")/.."
case "${1:-}" in -h|--help) sed -n '2,3p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;; esac
exec python3 scripts/local-mysql.py stop "$@"
