# 08 — Docker and the Local Platform (Prompt 08)

## Summary

The canonical local runtime is the **full Docker Desktop Kubernetes fleet**, not a root `docker-compose.yml`. All twelve JVM workloads (gateway, ten DB-owning services and external mock) plus Redis run in namespace `pharmacy`; observability runs separately in `pharmacy-observability`. Project-scoped **host-native** MySQL 8.4 (`3308`) and Kafka 4.x KRaft (`localhost:19092` for host tools, `host.docker.internal:29092` for pods) are not Compose containers. `infra/compose/compose.yml` remains a legacy **full-fleet alternative**, not a partial stack; never run both against the same data/ports.

## Local topology

```text
client -> kubectl port-forward localhost:18080 -> api-gateway ClusterIP :8080
             gateway management :9081, other services internal only
pharmacy namespace:
  auth :8081 | product :8082 | customer :8083 | pharmacy :8084
  inventory :8085 | prescription :8086 | order :8087 | payment :8088
  notification :8089 | audit :8090 | external mock :8080 | Redis :6379
  -> host.docker.internal:3308 (ten schema/user pairs, not shared tables)
  -> host.docker.internal:29092 (Kafka event + retry + DLT topics)
pharmacy-observability namespace:
  Prometheus, Grafana, Loki, Tempo, Alloy, OpenTelemetry Collector, Kafka exporter
```

## Key files and commands

| File | What it shows |
|---|---|
| `docs/self-run-guide.md`, `docs/architecture-local.md` | Canonical deployment and stop/verification steps. |
| `scripts/local-mysql-{init,verify}.sh`, `scripts/local-kafka-{start,init}.sh` | Isolated native dependencies under ignored `.local/` state; no Homebrew default data directory. |
| `infra/local/kafka/server.properties` | Separate host/pod advertised listeners. |
| `scripts/k8s-local-secret.sh`, `infra/helm/pharmacy-platform/values-local.yaml` | Local-only DB passwords/RSA fixture mount and twelve service ports. |
| `scripts/local-k8s-test.sh` | Local Kubernetes full-fleet E2E with temporary gateway port-forward. |
| `infra/compose/compose.yml`, `infra/compose/compose-observability.yml` | Legacy full-fleet Docker alternative with its own dependencies and optional observability overlay. |

Typical order: initialize/verify MySQL, start Kafka in a separate terminal, initialize topics, build twelve `pharmacy/<service>:local` images, generate the local Secret, validate/render and install the application Helm chart, then run `sh scripts/local-k8s-test.sh`. Follow `docs/self-run-guide.md` for exact prerequisites and commands. Run observability with its **own Helm release**, not Compose service DNS. Checked-in fixture credentials are for local learning only.

## Core concepts and interview Q&A

| Concept | Why it matters |
|---|---|
| Host vs pod Kafka listener | A broker advertising `localhost:19092` to a pod makes subsequent metadata connections loop back to the pod. The pod-facing listener advertises `host.docker.internal:29092`. |
| Schema/user isolation | One project MySQL server saves local resources; ten least-privilege schema users still enforce service data ownership. |
| Kubernetes DNS | Internal HTTP uses Service names/ports, not Eureka or host port mappings. |
| Compose alternative | Compose service DNS/volumes/ports belong to its own full fleet; Compose's observability scraper is not the Kubernetes scraper. |

**Q: What starts locally today?**
A: Docker Desktop Kubernetes runs the full application fleet, Redis and an in-cluster external mock; MySQL and Kafka are independent native host processes. Observability uses a separate namespace/release.

**Q: Why two Kafka listener ports?**
A: Host tools connect to `localhost:19092`. Pods bootstrap and follow metadata via `host.docker.internal:29092`; advertising the host-only address to pods breaks them.

**Q: Does Compose omit customer, pharmacy, prescription or external mock?**
A: No. `infra/compose/compose.yml` is a maintained full-fleet **legacy alternative**. The old root `docker-compose.yml` and partial topology descriptions are historical, not today's runtime instructions.

**Q: Where do credentials live?**
A: Local `.env` and generated local Kubernetes Secrets are ignored/private; the chart reads DB passwords per service and mounts local RSA fixtures only in auth. Cloud secrets follow a different Secrets Manager/IRSA path.

## Gotchas / trace-through

- MySQL `3308` is neither the user's default MySQL `3306` nor legacy Compose's `3307`. Do not point a Kubernetes pod at Compose's `mysql` hostname.
- Use `kubectl -n pharmacy port-forward svc/api-gateway 18080:8080`; application Services remain ClusterIP. Gateway management metrics are on `9081`.
- The chart has startup/readiness/liveness probes and default resource requests/limits. A healthy container process is not proof the service is ready.

**Trace:** a local client hits `localhost:18080`; gateway routes to `order-service:8087`, which calls `inventory-service:8085` for pre-check and writes to host MySQL via `host.docker.internal:3308`. Relays send to the Kafka pod-facing listener; in-cluster Redis and mock serve their own roles.
