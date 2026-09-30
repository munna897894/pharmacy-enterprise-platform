# 15-day execution plan — 6 productive hours per day

## Daily rhythm

| Block | Duration | Activity |
|---|---:|---|
| A | 60 min | Learn concepts and draw the day's flow |
| B | 90 min | Run the first bounded Copilot prompt |
| C | 90 min | Complete implementation and tests |
| D | 60 min | Manual/API verification and troubleshooting |
| E | 45 min | Read important generated code and explain it aloud |
| F | 15 min | Notes, checklist and Git commit |

Breaks are outside the six productive hours. If a day's acceptance criteria fail, finish that stage before starting unrelated work. Protect Days 14–15 for integration and troubleshooting.

This is a **study sequence**, not a current implementation-status report. For
present wiring use `interview-guides/full-system-architecture-map.md`. The
canonical local run path is `docs/self-run-guide.md` (Docker Desktop Kubernetes
full fleet, host-native MySQL `3308` and Kafka `19092`/`29092`); Compose is a
legacy full-fleet alternative. The current full-fleet AWS sandbox is prepared
but not live-verified, and Prompt 14 vendor hooks are disabled.

## Day 1 — Foundation and product service skeleton

**Concepts:** IoC, dependency injection, beans, Maven, Boot auto-configuration, profiles, Actuator, controller/service/repository flow.

**Execute:** `prompts/01-bootstrap-monorepo.md`, then begin Product prompt 02A through entity/migration/repository.

**Finish with:** root build green, MySQL Testcontainer repository test, service health.

## Day 2 — Complete reference product service

**Concepts:** JPA lifecycle, transactions, validation, ProblemDetail, pagination, Redis cache-aside, optimistic locking.

**Execute:** finish Prompt 02A.

**Failure lab:** Redis down; invalid input; duplicate NDC; stale optimistic version.

**Finish with:** medication APIs, tests, OpenAPI and cache metrics.

## Day 3 — Customer and pharmacy services

**Concepts:** service boundaries, database ownership, object-level authorization, cross-service IDs.

**Execute:** Prompts 02B and 02C. Use Copilot heavily for repeated scaffolding but review migrations/security/tests.

**Finish with:** three independent schemas/services and no implementation-module coupling.

## Day 4 — Auth and gateway

**Concepts:** authentication/authorization, RSA JWT, claims, filters, 401/403, gateway routing, CORS and rate limiting.

**Execute:** Prompt 03A, then core of 03B.

**Finish with:** login/JWKS, validated token through gateway, correlation ID, role matrix.

## Day 5 — Inventory and prescription

**Concepts:** concurrent stock updates, domain invariants, external client timeouts, retry safety, circuit breaker, bulkhead.

**Execute:** Prompts 04A and 04B; start 04C.

**Finish with:** inventory API/domain tests and controllable mock dependency.

## Day 6 — Prescription outbox and resilience

**Concepts:** two-transaction external-call flow, outbox, failure state, circuit open/half-open.

**Execute:** finish Prompt 04C and its verification matrix, then apply the relevant HTTP/client portion of Prompt 07.

**Failure lab:** use the external payment mock's delay/500/rejection modes for
the running saga. External prescription verification remains an exercise, not
a currently executable verification flow.

**Finish with:** status/outbox consistency and resilience tests.

## Day 7 — Order and payment domains

**Concepts:** aggregate/state machine, idempotency keys, monetary calculation, synchronous boundary validation, saga preparation.

**Execute:** Prompts 05A and 05B; do 05C if time remains.

**Finish with:** order/payment tests and outbox rows, no Kafka yet.

## Day 8 — Kafka and event-driven workflow

**Concepts:** broker, topic, partition, key, offset, group, rebalance, delivery semantics, retry/DLT.

**Execute:** Prompt 06A and 06B.

**Finish with:** happy saga, inventory rejection and payment-failure compensation integration tests.

## Day 9 — Notification, audit and Kafka failure lab

**Concepts:** idempotent consumers, inbox, consumer lag, out-of-order events, poison messages.

**Execute:** Prompts 06C and 06D, then operational verification.

**Finish with:** duplicate-safe side effects, lag catch-up and DLT evidence.

## Day 10 — Docker Compose and Redis review

**Concepts:** images, layers, containers, networks, volumes, listeners, non-root runtime, JVM memory.

**Execute:** Prompt 08, then complete the platform-wide review in Prompt 07. Run
end-to-end happy/negative paths with `sh scripts/local-k8s-test.sh` on the
canonical local fleet; study Compose as the legacy alternative.

**Failure lab:** wrong DNS, bad port, dependency unavailable, container memory limit.

**Finish with:** reproducible local Kubernetes fleet using
`docs/self-run-guide.md`; native dependencies and the Helm release are
separate startup steps, not a single Compose command.

## Day 11 — Kubernetes and Helm

**Concepts:** pod/deployment/service, DNS, probes, resources, ConfigMap/Secret, Ingress, HPA, rollout/rollback, Helm rendering.

**Execute:** Prompt 09A, 09B for a representative service set, and 09C if on schedule.

**Failure lab:** at least four mandatory Kubernetes failures today; complete remaining failures later.

**Finish with:** full local Kubernetes fleet (twelve JVM workloads plus Redis)
and a rollback demonstration.

## Day 12 — Observability

**Concepts:** RED/USE, metrics cardinality, traces/spans, async propagation, structured logs, alert triage.

**Execute:** Prompts 10A and 10B using the separate local
`pharmacy-observability` Helm release. Treat Prompt 14 Dynatrace/Splunk hooks
as disabled preparation unless an approved trial is activated.

**Failure lab:** slow payment via the external mock and DB connection pressure.
Treat slow prescription verification as a future implementation exercise.

**Finish with:** dashboard plus one end-to-end trace/correlated log trail.

## Day 13 — CI/CD and DevSecOps

**Concepts:** PR checks, artifacts, image tags, vulnerability scanning, secret safety, OIDC, GitOps.

**Execute:** Prompt 11. Run locally reproducible equivalents before relying on CI.

**Finish with:** green PR workflow configuration, scans, immutable image tagging and GitOps example.

## Day 14 — Postman, E2E and optional AWS

**First four hours:** execute Prompt 13 and fix integration gaps. Run all core E2E scenarios.

**Final two hours:** study the current full-fleet Prompt 12 AWS mapping, cost
review and guarded lifecycle. Only deploy with fresh AWS SSO credentials,
explicit billable approval and budget protections; offline validation is not
a live smoke test.

**Finish with:** repeatable local E2E suite and an AWS create/destroy plan,
not necessarily a running EKS cluster. Do not mark full-fleet AWS verified
without live apply, smoke and post-destroy evidence.

## Day 15 — Production simulation and explanation

**Hour 1:** clean start, build and deploy.

**Hour 2:** happy path plus inventory/payment negative paths.

**Hours 3–5:** randomly select at least four incident labs. Diagnose with logs, metrics, traces, Kafka tools and Kubernetes before changing code.

**Hour 6:** complete definition of done, draw architecture from memory, explain one request and one Kafka saga, record gaps and tag the release.

No major new features on Day 15.

## Scope-control rule

If behind schedule, cut in this order:

1. Actual AWS deployment (keep architecture/Terraform review).
2. Dynatrace/Splunk trial execution (keep hooks/runbooks).
3. Advanced Helm/Argo CD polish.
4. Nonessential endpoints.

Do not cut security tests, idempotency, compensation tests, probes, core observability or incident practice.
