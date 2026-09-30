#!/usr/bin/env sh
# Usage: sh scripts/local-mysql-init.sh [--env-file PATH]
#
# Bootstraps (first run only), starts, provisions and verifies the project-scoped
# MySQL 8.4 on 127.0.0.1:3308 (data: .local/mysql-data). Idempotent; never touches
# the host server on 3306 or Compose on 3307, and never modifies the env file.
# Env file precedence: --env-file, $MYSQL_LOCAL_ENV_FILE, $K8S_ENV_FILE, .env.example.
# Custom: K8S_ENV_FILE=.env sh scripts/local-mysql-init.sh (default is demo .env.example)
# Full help: python3 scripts/local-mysql.py --help
set -eu
cd "$(dirname "$0")/.."
case "${1:-}" in -h|--help) sed -n '2,9p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;; esac
exec python3 scripts/local-mysql.py init "$@"
