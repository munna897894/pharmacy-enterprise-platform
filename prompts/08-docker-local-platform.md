# Prompt 08 — Docker Compose local platform (current state)

This file reflects the local platform configuration now used by the repo, including the actual Docker/Compose runtime pattern and the fixed host-based dependency model.

## Status

Complete for the local Docker platform model.

The repo contains:

- `docker-compose.yml`
- `infra/compose/` setup and service config
- `.env.example`
- scripts for local lifecycle operations

## Current local platform model

The local platform is intentionally configured around host-based services instead of in-cluster MySQL/Kafka for day-to-day local development.

Expected local access pattern:

- app containers reach MySQL and Kafka via `host.docker.internal`
- Redis remains a local dependency for Kubernetes and selected local setups
- service URLs are environment-configured rather than hardcoded to a single profile

This is the actual runtime model that was stabilized after the earlier drift fixes; it is the source of truth for the repo’s current Docker operations.

## Included platform components

- all service applications
- MySQL initialization and schema setup
- Redis service configuration
- Kafka broker configuration
- named networks and persisted volumes where appropriate
- local scripts for build/start/status/log/reset behavior

## Important runtime corrections that were made

The initial local platform assumptions were not fully valid in practice. The following corrections were required and are now reflected in the repo’s working setup:

- remove `localhost` assumptions inside containers
- fix Kafka advertised-listener behavior for host connectivity
- align service DB users, names, and credentials
- rebuild and re-push registry-backed service images when config changes are required
- force cleanup of stale pods, deployments, and rollout state when the old config remains active

## Current operational guidance

- validate Compose config before startup
- build the required images first
- start dependencies before app services when a full stack is needed
- prefer the environment-configured local URLs over directly hardcoding service hostnames
- guard destructive reset operations behind explicit confirmation or a dedicated flag

## Repo reality

The original prompt was a task definition. The actual repo behavior is the final authority: the platform is running in a host-aware dependency model rather than a purely internal “compose-only localhost” model.

