#!/usr/bin/env sh
set -eu
docker compose -f infra/compose/compose.yml --env-file .env config
docker compose -f infra/compose/compose.yml --env-file .env build
