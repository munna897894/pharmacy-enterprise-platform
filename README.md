# Pharmacy Enterprise Platform

Bootstrap foundation for a production-style educational retail pharmacy microservices monorepo.

## Current stage

Prompts 01-06 are in place with:

- root Maven parent/aggregator POM
- 11 service modules plus gateway
- service-to-service HTTP calls for order/payment checks
- Docker Compose and Kubernetes deployment scaffold
- shared `platform/event-contracts`
- shared `platform/test-support`
- Prometheus/Grafana/OpenTelemetry observability scaffold

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
│   └── test-support/
├── prompts/
├── services/
│   ├── api-gateway/
│   ├── auth-service/
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

To run and test the platform yourself (local Compose stack and the AWS sandbox slice), follow
[`docs/self-run-guide.md`](docs/self-run-guide.md).

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
- `product-service`: `8081`
- `customer-service`: `8082`
- `pharmacy-service`: `8084`
- `inventory-service`: `8085`
- `prescription-service`: `8086`
- `order-service`: `8087`
- `payment-service`: `8088`
- `notification-service`: `8089`
- `audit-service`: `8090`
- `external-mock-service`: `8089`

## Notes

- `ddl-auto=validate` is preconfigured for persistence modules.
- Actuator exposes `health`, `info`, `metrics`, `prometheus`.
- Profiles currently supported in runnable modules: `local`, `compose`, `k8s`, `test`.
- Local Compose host URLs:
  - `http://localhost:8080` gateway
  - `http://localhost:8081` auth-service
  - `http://localhost:8082` product-service
  - `http://localhost:8085` inventory-service
  - `http://localhost:8087` order-service
  - `http://localhost:8088` payment-service
  - `http://localhost:8089` notification-service
  - `http://localhost:8090` audit-service
- Local MySQL host port: `3307`
- Compose DNS names:
  - `api-gateway`, `auth-service`, `product-service`, `inventory-service`, `order-service`, `payment-service`, `notification-service`, `audit-service`, `mysql`, `redis`, `kafka`
- Prompt 08 compose reset is guarded by `./scripts/reset-demo-data.sh --yes-i-know`.
