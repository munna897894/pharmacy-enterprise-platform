# Technology and version policy

## Pinned baseline

Use this compatibility family unless an official BOM requires a patch adjustment:

| Technology | Baseline | Policy |
|---|---:|---|
| Java | 21 LTS | Required language/runtime |
| Maven | 3.9+ via wrapper | Commit the wrapper |
| Spring Boot | 3.5.16 | Use the latest available 3.5.x patch if 3.5.16 is unavailable |
| Spring Cloud | 2025.0.3 | 2025.0.x is the official train for Boot 3.5.x |
| Apache Kafka broker | 4.3.1 KRaft locally; 3.9.1 image in AWS | Pin deployment-specific version; no `latest` |
| MySQL | 8.4 LTS | Pin major/minor image |
| Redis | 7.4 | Pin major/minor image |
| Docker Compose | v2 | Use `docker compose`, not legacy `docker-compose` |
| Kubernetes | Docker Desktop current | Record actual client/server versions in learning notes |
| Helm | 3.x | Pin CI installer action/version |
| Terraform | 1.x | Commit `.terraform.lock.hcl`; never commit state/secrets |

Official compatibility reference: https://spring.io/projects/spring-cloud

## Dependency-management rules

- Let the Spring Boot parent/BOM manage Spring dependencies.
- Import `spring-cloud-dependencies` once at the parent level.
- Use a Testcontainers BOM if the Boot BOM does not manage the selected modules consistently.
- Pin Docker images and GitHub Actions to explicit versions/tags.
- Do not specify versions for dependencies already controlled by a BOM unless there is a documented reason.
- Run `./mvnw versions:display-dependency-updates` only as a report; do not accept bulk upgrades during the 15-day build.

## Core application dependencies

Choose per module; do not place every dependency in every service.

- `spring-boot-starter-web`
- `spring-boot-starter-validation`
- `spring-boot-starter-data-jpa`
- `spring-boot-starter-security`
- `spring-boot-starter-oauth2-resource-server`
- `spring-boot-starter-actuator`
- `spring-kafka`
- `spring-boot-starter-data-redis`
- Spring Cloud Gateway WebFlux starter for gateway only
- Spring Cloud OpenFeign where synchronous internal calls are required
- Resilience4j Spring Boot integration
- Flyway MySQL support
- MySQL Connector/J runtime driver
- Micrometer Prometheus registry
- OpenTelemetry/Micrometer tracing bridge chosen consistently across modules
- Springdoc OpenAPI starter compatible with Boot 3.5.x

## Test dependencies

- `spring-boot-starter-test`
- `spring-security-test`
- `spring-kafka-test`
- Testcontainers JUnit Jupiter, MySQL, Kafka and Redis-compatible container support
- Awaitility
- WireMock or MockWebServer for external HTTP behavior
- ArchUnit for selected architecture rules

## Version verification gate

Before generating modules, Copilot must:

1. Check that Spring Boot and Spring Cloud versions are compatible.
2. Produce an effective POM without dependency convergence errors.
3. Run `./mvnw -q -DskipTests validate`.
4. Report any unavailable artifact rather than substituting an unrelated dependency.
5. Save the resolved versions in the root README.

Do not migrate this learning project to Spring Boot 4 during the 15-day implementation. Boot 3.5.x is intentionally retained to match the planned enterprise stack and reduce migration noise.
