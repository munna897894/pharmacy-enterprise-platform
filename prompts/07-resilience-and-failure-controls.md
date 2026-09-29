# Prompt 07 — Resilience and controlled failures (current state)

This file documents the resilience-oriented work that was implemented and stabilized in the repo, rather than only the original task brief.

## Status

Complete for the current resilience scope.

The repo includes resiliency controls across the service fleet, especially around HTTP client behavior, external verification, and local failure injection.

## Implemented resilience measures

- explicit timeouts on outbound HTTP calls
- retry only where the call is safely idempotent
- circuit breaker and bulkhead protection around external prescription verification
- local mock failure modes for delay/error/rejection scenarios
- deterministic payment success/failure/delay simulation
- readiness and shutdown behavior aligned to graceful service lifecycle
- metrics and logging for dependency latency and failure-handling behavior

## Platform-level lessons captured by the implementation

The major operational issue in the project was not a lack of resilience patterns in theory; it was drift in deployment values, stale images, and old ConfigMap/Kubernetes state. Once the platform state was corrected, the resilience features could actually perform as designed.

## Local failure-control model

The repo includes the test-friendly failure controls needed to simulate:

- dependency delays
- dependency errors
- retries and half-open recovery behavior
- Kafka or external degradation without corrupting business state
- cache outage without a false-success product read path

## Acceptance criteria now reflected by the platform

- outbound clients have clear timeout budgets
- retries are restricted to safe or idempotent execution paths
- external dependency failure is isolated to a bounded failure state
- product reads still function when Redis degrades
- order state remains consistent when downstream dependencies are temporarily unavailable
- graceful shutdown happens before service termination

