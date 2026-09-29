# Concept mastery checklist

Use three ratings: `E` = can explain, `D` = can demonstrate, `T` = can troubleshoot. The target is E/D for all core items and T for the bold operational items.

## Java and Spring

- [ ] IoC container, bean lifecycle and dependency injection
- [ ] Boot auto-configuration and profiles
- [ ] Controller/service/repository/entity/DTO separation
- [ ] Bean Validation and ProblemDetail
- [ ] Transactions and propagation basics
- [ ] JPA persistence context, fetching and N+1
- [ ] HikariCP and connection pools
- [ ] Flyway migrations
- [ ] Optimistic locking
- [ ] Actuator and graceful shutdown

## Security and gateway

- [ ] Password hashing
- [ ] RSA JWT structure/signature/claims
- [ ] Access vs refresh token
- [ ] Authentication vs authorization
- [ ] Security filter chain and method security
- [ ] 401 vs 403
- [ ] Gateway route/predicate/filter
- [ ] CORS and rate limiting
- [ ] Correlation and trace propagation

## Distributed systems

- [ ] Service boundaries and data ownership
- [ ] Sync vs async communication
- [ ] Timeout budgets
- [ ] Retry safety/idempotency
- [ ] Circuit breaker and bulkhead
- [ ] Saga/compensation
- [ ] Outbox and inbox patterns
- [ ] Eventual consistency

## Kafka

- [ ] Broker/topic/partition/key
- [ ] Producer acknowledgements/idempotence
- [ ] Offset and consumer group
- [ ] Rebalance and lag
- [ ] At-least-once delivery
- [ ] Ordering boundary
- [ ] Retry and DLT
- [ ] Duplicate and poison-message handling
- [ ] KRaft vs ZooKeeper history

## Docker and Kubernetes

- [ ] Image/container/layer/volume/network
- [ ] Multi-stage and non-root images
- [ ] Container DNS and Kafka advertised listeners
- [ ] Pod/ReplicaSet/Deployment/Service
- [ ] ConfigMap/Secret
- [ ] Liveness/readiness/startup
- [ ] Requests/limits/OOM/throttling
- [ ] HPA
- [ ] Ingress
- [ ] Rolling update/rollback
- [ ] Helm values/templates/rendering

## Observability and support

- [ ] Logs/metrics/traces distinction
- [ ] RED and USE
- [ ] Trace/span/context propagation
- [ ] Metric cardinality
- [ ] JVM heap/GC/threads
- [ ] Hikari active/idle/pending
- [ ] Kafka lag/DLT/outbox age
- [ ] Alert -> triage -> root cause -> mitigation -> validation -> RCA

## Delivery and cloud

- [ ] Git branch/PR/tag/release
- [ ] CI jobs/cache/artifacts
- [ ] Unit/integration/E2E/contract tests
- [ ] SAST/SCA/image/secret scans
- [ ] Immutable image tags
- [ ] GitOps reconciliation
- [ ] IAM/VPC/subnet/route/security group
- [ ] ECR/EKS/RDS/ALB/Secrets Manager/CloudWatch mapping
- [ ] Terraform plan/state/apply/destroy
- [ ] AWS cost controls

