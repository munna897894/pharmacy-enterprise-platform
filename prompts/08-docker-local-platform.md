# Prompt 08 — Docker Compose local platform (current state)

> **Status (2026-09-30): Complete.** Compose remains a legacy local alternative. The canonical full-fleet local Kubernetes topology uses host MySQL/Kafka and in-cluster Redis/external-mock; see [local deployment](../docs/architecture-local.md).

This file records the Docker Compose stage and its host-based dependency pattern; it is not the source of truth for the current local Kubernetes deployment.

## Status

Complete for the local Docker platform model.

The repo contains:

- `docker-compose.yml`
- `infra/compose/` setup and service config
- `.env.example`
- scripts for local lifecycle operations

## Current local platform model

The Compose path is retained as a legacy alternative. Do not infer the canonical full-fleet Kubernetes topology from this Compose prompt.

When using this legacy Compose path, dependency addresses are environment-configured rather than hardcoded to one profile.

Compose-specific behavior is defined by the files under `infra/compose/`; the linked local deployment guide defines the canonical Kubernetes topology.

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

The Compose implementation is a retained local alternative. Use the linked deployment guide for current platform topology.
