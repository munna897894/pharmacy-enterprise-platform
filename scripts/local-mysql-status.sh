#!/usr/bin/env sh
# Usage: sh scripts/local-mysql-status.sh
# Exits 0 when the project-scoped MySQL on 3308 is running and answering.
set -eu
cd "$(dirname "$0")/.."
case "${1:-}" in -h|--help) sed -n '2,3p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;; esac
exec python3 scripts/local-mysql.py status "$@"
