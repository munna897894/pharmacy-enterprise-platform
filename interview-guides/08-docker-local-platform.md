# 08 — Docker and the Local Platform (Prompt 08)

## Summary

Local development runs via a single root `docker-compose.yml` plus a companion `.env` file — one shared MySQL container (per-service schema + user), one Redis, one single-node KRaft-mode Kafka broker (no Zookeeper), and Spring Boot service containers built from per-service `Dockerfile`s. This is a real, working setup for the services it covers — but a careful read of the actual file uncovers two genuine configuration bugs (container port mismatches) and a real coverage gap (4 of 12 services are entirely absent from the compose file), all confirmed directly against each service's `application.yml`.

## Diagram — the real container topology

```
                       .env  (git-ignored; .env.example is the checked-in template)
                        │  (env_file: .env on every service)
                        ▼
 ┌─────────┐     ┌─────────┐     ┌──────────────────────┐
 │  mysql  │     │  redis  │     │  kafka (KRaft, single │
 │ :3307→  │     │ :6379   │     │  node, no ZooKeeper)  │
 │  3306   │     │         │     │  :9092                │
 └────┬────┘     └────┬────┘     └───────────┬───────────┘
      │ (healthcheck: mysqladmin ping)        │ (healthcheck: kafka-broker-api-versions)
      │ one schema+user per service           │
      ▼                                        ▼
 auth-service :8081 ──depends_on(mysql healthy, kafka healthy)
 product-service :8082→8081 (host:container REMAP — see gotcha)
       ──depends_on(mysql healthy, redis healthy)
 inventory-service :8085 ──depends_on(mysql healthy, kafka healthy)
 order-service :8087 ──depends_on(mysql, kafka healthy; inventory-service STARTED not healthy)
 payment-service :8088 ──depends_on(mysql, kafka healthy; order-service STARTED not healthy)
 notification-service :8090:8090 ──depends_on(kafka healthy)   ⚠ REAL PORT MISMATCH (see gotcha)
 audit-service :8091:8091 ──depends_on(mysql, kafka healthy)    ⚠ REAL PORT MISMATCH (see gotcha)
 api-gateway :8080 ──depends_on(redis healthy; auth/inventory/order/payment STARTED not healthy)

 ⚠ NOT PRESENT in docker-compose.yml at all:
   customer-service, pharmacy-service, prescription-service, external-mock-service
```

## Key files

| File | What it shows |
|---|---|
| `docker-compose.yml` (repo root) | The full local topology: `mysql`, `redis`, `kafka` infra containers plus 8 of the 12 application services (see gap below), each with `env_file: .env`, service-specific `environment:` overrides, `depends_on` with `condition: service_healthy` or `service_started`, and explicit host:container port mappings. |
| `infra/compose/mysql-init/01-create-schemas.sql` | Mounted at `/docker-entrypoint-initdb.d` — runs once on first MySQL container startup to create the per-service schemas/users referenced by each service's `DB_NAME`/`DB_USER` env vars. |
| `.env.example` (repo root) | The checked-in template listing every required env var (`MYSQL_ROOT_PASSWORD`, `KAFKA_KRAFT_CLUSTER_ID`, and `<SERVICE>_DB_NAME/USER/PASSWORD` for each service) — real `.env` is git-ignored, following 12-factor "config lives in the environment, never in source control." |
| `services/*/Dockerfile` (per service, e.g. `product-service/Dockerfile`) | Standard multi-stage-style build producing the `pharmacy/<service>:local` images referenced by `docker-compose.yml`. |
| `services/notification-service/src/main/resources/application.yml` | `server.port: 8089` hardcoded, no env-var override — directly contradicts the compose file's `8090:8090` mapping (see gotcha). |
| `services/audit-service/src/main/resources/application.yml` | `server.port: 8086` hardcoded — contradicts the compose file's `8091:8091` mapping (see gotcha). |

## Core concepts

