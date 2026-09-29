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
├── gateway/
│   └── api-gateway/
├── platform/
│   ├── event-contracts/
│   └── test-support/
├── contracts/
│   ├── openapi/
│   ├── asyncapi/
│   └── json-schema/
├── infra/
│   ├── compose/
│   ├── kafka/
│   ├── mysql/
│   ├── observability/
│   ├── kubernetes/
│   ├── helm/
│   ├── argocd/
│   └── terraform/
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

Each application supports:

- `local`: direct IntelliJ/Maven run with infrastructure on localhost
- `compose`: Docker DNS names
- `k8s`: Kubernetes service DNS and mounted configuration
- `test`: isolated test configuration using Testcontainers or mocks

Secrets must come from environment variables or mounted secrets. Commit only `.env.example`, never `.env`.

