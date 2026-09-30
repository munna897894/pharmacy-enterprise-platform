# Production incident labs

For every lab use this record:

```text
Incident ID:
Start/end time:
Customer symptom:
Initial severity/impact:
Recent change:
Evidence timeline:
Hypotheses considered:
Root cause:
Mitigation:
Permanent correction:
Validation:
Prevention/alert improvement:
```

Do not ask Copilot for the answer until you have collected evidence and ranked at least three hypotheses.

Use the canonical full Docker Desktop Kubernetes fleet for runnable local labs
(`docs/self-run-guide.md`): project MySQL on `3308`, Kafka host listener
`19092`/pod listener `29092`, in-cluster Redis and external mock. These are
exercises, not evidence that each fault has been injected or resolved. The
current prescription service does not call an external verifier, and the
full-fleet AWS sandbox has not been live-verified.

## INC-01 — API latency jumps from 300 ms to 9 seconds

**Inject:** set the external payment mock to `DELAY` (see
`scripts/resilience-lab.sh slow-payment`); a prescription-verification delay
is only a future exercise until that outbound verification exists.

**Observe:** gateway p95, payment/mock trace waterfall, client timeout and
correlation logs; check circuit state only if a breaker is configured on the
specific client.

**Success:** identify the exact downstream span and explain whether retry worsened latency.

## INC-02 — Inventory pods restart continuously

**Inject:** lower the inventory pod's local lab memory limit within safe
resources; restore the chart values afterward.

**Observe:** pod status/restarts, `describe`, last termination reason, previous logs, memory graph and events.

**Success:** distinguish OOMKilled from Java heap OOM and propose application plus limit/request changes.

## INC-03 — Orders succeed but notifications are absent

**Inject:** stop notification consumer or force delivery failures.

**Observe:** order state, topic offsets, consumer group lag, consumer logs, retry/DLT and notification database.

**Success:** prove the order workflow is healthy and isolate the async notification failure.

## INC-04 — HTTP 500 rate increases after deployment

**Inject:** deploy an image with a bad environment variable or incompatible API assumption.

**Observe:** rollout revision, pod age, error-rate split by version, logs/traces and readiness.

**Success:** mitigate with rollback, validate recovery and identify the missing release gate.

## INC-05 — Database connection pool exhausted

**Inject:** temporarily reduce Hikari pool and introduce slow queries/held connections in a safe test path.

**Observe:** Hikari active/idle/pending, request threads, DB process list, query duration and timeout errors.

**Success:** distinguish connection leak, slow query and undersized pool; do not solve only by increasing pool size.

## INC-06 — Kafka consumer lag grows continuously

**Inject:** stop/slow inventory consumer while producing orders.

**Observe:** produced rate, consumed rate, lag by partition, rebalance logs, processing duration and errors.

**Success:** identify whether the cause is capacity, poison messages, rebalances or dependency slowness.

## INC-07 — Duplicate inventory reservations

**Inject:** replay the same `OrderCreated` event/eventId and then a semantically duplicate event with a different eventId.

**Observe:** processed-event table, business unique key, reservation count and logs.

**Success:** explain transport idempotency vs business idempotency and fix both without dropping legitimate events.

## INC-08 — Readiness fails but liveness passes

**Inject:** break the MySQL URL or critical dependency configuration.

**Observe:** readiness/liveness endpoints, endpoints/pod routing, events and dependency health details.

**Success:** explain why Kubernetes should stop routing without necessarily restarting the process.

## INC-09 — Service name cannot resolve

**Inject:** wrong Kubernetes service hostname/namespace.

**Observe:** `kubectl get svc/endpoints`, DNS lookup from pod, environment/config and NetworkPolicy.

**Success:** isolate DNS vs empty endpoints vs blocked traffic.

## INC-10 — Product cache outage

**Inject:** stop Redis.

**Observe:** cache error/miss metrics, product latency, MySQL load and application errors.

**Success:** product reads remain correct; explain degraded latency and protection against database overload.

## INC-11 — Expired JWT reports wrong status

**Inject:** use expired token and a valid token with insufficient role.

**Observe:** gateway and service security logs without token contents.

**Success:** expired/invalid returns 401; insufficient privilege returns 403; correlation ID remains visible.

## INC-12 — Outbox backlog grows

**Inject:** stop Kafka while creating orders, then restore it.

**Observe:** unpublished count/oldest age, Kafka producer errors, business records and catch-up.

**Success:** no committed order event is lost; repeated publish remains safe downstream.

## INC-13 — Slow SQL causes cascading failure

**Inject:** unindexed search or controlled database delay in test.

**Observe:** SQL/query timing, Hikari pending, HTTP latency, timeouts and CPU.

**Success:** identify query/index rather than treating all symptoms as pod capacity.

## INC-14 — Bad secret causes startup failure

**Inject:** wrong DB password/JWT public-key path.

**Observe:** pod events/logs, Secret key names/mounts and startup failure sanitization.

**Success:** fix configuration without printing secret values.

## INC-15 — CPU throttling under load

**Inject:** low CPU limit plus load test.

**Observe:** CPU usage/throttling, latency, replicas, HPA and GC.

**Success:** separate application CPU work, throttling and insufficient replica count.

## Standard triage order

1. Confirm impact and time window.
2. Identify recent deployments/config changes.
3. Check golden signals: traffic, errors, latency and saturation.
4. Follow trace/correlation to the failing dependency.
5. Check platform events/restarts/resources.
6. Mitigate safely before deep repair when impact is active.
7. Validate customer path and monitoring recovery.
8. Write RCA with evidence, not guesses.
