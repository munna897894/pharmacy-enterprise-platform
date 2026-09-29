# 01 — Monorepo Foundations (Prompt 01)

## Summary

A single Maven reactor build hosts every service and shared library as a module, using `spring-boot-starter-parent` (3.5.16) as the effective parent, with the Spring Cloud BOM (2025.0.3) imported for cross-compatible versions. The goal: one consistent build/toolchain, but each module still builds and runs independently, and business services are architecturally forbidden from depending on each other.

## Diagram — module tree

```
pharmacy-enterprise-platform (pom, packaging=pom)
│
├── platform/
│   ├── event-contracts      (library: shared DomainEvent<T> + payload records)
│   └── test-support         (library: shared test/ArchUnit helpers)
│
└── services/
    ├── api-gateway           (WebFlux)
    ├── auth-service          (MVC)
    ├── product-service       (MVC)
    ├── customer-service      (MVC)
    ├── pharmacy-service      (MVC)
    ├── inventory-service     (MVC)
    ├── prescription-service  (MVC)
    ├── order-service         (MVC)
    ├── payment-service       (MVC)
    ├── notification-service  (MVC)
    ├── audit-service         (MVC)
    └── external-mock-service (MVC, test double only)
```

Each service module has its own POM inheriting from the root, and its own runnable `main()` / `@SpringBootApplication`. Nothing here is a "shared framework module" beyond `event-contracts` and `test-support` — those are intentionally thin.

## Key files

| File | Purpose |
|---|---|
| `/pom.xml` | Root aggregator + parent. Declares all 13 modules, dependencyManagement (event-contracts, test-support, Spring Cloud BOM), plugin management (compiler, surefire, failsafe, jacoco), and the Enforcer plugin rule requiring Java 21+. |
| `platform/event-contracts/.../DomainEvent.java` | The one shared event envelope record used by every Kafka producer/consumer across the platform. |
| `platform/test-support/.../ArchitectureTestSupport.java` | Placeholder/marker class; actual ArchUnit rules live per-service (see below), not centralized. |
| `services/*/src/test/java/.../ArchitectureRulesTest.java` | Per-service ArchUnit test — the actual enforcement mechanism for "no cross-service dependency." |

## Core concepts

| Concept | Explanation | Why it matters |
|---|---|---|
| Parent vs aggregator POM | This root POM is *both*: `<packaging>pom</packaging>` makes it an aggregator (defines `<modules>` to build), while also being a Maven **parent** any module can inherit from for shared config (though here modules actually inherit from `spring-boot-starter-parent` directly with this POM providing dependencyManagement instead — check actual per-service parent tag). | Confusing distinction in interviews — aggregator = "what to build together"; parent = "what config to inherit." |
| `dependencyManagement` vs `dependencies` | Root POM only *manages* versions (event-contracts, test-support, Spring Cloud BOM); it doesn't force every module to include them. | Prevents version drift without forcing unwanted transitive deps. |
| Maven Enforcer plugin | Fails the build if Java < 21 or Maven < 3.9.0. | Catches environment drift before a confusing runtime failure. |
| Multi-module reactor build | `./mvnw clean verify` builds all modules in dependency order in one pass. | Single source of truth for "does the whole platform compile and pass tests." |
| ArchUnit boundary test | Each service has a `noClasses()... resideInAPackage("com.jagapathi.pharmacy.<service>..")` rule banning imports from every other service's package. | This is the actual mechanism (not just convention) that prevents service coupling — a real interview differentiator vs. "just don't do it." |
| JDK 17 workaround profile | A Maven profile auto-activates `maven.compiler.release=17` if JDK 17 is detected, else defaults to 21. | Shows defensive tooling for mixed local environments. |

## Common interview Q&A

**Q: Why put everything in one Maven repo (monorepo) instead of separate repos per service?**
A: Simplifies shared BOM/version management and cross-service architecture testing (ArchUnit can scan all service packages in one pass) while services still deploy and scale independently — each has its own Docker image and Kubernetes deployment. The monorepo is a *build-time* convenience, not a *runtime* coupling.

**Q: How do you stop service A from importing service B's internal classes if they're in the same repo?**
A: ArchUnit test per service, `noClasses().that().resideInAPackage("<own package>..").should().dependOnClassesThat().resideInAnyPackage(<every other service package>)`. It runs as part of the normal test phase, so a violation fails the build, not just a code review comment.

**Q: What's in `platform/event-contracts` and why is it so small?**
A: Just the `DomainEvent<T>` envelope (eventId, eventType, version, aggregateType/Id, occurredAt, producer, correlationId, causationId, actorId, payload) plus event payload types. It's deliberately not a "shared business logic" library — it only defines the wire contract for Kafka messages, so services stay decoupled at the persistence/business-logic level.

**Q: Why `ddl-auto=validate` instead of `update` or `create`?**
A: Flyway owns schema changes; Hibernate should only validate that entity mappings match the actual schema, never silently mutate it. This is a common production-safety practice enforced from day one.

## Gotchas / real issues encountered

- Enforcer plugin can fail confusingly if a contributor is on JDK 17 instead of 21 without realizing it — the `jdk17-workaround` profile exists specifically to make partial local development still work, but it's a stopgap, not the intended target.
- Because this is a reactor build, a single broken module (e.g., a bad Flyway migration in one service) can make it look like the "whole build is broken" — but in this repo, per-module builds also work independently (`./mvnw -pl services/product-service ...`), which is important for isolating failures fast.

## Trace-through: "What happens when I run `./mvnw clean verify` at the root?"

1. Maven reads root `pom.xml`, resolves the module list, computes build order from inter-module dependencies (platform libs build before services that depend on them).
2. Enforcer plugin runs first — checks Java/Maven version.
3. Each module compiles, runs unit tests (Surefire), then (if configured) integration tests (Failsafe).
4. JaCoCo instruments and reports coverage per module during `verify`.
5. ArchUnit tests run as normal JUnit tests inside each service's test phase — a violation here fails `verify` just like any other test failure.
6. If everything passes, the reactor reports success across all 13 modules.
