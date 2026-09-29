#!/usr/bin/env sh
set -eu
docker compose -f infra/compose/compose.yml --env-file .env up -d
docker compose -f infra/compose/compose.yml --env-file .env ps
