#!/usr/bin/env sh
set -eu
if [ "${1:-}" != "--yes-i-know" ]; then
  echo "Refusing to remove named volumes. Re-run with --yes-i-know." >&2
  exit 1
fi
docker compose -f infra/compose/compose.yml down -v
