#!/usr/bin/env sh
set -eu

if [ ! -f infra/local/kafka/server.properties ]; then
  echo "Run from the repository root." >&2
  exit 1
fi

mkdir -p .local/kafka-data
if [ ! -f .local/kafka-data/meta.properties ]; then
  kafka-storage format --standalone \
    --cluster-id "$(kafka-storage random-uuid)" \
    --config infra/local/kafka/server.properties
fi

exec kafka-server-start infra/local/kafka/server.properties
