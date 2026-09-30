# Prompt 01 — Bootstrap and platform foundation (current state)

> **Status (2026-09-30): Complete.** Current platform versions and deployment topology are authoritative in [architecture](../docs/02-architecture.md) and [local deployment](../docs/architecture-local.md); details below are a stage record.

This file records the Prompt 01 implementation stage; the linked architecture docs, not this stage summary, define current platform state.

## Status

Complete.

The repo is a Java 21, Spring Boot 3.5.16, Spring Cloud 2025.0.3 Maven monorepo with a shared parent, shared platform libraries, and a full service fleet. The root reactor builds as a coherent project. In the canonical full-fleet local Kubernetes deployment, MySQL and Kafka run on the host; Redis and the external mock run in the cluster. Docker Compose is a legacy alternative. See [local deployment](../docs/architecture-local.md).

## What was implemented

- Root Maven parent/aggregator POM
- Maven Wrapper
- Shared platform modules:
  - `platform/event-contracts`
  - `platform/test-support`
- Service modules:
  - `services/api-gateway`
  - `services/auth-service`
  - `services/customer-service`
  - `services/inventory-service`
  - `services/notification-service`
  - `services/order-service`
  - `services/payment-service`
  - `services/pharmacy-service`
  - `services/prescription-service`
  - `services/product-service`
  - `services/external-mock-service`
- Shared config for local and Kubernetes execution
- Docker Compose and Kubernetes deployment scaffolding
- Prometheus/Grafana/OpenTelemetry-aligned observability scaffold
- Common Java package convention under `com.jagapathi.pharmacy`

## Architecture rules that are now established

- Each service owns its data and database schema.
- Public traffic enters through `api-gateway`.
- No service imports another service implementation module as a library dependency.
- DTOs, Kafka payloads, and persistence entities stay separate.
- JWT validation is handled at the edge and downstream resource services validate tokens as needed.
- The local model uses host-based access for MySQL and Kafka (`host.docker.internal`) while Redis remains in-cluster for the local Kubernetes runtime.

## Operational conventions used in the repo

- Java 21 and Maven Wrapper are the default toolchain.
- Spring Boot 3.5.x / Spring Cloud 2025.0.x compatibility is the target family.
- MySQL remains the durable backing store for the service fleet.
- Flyway runs per service and is not shared across services.
- Outbox/inbox patterns are used for Kafka event publication and duplicate processing protection.
- Correlation IDs are propagated across HTTP and Kafka flows.
- Actuator health and readiness endpoints are exposed for runtime checks.

## Current runtime model

For the canonical full-fleet local Kubernetes environment:

- application pods reach host MySQL and Kafka through the configured host endpoint
- Redis and the external mock service run in the cluster
- the original Compose path remains available as a legacy alternative, not the canonical Kubernetes deployment
- image tags are registry-backed and refreshed after rebuilds
- stale deployments, ReplicaSets, and outdated config values have been cleaned up to keep the platform consistent

## Execution baseline

- `./mvnw clean verify` is the repo-wide verification baseline.
- Local development uses the root `pom.xml` as the import target in IntelliJ/IDEA.
- Java 21 is the required SDK for the project and Maven runner.

## Practical guidance

Use the linked canonical docs and live repository for current state. This file is a stage record, not a replacement for the original bootstrap requirements.
