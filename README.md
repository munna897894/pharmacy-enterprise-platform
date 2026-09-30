# Pharmacy Enterprise Platform

Production-style educational retail pharmacy microservices monorepo.

## Current stage

The platform includes:

- root Maven parent/aggregator POM
- 11 service modules plus gateway
- service-to-service HTTP calls for order/payment checks
- a full Docker Desktop Kubernetes deployment chart and an on-demand AWS sandbox
- shared `platform/event-contracts`
- shared `platform/test-support`
- Micrometer/OpenTelemetry instrumentation and open-source observability configuration

Canonical deployment diagrams:

- [Local Docker Desktop Kubernetes](docs/architecture-local.md)
- [Temporary AWS EKS sandbox](docs/architecture-cloud.md)

## Baseline stack

| Technology | Version |
|---|---:|
| Java | 21 |
| Maven Wrapper | 3.9.9 |
| Spring Boot | 3.5.16 |
| Spring Cloud | 2025.0.3 |

## Repository layout

```text
.
├── docs/
├── learning/
├── platform/
│   ├── event-contracts/
│   ├── observability/
│   └── test-support/
├── infra/
│   ├── compose/
│   ├── helm/
│   ├── k8s/
│   ├── local/
│   └── terraform/
├── prompts/
├── services/
│   ├── api-gateway/
│   ├── auth-service/
│   ├── audit-service/
│   ├── customer-service/
│   ├── inventory-service/
│   ├── notification-service/
│   ├── order-service/
│   ├── payment-service/
│   ├── pharmacy-service/
│   ├── prescription-service/
│   ├── product-service/
│   └── external-mock-service/
├── .env.example
├── .editorconfig
├── .gitignore
├── Makefile
├── mvnw
├── mvnw.cmd
└── pom.xml
```

## IntelliJ setup

1. Open the repository root, not an individual module.
2. Import the root `pom.xml` as a Maven project.
3. Set the project SDK and Maven runner JDK to **Java 21**.
4. Enable annotation processing only if a future module requires it; the bootstrap stage does not.
5. Use the root Maven tool window to run lifecycle goals for the full reactor build.

## Common commands

To run and test the platform yourself (full local Kubernetes and an independent AWS sandbox), follow
[`docs/self-run-guide.md`](docs/self-run-guide.md).

The optional Dynatrace/Splunk setup is prepared but disabled. See
[`docs/12-commercial-observability.md`](docs/12-commercial-observability.md);
following the normal commands below does not activate either vendor.

```bash
./mvnw -q -DskipTests validate
./mvnw clean verify
./mvnw -pl services/order-service -am test -DskipITs
./mvnw -pl services/product-service spring-boot:run
./mvnw -pl services/external-mock-service spring-boot:run
```

Default ports:

- `api-gateway`: `8080`
- `auth-service`: `8081`
- `product-service`: `8082`
- `customer-service`: `8083`
- `pharmacy-service`: `8084`
- `inventory-service`: `8085`
- `prescription-service`: `8086`
- `order-service`: `8087`
- `payment-service`: `8088`
- `notification-service`: `8089`
- `audit-service`: `8090`
- `external-mock-service`: `8080` (inside the cluster; Compose maps it to host `8099`)

## Notes

- `ddl-auto=validate` is preconfigured for persistence modules.
- Actuator exposes `health`, `info`, `metrics`, `prometheus`.
- Runtime configuration is environment-driven. `local` and `test` are the
  primary explicit Spring profiles; non-local runtime profiles enable
  structured JSON logging.
- Local Compose host URLs:
  - `http://localhost:8080` gateway
  - `http://localhost:8081` auth-service
  - `http://localhost:8082` product-service
  - `http://localhost:8085` inventory-service
  - `http://localhost:8087` order-service
  - `http://localhost:8088` payment-service
  - `http://localhost:8089` notification-service
  - `http://localhost:8090` audit-service
- Canonical local Kubernetes dependencies:
  - project-scoped native MySQL: `127.0.0.1:3308`
  - project-scoped native Kafka host listener: `127.0.0.1:19092`
  - Kafka address advertised to pods: `host.docker.internal:29092`
- Legacy Compose MySQL host port: `3307`
- Compose DNS names:
  - `api-gateway`, `auth-service`, `product-service`, `inventory-service`, `order-service`, `payment-service`, `notification-service`, `audit-service`, `mysql`, `redis`, `kafka`
- Prompt 08 compose reset is guarded by `./scripts/reset-demo-data.sh --yes-i-know`.
