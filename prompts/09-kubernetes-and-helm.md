# Prompt 09 — Local Kubernetes and Helm (current state)

This file reflects the repository’s actual local Kubernetes setup and the operational fixes needed to make the fleet stable in a real local cluster environment.

## Status

Complete for the current local Kubernetes and Helm implementation.

The repo contains:

- `k8s/` raw manifests
- deployment definitions for the service fleet
- registry-backed image usage and shared config wiring
- Helm chart support and local values-driven configuration patterns

## Current local cluster model

The local Kubernetes model is intentionally not a pure in-cluster MySQL/Kafka deployment. The platform was stabilized around a host-based local dependency model, with the following practical decisions:

- MySQL and Kafka are accessed from the cluster via host endpoints (for example `host.docker.internal`)
- Redis remains local-cluster-backed for this runtime model
- image tags are registry-based and refreshed after rebuilds
- stale pods and ReplicaSets are cleaned up before rollout validation
- each service is reduced to a single replica for local resource constraints unless a specific workload requires more

## What is implemented in the repo

### Kubernetes manifests

The `k8s/` set includes service-specific manifests and common config wiring for the application fleet. This includes:

- Deployments
- ClusterIP Services
- ConfigMaps/environment config
- readiness/liveness logic
- image configuration and rollout alignment
- service-level corrections for ports, probes, and env values

### Platform drift fixes that were required

The local runtime required a fleet-wide cleanup rather than isolated service debugging:

- stale image tags in service manifests
- stale DB naming and user assumptions across services
- stale port and probe values
- stale rollout/ReplicaSet state
- old config values overriding environment-driven settings
- incorrect Kafka listener advertisement for local host connectivity

These were not “new features”; they were necessary runtime corrections to make the intended deployment pattern work.

## Helm approach

The repo’s working pattern is to keep the manifest layer and Helm values explicit and service-specific, rather than over-abstracting the chart. This keeps local deployment behavior easy to reason about while still providing a reusable deploy model.

## Current operational practice

- use registry-backed images rather than relying on old local tags
- validate deployment health after each config change
- inspect rollout history and replace stale revision state when needed
- prefer deterministic local runtime values over hidden assumptions

## Final guidance

This is the source-of-truth documentation for the current Kubernetes and Helm state. The original task brief is only historical context; the live manifests and configuration are what define the repo today.

