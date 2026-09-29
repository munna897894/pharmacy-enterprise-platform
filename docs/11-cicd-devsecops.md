# CI/CD and DevSecOps

## Workflows

`.github/workflows/pr.yml` is a fork-safe pull-request pipeline. Its token is
read-only, third-party Actions are pinned to commit SHAs, and it has no
deployment environment or registry-write permission. It runs Maven `verify`
(unit tests, configured Failsafe integration tests, architecture tests, and
JaCoCo report generation), scans source/dependencies/configuration/secrets,
and uploads Surefire, Failsafe, and JaCoCo reports. When service/image inputs
change, a matrix builds each service image and Trivy blocks high/critical
vulnerabilities.

`.github/workflows/release.yml` repeats Maven and source-security gates, builds
and scans all service images, and only then publishes images on pushes to
`main` or `v*` tags. `packages: write` is scoped to the `publish` job. It logs
in to GHCR with the short-lived `GITHUB_TOKEN`; no registry password or AWS
credential is stored. Image names are
`ghcr.io/<lowercase-owner>/pharmacy-<service>`. Every image gets a full
`sha-<commit>` tag; version-tagged releases also get the corresponding `v*`
tag. No `latest` tag or direct production `kubectl` deployment is produced.
The publisher uploads each service's immutable image references as a run
artifact and adds them to the job summary for a reviewer to use in a separate
GitOps change.

Both workflows fail closed on Trivy high/critical findings. Do not add blanket
ignore rules. If a finding is accepted, document the exact affected component,
impact, owner, and expiry in a narrowly scoped reviewed exception before
changing the gate.

## Repository setup

1. Enable GitHub Actions and package publishing for the repository. After its
   first successful GHCR publication, verify package ownership and set package
   visibility to public if public pulls are intended.
2. Create the GitHub Actions environment named `ghcr-release`. Require one or
   more reviewers and restrict deployment branches/tags to protected `main`
   and approved `v*` release tags. Environment reviewers approve publication;
   they do not replace code review or branch protection.
3. Protect `main`: require pull requests, CODEOWNERS review, the PR workflow's
   quality/security checks (and applicable image-scan checks), up-to-date
   branches, and disallow force-push/deletion. Protect release tags with a
   repository ruleset. Confirm the exact check names in the repository after
   the first workflow run.
4. No custom repository secret or variable is required for GHCR: the publish
   job uses GitHub's automatically provided `GITHUB_TOKEN`. Do not create or
   expose credentials to pull-request workflows.
5. For optional hosted SonarQube analysis, configure `SONAR_HOST_URL` and
   `SONAR_TOKEN` as protected repository/environment values and add a reviewed
   scanner step that runs only on trusted branches. They are not required by
   the checked-in workflows.

Keep local `.env` files, Terraform state/plan files, and real Kubernetes
Secret manifests out of Git. In this workspace, `.gitignore` excludes
`k8s/pharmacy-secrets.yaml`; the similarly named `common-secret.example.yaml`
is only an example and must be replaced with managed environment values before
deployment.

Dependabot is configured under `.github/dependabot.yml` for Maven, GitHub
Actions, and Docker. `.github/PULL_REQUEST_TEMPLATE.md` reminds contributors
to include verification and operational impact.

## Local SonarQube (optional)

Start only the SonarQube service with
`docker compose -f infra/compose/compose.yml --profile sonar up -d sonarqube`,
then open `http://localhost:9000`. It is excluded from the default Compose
startup. SonarQube requires a Linux VM `vm.max_map_count` of at least 524288;
configure the Docker Desktop Linux VM where applicable. For a local analysis,
create a token in SonarQube and run:

```bash
./mvnw --batch-mode verify sonar:sonar \
  -Dsonar.host.url=http://localhost:9000 \
  -Dsonar.token="$SONAR_TOKEN"
```

Set the token in the shell without committing it. This optional command does
not change or replace the mandatory PR gates.

The filesystem secret scan excludes only the committed RSA test-key fixture
directory used by auth tests. The auth Dockerfile's default `runtime` target
does not copy those fixtures into published images; only the local Compose
`local` target does. Kubernetes mounts its keypair from a Secret at runtime.
Production deployments must provide their own managed keypair.
The root `.dockerignore` keeps `.env`, IDE files, build outputs, and unrelated
documentation out of Docker build contexts.

## GitOps and rollback boundary

`infra/argocd/application-staging.example.yaml` is a sample Argo CD `Application`
that reads a separate deployment-config repository and staging path. Replace
the example repository URL and path only in deployment configuration owned by
the platform team. The example has no automated sync policy and is not wired
to production. CI never runs `kubectl` against a production cluster.

To promote a release, review a change in the separate GitOps repository that
sets each service image to its full `sha-<commit>` reference. Argo CD then
reconciles the reviewed desired state. For rollback, revert that image-value
change to the previously recorded SHA and review/sync it through the same
GitOps process; do not rely on a mutable `latest` tag.

## Reproducing the quality gate

From a clean checkout, run `./mvnw --batch-mode --no-transfer-progress verify`.
The `verify` lifecycle is required because Maven Failsafe integration tests
run after `test`. The workflows upload test and JaCoCo reports as run-specific
artifacts; JaCoCo report generation is configured, but this repository does
not currently enforce a project-wide numeric coverage threshold.
