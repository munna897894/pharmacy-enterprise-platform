#!/usr/bin/env sh
# Usage: sh scripts/local-mysql-start.sh
# Bootstraps .local/mysql-data on first run, then starts MySQL 8.4 on 127.0.0.1:3308 in the background.
set -eu
cd "$(dirname "$0")/.."
case "${1:-}" in -h|--help) sed -n '2,3p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;; esac
exec python3 scripts/local-mysql.py start "$@"
