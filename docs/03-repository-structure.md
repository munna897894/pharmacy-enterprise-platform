# Repository structure

Copilot must converge on this structure. A prompt may create only the directories needed for its current stage.

```text
pharmacy-enterprise-platform/
├── .github/
│   ├── copilot-instructions.md
│   ├── dependabot.yml
│   └── workflows/
├── docs/
├── prompts/
├── learning/
├── services/
│   ├── api-gateway/
│   ├── auth-service/
│   ├── product-service/
│   ├── customer-service/
│   ├── pharmacy-service/
│   ├── inventory-service/
│   ├── prescription-service/
│   ├── order-service/
│   ├── payment-service/
│   ├── notification-service/
│   ├── audit-service/
│   └── external-mock-service/
├── platform/
│   ├── event-contracts/
│   ├── observability/
│   └── test-support/
├── contracts/
│   ├── openapi/
│   ├── asyncapi/
│   └── json-schema/
├── infra/
│   ├── compose/
│   ├── grafana/
│   ├── local/
│   ├── kubernetes/
│   ├── k8s/
│   ├── helm/
│   ├── argocd/
│   ├── prometheus/
│   └── terraform/
├── interview-guides/
├── k8s/
├── postman/
├── scripts/
├── .env.example
├── .gitignore
├── Makefile
├── mvnw
├── mvnw.cmd
├── pom.xml
└── README.md
```

## Maven rules

- Root `pom.xml` is an aggregator and centralizes plugin/dependency management.
- Every service produces its own executable JAR and Docker image.
- `platform/event-contracts` contains immutable event-envelope and event-payload records only. It must not contain entities, repositories, service implementations or web DTOs.
- `platform/test-support` contains reusable Testcontainers setup/builders only; production modules must not depend on it.
- Business services do not import one another as Maven modules.
- The gateway must not depend on business-service implementation modules.

## Typical service structure

```text
src/main/java/com/jagapathi/pharmacy/<service>/
├── <Service>Application.java
├── api/
│   ├── <Feature>Controller.java
│   ├── request/
│   ├── response/
│   └── advice/
├── application/
│   ├── <Feature>Service.java
│   ├── command/
│   └── mapper/
├── domain/
│   ├── model/
│   ├── exception/
│   └── repository/
└── infrastructure/
    ├── persistence/
    ├── messaging/
    ├── client/
    ├── security/
    └── config/
```

Pragmatic simplification is allowed for a very small service, but controllers, persistence entities and Kafka code must not be mixed into one package.

## Configuration profiles

Applications use `local` and `test` profiles where profile-specific behavior
is required. Compose and Kubernetes primarily configure the same images through
environment variables, ConfigMaps and mounted secret files rather than relying
on mandatory `compose`/`k8s` Spring profiles. Non-local profiles enable
structured JSON logging.

Secrets must come from environment variables or mounted secrets. Commit only
`.env.example`, never `.env`. Local Kubernetes secret material is generated
from gitignored inputs; AWS workloads retrieve scoped values from Secrets
Manager through IRSA-backed init containers.
