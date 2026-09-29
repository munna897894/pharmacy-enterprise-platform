# Final definition of done

Mark an item complete only with evidence (test report, command output, API response, dashboard, trace or Git commit).

## Build and repository

- [ ] Root Maven clean verify passes.
- [ ] All runnable modules build independently.
- [ ] No service implementation module depends on another.
- [ ] No committed secrets, private keys, `.env`, Terraform state or kubeconfig.
- [ ] Dependency versions are BOM-managed and documented.
- [ ] Repository is tagged with a final learning release.

## APIs and data

- [ ] API contracts are implemented or explicitly marked deferred.
- [ ] Each service owns a separate schema and Flyway migrations.
- [ ] Entities are not exposed as API DTOs.
- [ ] Pagination/validation/ProblemDetail are consistent.
- [ ] Product cache hit/miss/fallback behavior is verified.
- [ ] Optimistic locking prevents lost inventory/order updates.

## Security

- [ ] Auth service issues RSA-signed access tokens.
- [ ] Gateway and downstream services validate tokens.
- [ ] 401 vs 403 behavior is correct.
- [ ] Owner/role authorization tests pass.
- [ ] Tokens/passwords/private data are absent from logs.
- [ ] CORS and gateway rate limits are tested.

## Kafka workflow

- [ ] Happy order saga reaches CONFIRMED.
- [ ] Inventory rejection cancels without payment.
- [ ] Payment failure releases inventory and cancels.
- [ ] Duplicate deliveries do not duplicate business effects.
- [ ] Retry/DLT behavior is bounded and observable.
- [ ] Kafka-down outbox backlog catches up after recovery.
- [ ] Notification/audit use distinct consumer groups.

## Containers and Kubernetes

- [ ] Compose stack starts from documented steps.
- [ ] Images use multi-stage builds and non-root runtime where practical.
- [ ] Host and container Kafka listeners work.
- [ ] Gateway is the only normal ingress.
- [ ] Kubernetes manifests/Helm render cleanly.
- [ ] Startup/readiness/liveness probes behave correctly.
- [ ] Resource requests/limits and HPA demonstration exist.
- [ ] Rolling update and rollback were practiced.

## Observability

- [ ] Prometheus scrapes all running services.
- [ ] Grafana dashboard shows HTTP, JVM, Hikari, Kafka, cache/outbox and business metrics.
- [ ] One end-to-end flow has trace evidence.
- [ ] Correlation ID links HTTP, Kafka and logs.
- [ ] Alerts exist for high errors/latency, pool saturation, lag/DLT, heap and outbox age.
- [ ] Sensitive fields and high-cardinality metric tags are absent.

## CI/CD and operations

- [ ] PR workflow builds/tests/scans.
- [ ] Release image uses immutable commit SHA.
- [ ] Dependabot covers Maven, actions and Docker.
- [ ] Deployment secrets are protected and not exposed to PRs.
- [ ] GitOps/Argo CD flow is documented or demonstrated.
- [ ] At least eight incident labs were attempted and four completed without immediate Copilot fixes.
- [ ] Every completed incident has a short RCA.

## AWS, if executed

- [ ] Budget alerts configured before billable resources.
- [ ] No MSK and no unapproved NAT Gateway.
- [ ] Resources tagged with expiration/project metadata.
- [ ] Terraform plan reviewed before apply.
- [ ] Terraform destroy completed.
- [ ] Post-destroy inventory confirms no unexpected EKS/EC2/ELB/RDS/NAT/EIP resources.

## Personal explanation test

You can explain without reading notes:

- [ ] a request from gateway to controller/service/repository/DB and back;
- [ ] JWT signing/validation and 401 vs 403;
- [ ] order saga events and compensation;
- [ ] at-least-once delivery, outbox and consumer idempotency;
- [ ] Docker networking and Kafka listeners;
- [ ] Kubernetes deployment/service/probes/resources/rollout;
- [ ] how metrics, traces and logs combine during an incident;
- [ ] CI build-to-image-to-deployment flow;
- [ ] what AWS manages compared with the local stack.

