# Prompt 11 — GitHub Actions, quality and GitOps

## Give Copilot these files

- `docs/03-repository-structure.md`
- `docs/04-technology-versions.md`
- `docs/09-testing-strategy.md`

## Prompt

```text
Create secure GitHub Actions workflows for the Maven monorepo.

Pull-request workflow:
- checkout with minimal permissions;
- setup Java 21 and Maven cache;
- validate/compile/unit tests;
- integration tests with Testcontainers;
- JaCoCo and static/architecture checks;
- dependency vulnerability scan;
- secret scan using an appropriate action/tool;
- build Docker images only when relevant;
- Trivy filesystem/image scans;
- upload test/coverage reports as artifacts;
- concurrency cancellation for superseded PR runs.

Main/release workflow:
- repeat required quality gates;
- build immutable versioned images labeled with commit SHA;
- authenticate to a configurable registry using GitHub secrets/OIDC, never hard-coded credentials;
- push only on protected main/tag conditions;
- update or prepare GitOps image values without directly running unreviewed kubectl against production.

Also add:
- Dependabot config for Maven, GitHub Actions and Docker;
- CODEOWNERS/sample PR template if useful;
- local SonarQube Compose profile and documented optional CI integration;
- Argo CD application manifests/concept documentation targeting a separate deployment-config path;
- branch protection recommendations and environment approval-gate instructions.

Pin third-party actions to trusted versions/SHAs according to current GitHub guidance. Use least-privilege workflow permissions. Validate YAML and document required repository secrets/variables without values.
```

## Acceptance criteria

- Fork/PR workflow cannot access deployment secrets.
- Build is reproducible from clean checkout.
- Test failure blocks image publication.
- High/critical unaccepted vulnerability blocks release.
- Image tag is immutable commit SHA, not only `latest`.
- Deployment approval/environment boundary is documented.
- Dependabot covers Maven, actions and Docker.

## Study

- CI vs CD
- Workflow/event/job/step/action
- Cache vs artifact
- OIDC vs long-lived cloud keys
- Supply-chain risks and action pinning
- Push deployment vs GitOps reconciliation
- Rollback using immutable images