| Concept | Explanation | Why it matters |
|---|---|---|
| 12-factor config via environment | Every service reads DB host/name/user/password and Kafka bootstrap servers from environment variables (`${PRODUCT_DB_NAME:?required}` etc.), never hardcoded, with `.env` supplying local values. | This is the standard "config, credentials, and constants" factor of 12-factor apps — the exact same container image can run in any environment by changing only the env vars, never rebuilding. |
| `:?required` bash-style default syntax in compose | `${AUTH_DB_PASSWORD:?required}` fails the compose startup immediately with a clear error if the variable is unset, rather than silently starting with an empty value. | Fails fast and loud instead of allowing a service to boot with a blank DB password and fail confusingly later. |
| One shared MySQL instance, N schemas/users (local only) | A single `mysql` container hosts all services' data, each with its own schema and DB user (least privilege — `order_user` cannot touch `pharmacy_payment`). | Locally this saves resources versus 10+ separate MySQL containers; the "own your data" boundary is still enforced at the schema/user level even though the physical server is shared — this is explicitly a local-dev simplification, not the production topology (see 12-aws-terraform guide for the "real" per-service RDS instance concept). |
| Container networking / service discovery by name | Services reference each other as `http://inventory-service:8085`, `mysql`, `redis`, `kafka` — Docker Compose's default bridge network provides DNS resolution by service name automatically, no manual `/etc/hosts` or IP wiring needed. | Directly mirrors how Kubernetes Service DNS works (see 09 guide) — the same hostname-based addressing pattern is used in both environments, which is why no service-discovery tool (Eureka/Consul) is needed in either. |
| Healthcheck-gated startup ordering | `depends_on: mysql: condition: service_healthy` (not just "started") ensures MySQL/Kafka/Redis are actually accepting connections before a dependent service's container even starts, using each infra container's own `healthcheck:` block (`mysqladmin ping`, `redis-cli ping`, `kafka-broker-api-versions.sh`). | Prevents the classic "app container starts before the DB is ready and crash-loops on the first connection attempt" race condition — a real and common Docker Compose pitfall this file correctly avoids for infra dependencies. |
| Weaker `service_started` dependency between app services | `order-service` depends on `inventory-service` with `condition: service_started` (not `service_healthy` — app services here have no compose-level healthcheck defined at all). | "Started" only means the container process began; it does NOT mean the Spring Boot app finished starting up and is accepting traffic — so a fast-starting `order-service` container could still send its first request to `inventory-service` before Spring Boot has fully initialized there, unlike the stronger guarantee given to the infra containers. |
| KRaft-mode Kafka (no ZooKeeper) | `KAFKA_CFG_PROCESS_ROLES: broker,controller` with `KAFKA_CFG_CONTROLLER_QUORUM_VOTERS` — a single container acts as both broker and controller using Kafka's newer KRaft consensus protocol. | Reflects the modern (post Kafka 3.x/4.x) recommended deployment mode; ZooKeeper is deprecated for new Kafka clusters, so this is the "current industry standard" choice, not a legacy holdover. |
| Named volumes for stateful data | `mysql-data`, `redis-data`, `kafka-data` are Docker named volumes, not bind mounts. | Data survives `docker compose down` (without `-v`) and container restarts/recreation, while staying isolated from the host filesystem — the standard pattern for local persistent state in Compose. |

## Common interview Q&A

**Q: Why does this project use one MySQL container with multiple schemas instead of one container per service?**
A: It's a deliberate local-development simplification to save resources (RAM/CPU/disk on a single dev machine) while still preserving the "each service owns its data" boundary at the schema+user level — `product_user` has no grants on `pharmacy_order`, so the isolation is real even though the physical server is shared. In a "real" cloud deployment this would typically become one RDS instance per service (see the AWS/Terraform guide) for full physical isolation, better independent scaling, and blast-radius containment.

**Q: How do services find each other without a service registry like Eureka?**
A: Docker Compose's default bridge network gives every container a DNS-resolvable hostname equal to its service name (`inventory-service`, `mysql`, `kafka`, etc.) — so `http://inventory-service:8085` just resolves via Docker's embedded DNS. This is the same mechanism Kubernetes uses (ClusterIP Service DNS), which is exactly why this platform never needed Eureka/Consul in either environment — DNS-based discovery is sufficient when you control the orchestration platform.

