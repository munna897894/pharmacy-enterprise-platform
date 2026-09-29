# Prompt 01 — Bootstrap and platform foundation (current state)

This file reflects the live repository state after the monorepo bootstrap, service implementation, and runtime stabilization work. It is a current-state reference rather than the original task brief.

## Status

Complete.

The repo is a Java 21, Spring Boot 3.5.x, Spring Cloud 2025.0.x Maven monorepo with a shared parent, shared platform libraries, and a full service fleet. The root reactor builds as a coherent project and the local Kubernetes/Docker runtime has been aligned to the host-based dependency model used in this environment.

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

Local development and the Docker/Kubernetes local environment are intentionally not using an in-cluster MySQL/Kafka dependency model. Instead:

- app containers reach host services via `host.docker.internal`
- Redis remains local-cluster hosted for the Kubernetes runtime
- image tags are registry-backed and refreshed after rebuilds
- stale deployments, ReplicaSets, and outdated config values have been cleaned up to keep the platform consistent

## Execution baseline

- `./mvnw clean verify` is the repo-wide verification baseline.
- Local development uses the root `pom.xml` as the import target in IntelliJ/IDEA.
- Java 21 is the required SDK for the project and Maven runner.

## Practical guidance

Use this document as the current project baseline. The earlier bootstrap brief is a historical artifact; the live repo is the source of truth for what is implemented today.

