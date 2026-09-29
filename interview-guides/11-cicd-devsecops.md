# 11 — CI/CD and DevSecOps

## Implemented pipeline

```text
pull_request
  ├─ Maven verify: compile, unit/Failsafe tests, architecture tests, JaCoCo reports
  ├─ Trivy filesystem: dependency, misconfiguration, and secret scan
  └─ On service/build changes: build each image and Trivy-scan it

protected main or v* tag push
  ├─ Repeat Maven verify and filesystem scan
  ├─ Build and scan all service images
  └─ ghcr-release environment approval
       └─ Publish GHCR images tagged with full commit SHA (and v* on releases)
            └─ Upload per-service image-reference artifacts for GitOps review

Reviewed image-SHA change in a separate deployment-config repository
  └─ Argo CD reconciles staging; no CI kubectl-to-production step
```

## Key files

| File | Purpose |
|---|---|
| `.github/workflows/pr.yml` | Fork-safe read-only quality/security gates, conditional image builds/scans, artifacts, and superseded-PR cancellation. |
| `.github/workflows/release.yml` | Repeats quality/security/image gates; scopes `packages: write` to the GHCR publish job and waits for the `ghcr-release` environment. |
| `.github/dependabot.yml` | Weekly grouped Maven, GitHub Actions, Dockerfile and Compose update PRs, with ignore rules for Boot 3.5 / Java 21 constraints. |
| `CODEOWNERS` | Routes code review to the repository owner; protect `main` to require CODEOWNERS approval. |
| `infra/compose/compose.yml` | Optional `sonar` profile; SonarQube does not join normal local startup. |
| `infra/argocd/application-staging.example.yaml` | Example Argo CD application pointed at a separate deployment-config repository/path. |
| `services/auth-service/Dockerfile` | Separates the local test-key image target from the default runtime image so published images do not bundle test signing keys. |
| `.dockerignore` | Keeps local environment files, IDE state, build outputs, and unrelated docs out of image build contexts. |
| `docs/11-cicd-devsecops.md` | GHCR, environment, branch protection, SonarQube, GitOps and rollback setup instructions. |
| `pom.xml` | Maven Enforcer and JaCoCo configuration; JaCoCo reports are generated, with no global numeric threshold yet. |

## Concepts and interview notes

| Concept | Explanation |
|---|---|
| CI vs CD | CI demonstrates that a change builds and passes checks. CD promotes a verified artifact toward an environment. Here image publication is gated, while deployment is handled separately through GitOps. |
| Workflow/event/job/step/action | A workflow responds to an event; jobs run on runners and define permissions/dependencies; steps execute commands or reusable actions. |
| Cache vs artifact | Maven cache accelerates later runs; uploaded test and coverage reports preserve outputs for inspection and expire after 14 days. |
| Failsafe and `verify` | Surefire runs unit tests in `test`; configured Failsafe integration tests run later in the Maven lifecycle, so `verify` is needed to include them. |
| Action pinning | Full commit SHAs make action references immutable. Version comments keep the selected release readable and make intentional upgrades reviewable. |
| Least privilege/fork safety | PR workflows receive read-only repository permissions and no deployment secrets. The `packages: write` permission exists only in the trusted release publisher. |
| SCA and secret scanning | Trivy filesystem scanning inspects dependency manifests/lock data, supported misconfigurations, and secrets. Image scans inspect built container contents. High/critical findings fail the gate. |
| OIDC vs long-lived keys | OIDC exchanges workflow identity for short-lived credentials at a supported cloud provider. GHCR uses the narrower short-lived `GITHUB_TOKEN`; no AWS keys are needed for this pipeline. |
| Immutable tags | The full Git commit SHA identifies the exact image for audit and rollback. A release may additionally carry its version tag; `latest` is disabled. |
| GitOps vs push deployment | CI does not run production `kubectl`. A reviewed change in a separate deployment-config repository changes the desired image; Argo CD reconciles it. |
| Rollback | Revert the deployment-config image reference to a previously recorded SHA and review/sync that change. |

## Security and operations

- Configure `ghcr-release` with required reviewers and restrict it to protected
  release branches/tags. GitHub automatically supplies `GITHUB_TOKEN`; do not
  create registry credentials for the PR workflow.
- Protect `main` with required review, CODEOWNERS review, required CI checks,
  up-to-date branches, and no force-push. Protect version tags using a ruleset.
- The PR workflow scans/builds container images only when service or shared
  build inputs change. The release workflow scans every image before the
  publication job can run.
- Release scans fail on HIGH/CRITICAL findings, including fixable and
  unfixed findings. Do not suppress findings broadly; reviewed exceptions
  should be specific and time-bounded.
- Local SonarQube is optional and started with Compose profile `sonar`. Its
  token is supplied through the developer shell, never committed.
- The auth-service release image receives its JWT key pair through a Kubernetes
  Secret mount. Only local Compose builds use the committed test-key fixture.
- The release run summary and per-service artifacts expose the pushed image
  references; a reviewer applies them in the separate GitOps repository.

## Example flow

1. A contributor opens a PR. The runner checks out untrusted source with no
   write token, runs `./mvnw --batch-mode --no-transfer-progress verify`,
   scans dependencies/configuration/secrets, and uploads reports.
2. If an image input changed, the matrix builds the affected service images
   and fails on high/critical vulnerabilities. No registry login or push is
   available to the PR jobs, including fork PRs.
3. A reviewed change reaches protected `main` or a protected `v*` tag. The
   release workflow repeats the quality and scanning gates for all services.
4. After the `ghcr-release` environment approval, the publisher builds and
   pushes images to GHCR using full commit-SHA tags and release version tags
   when applicable.
5. A reviewer updates the image SHA in the separate deployment-config
   repository. Argo CD reconciles staging. Rollback restores the previous
   SHA through another reviewed GitOps change.