**Q: What's the difference between `service_healthy` and `service_started` in `depends_on`, and does this file use both correctly?**
A: `service_healthy` waits for the dependency's own `healthcheck:` to pass (a real readiness signal); `service_started` only waits for the container process to launch. The compose file correctly uses `service_healthy` for the three infra containers (mysql/redis/kafka), which all define real healthchecks — but application services (order-service depending on inventory-service, api-gateway depending on auth-service, etc.) only use `service_started`, because none of the Spring Boot services have a compose-level healthcheck defined. In practice this means there's still a possible race where a downstream app service receives traffic before an upstream app service has finished Spring Boot startup — weaker than the infra-tier guarantee.

**Q: Walk me through what happens on `docker compose up` from cold start.**
A: MySQL, Redis, and Kafka containers start first (no dependencies); MySQL runs `01-create-schemas.sql` on first boot to create every service's schema/user. Each container's healthcheck begins polling. Once mysql/kafka report healthy, auth-service and inventory-service (etc.) start; once redis is healthy, product-service starts. order-service waits for inventory-service to merely start (not be ready) before starting itself. api-gateway starts last, once its declared dependencies have started.

**Q: If you were asked to harden this compose file for a more realistic local environment, what would you change?**
A: Add real HTTP-based healthchecks (hitting each Spring Boot service's `/actuator/health` endpoint) to every application service, and upgrade the app-to-app `depends_on` conditions from `service_started` to `service_healthy` so downstream services never receive traffic from an upstream service that hasn't finished initializing — plus fix the two port-mapping bugs and add the four missing services (see Gotchas).

## Gotchas / real findings

- **notification-service's port mapping is wrong.** `docker-compose.yml` maps `8090:8090`, but `notification-service/application.yml` hardcodes `server.port: 8089` with no environment-variable override anywhere in the compose file (`SERVER_PORT` is never set for this service). The container process actually listens on `8089` internally, so the host-side mapping to container port `8090` doesn't reach the app — `http://localhost:8090` would not work as intended from the host.
- **audit-service has the identical bug.** Compose maps `8091:8091`, but `audit-service/application.yml` hardcodes `server.port: 8086`, again with no `SERVER_PORT` override present anywhere in the compose file. Same class of mismatch, same root cause.
- **Four of twelve services are completely absent from `docker-compose.yml`**: `customer-service`, `pharmacy-service`, `prescription-service`, and `external-mock-service` have no entry at all — meaning `docker compose up` does not give you a fully running platform; you'd need to run those four separately (e.g., via Maven/IDE) to exercise end-to-end flows that touch customer, pharmacy, or prescription data, or to simulate the payment gateway locally through Compose alone.
- **App-to-app `depends_on` uses the weaker `service_started` condition**, not `service_healthy` — real (if usually harmless in practice) startup-race risk, as explained in the Q&A above.
- **payment-service's dependency on external-mock-service is entirely implicit** — since external-mock-service isn't in the compose file at all, `PaymentGatewayClient`'s calls to it will fail outright unless that service is started separately outside Compose.

## Trace-through: `docker compose up` for the order → payment path, from cold start

1. `mysql`, `redis`, `kafka` containers start with no dependencies; MySQL executes `01-create-schemas.sql`, creating (among others) the `pharmacy_order`/`order_user` and `pharmacy_payment`/`payment_user` schema+user pairs.
2. Once `mysql` and `kafka` report `service_healthy`, `inventory-service` and `auth-service` start (both depend only on mysql+kafka healthy).
3. `order-service` starts once mysql+kafka are healthy AND `inventory-service`'s container has merely *started* (not necessarily finished Spring Boot init) — a real, if narrow, window where the very first order-creation request could hit a not-yet-ready inventory-service.
4. `payment-service` starts once mysql+kafka are healthy AND `order-service` has started.
5. `api-gateway` starts last, once redis is healthy and auth/inventory/order/payment have all at least started.
6. A client request to `POST /api/v1/orders` through the gateway now depends on `order-service` calling `inventory-service` over the Docker bridge network by container name (`http://inventory-service:8085`) — this works because Compose's embedded DNS resolves the service name, exactly like Kubernetes Service DNS would.
7. Because `external-mock-service` was never included in this compose file, any subsequent payment attempt against `payment-service` would fail when `PaymentGatewayClient` tries to reach it — a concrete, reproducible consequence of the missing-service gap above.
