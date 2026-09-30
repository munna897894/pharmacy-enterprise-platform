# Temporary full-fleet AWS sandbox review

Topology reference: [AWS deployment architecture](architecture-cloud.md).
The design and render/guard scripts are validated offline; a full live AWS
apply remains deliberately deferred.

## Approved scope and boundaries

This is an independent, temporary, non-production environment for synthetic-data sessions. It
deploys all 12 JVM workloads: `auth-service`, `api-gateway`, `product-service`, `customer-service`,
`pharmacy-service`, `inventory-service`, `prescription-service`, `order-service`, `payment-service`,
`notification-service`, `audit-service`, and `external-mock-service`. No local cluster, database,
Kafka broker or data is reused.

The environment is scoped to one AWS account selected by the operator, `us-east-1`, and the
`pharmacy-sbx` resource prefix by default. Every script requires an independently supplied
`EXPECTED_AWS_ACCOUNT_ID`, verifies the active AWS identity and region, and uses only the default
Terraform workspace. The EKS public API endpoint allows only the operator's configured IPv4 `/32`.
By default there is **no public load balancer at all**: the gateway is reached with
`kubectl port-forward` over that authenticated endpoint (see "Gateway exposure and transport
security").

No production/customer/prescription/payment data may be loaded. Use only synthetic test fixtures.
The environment must be destroyed at the end of each session; an expiration tag is informational,
not an AWS TTL.

## Architecture

| Component | Sandbox implementation |
|---|---|
| Compute | Temporary EKS cluster, two `t4g.large` (Graviton/arm64) managed worker nodes across two public subnets |
| Public access | Default `port-forward`: none (gateway reached through the IAM-authenticated, TLS EKS API). Opt-in `alb-https`: one HTTPS-only ALB Ingress (ACM certificate, no HTTP listener) whose only backend is `api-gateway`, restricted to explicit `/32`s |
| Data | One encrypted, non-public RDS MySQL instance; ten service-owned schemas and ten distinct DB users |
| Messaging | One private single-broker Kafka KRaft StatefulSet in EKS, ClusterIP-only and namespace-restricted; **no MSK** |
| Cache | One ephemeral in-cluster Redis Deployment |
| Images | Twelve immutable-tag ECR repositories with scan-on-push and image lifecycle cleanup |
| Secrets | Secrets Manager DB credentials, RDS master credentials and RSA JWT key pair; workload-specific IRSA read access |
| State | Versioned, encrypted, public-access-blocked S3 state bucket created by a separate bootstrap stack |
| Observability | Private in-cluster Prometheus, Grafana, Loki, Tempo, Alloy, OpenTelemetry Collector and Kafka Exporter with bounded ephemeral storage |
| AWS logs | EKS `api` and `authenticator` control-plane logs with one-day retention by default |

EKS is used because this learning exercise requires real scheduling, IRSA, optional ALB integration and
in-cluster service discovery. All business APIs except the gateway remain ClusterIP-only. The
database schemas/users are created through a disposable Job whose IRSA role can read only the
database secrets needed for initialization. Workload roles can read only their own database secret;
auth additionally reads the JWT key pair. The gateway has no AWS API permissions.

## Network and cost decisions

Worker nodes run in public subnets with public IPs to avoid NAT Gateway cost. Their security groups
do not permit unsolicited public inbound connections; EKS API access is operator `/32` restricted.
Worker egress remains open for EKS/ECR/AWS APIs and image pulls. Worker instance metadata requires
IMDSv2 and a one-hop limit so pods cannot bypass their workload-specific IRSA roles via node
credentials. RDS uses isolated subnets and its security group permits MySQL only from the EKS
cluster security group. In the opt-in `alb-https` mode the ALB security group allows inbound
**443 only** from explicit `/32`s and sends traffic only into the VPC; in the default mode no ALB
security group, ALB controller role or load balancer exists.

A production environment requires private workers, controlled egress,
multi-AZ managed services, hardened identity, durable Kafka and a formal threat review. This scope
intentionally creates no NAT Gateway, VPC endpoints, ElastiCache, MSK, production DNS or production
data services.

Expected billable resources include EKS control plane, two EC2 workers, RDS compute/storage, ALB (opt-in `alb-https` only),
ECR storage, Secrets Manager secrets, CloudWatch log ingestion/storage and public data transfer.
Potential leftover costs include EKS/EC2 resources, ALB/target groups, RDS storage/backups, NAT/EIPs,
ECR images, Secrets Manager values and CloudWatch log groups.

## Time-boxed cost estimate

Run `scripts/aws-cost-estimate.sh <hours>` for the current line-item breakdown. It is fully offline
and makes no AWS API calls. Figures below are us-east-1 on-demand list prices captured when this
document was written and are planning estimates only; reconcile actual charges in Cost Explorer.

| Component | Estimated USD/hour |
|---|---|
| EKS control plane | 0.1000 |
| Two `t4g.large` workers | 0.1344 |
| RDS `db.t4g.small`, single-AZ | 0.0320 |
| Secrets Manager, thirteen secrets | 0.0071 |
| Public data transfer allowance | 0.0050 |
| Two 30 GiB gp3 node volumes | 0.0066 |
| CloudWatch Logs, `api` and `authenticator` | 0.0042 |
| RDS 20 GiB gp3 storage | 0.0032 |
| 8 GiB gp3 Kafka volume | 0.0009 |
| ECR storage, about 6 GiB | 0.0008 |
| **Total, default `port-forward` mode** | **about 0.29** |
| HTTPS ALB, opt-in `alb-https` only (hourly plus about one LCU) | +0.0305 |
| **Total, `alb-https` mode** | **about 0.32** |

| Time box | `port-forward` USD | `alb-https` USD |
|---|---|---|
| 2-hour session | 0.58 | 0.65 |
| 4-hour session | 1.17 | 1.29 |
| 8-hour session | 2.34 | 2.58 |
| Left running 24 hours | 7.01 | 7.74 |
| Left running 7 days | 49.06 | 54.18 |
| Left running 30 days | 210.24 | 232.20 |

**The full fleet exceeds the original $20 sandbox budget after roughly 68 hours (port-forward) or
62 hours (alb-https) of continuous runtime.** Intended use is short attended sessions of a few hours, destroyed immediately afterwards,
which keeps a session near $1-$2. The budget figure is therefore a notification threshold for a
usage pattern, not a ceiling the platform enforces.

### Budget alerts are not a spending cap

AWS Budgets only send notifications. They do not stop provisioning, throttle usage, cap charges or
terminate anything, and their evaluation commonly lags real spend by several hours, so spending can
pass a threshold before any alert arrives. Configure alerts (for example at $5/$10/$15/$20) before
the first apply, but treat them strictly as a late-warning signal. The only reliable cost control in
this design is destroying the environment: `scripts/aws-destroy.sh` followed by
`scripts/aws-post-destroy-check.sh`, which fails if targeted billable resources remain.

### Conservative sizing choices

Kafka, Redis and the private observability stack run in-cluster on the existing
worker nodes, so they add no managed-service line item; MSK, ElastiCache and
commercial observability are never created by the baseline. Observability uses
bounded ephemeral storage and its resource requests are included in the node
capacity review. There is no NAT Gateway
and there are no VPC endpoints. RDS storage autoscaling is disabled and backup retention is zero, so
storage cannot silently grow. Worker root volumes are pinned to 30 GiB gp3. Control-plane logging
defaults to `api` and `authenticator` with one-day retention; the far more voluminous `audit` stream
is available through the `control_plane_log_types` variable when the extra ingest cost is accepted.
Two `t4g.large` workers are the floor for this fleet: its pod requests total roughly 4.1 GiB, but its
pod *limits* total about 8.4 GiB, which exceeds what two `t4g.medium` nodes can hold once the JVMs
warm up (see the node-capacity table below). `db.t4g.small` is retained over `db.t4g.micro` because
ten schemas with ten JDBC pools exceed a 1 GiB instance's connection and memory headroom.

## Persistent storage: EBS CSI driver and the gp3 StorageClass

EKS does not arrive with working dynamic provisioning. It pre-creates a `gp2`
StorageClass marked as the cluster default, but that class is served by the in-tree
`kubernetes.io/aws-ebs` provisioner, which Kubernetes **removed in 1.31**. On this
cluster (1.35) nothing implements it, so a PersistentVolumeClaim that lands on `gp2`
sits in `Pending` indefinitely and the StatefulSet that owns it never starts. There is
no error explaining the cause, which makes it a slow failure to diagnose.

The sandbox therefore provisions the `aws-ebs-csi-driver` EKS addon and defines its own
`gp3` class in `infra/k8s/sandbox/09-storage.yaml`:

- **Addon with IRSA.** The driver assumes a dedicated role restricted to the
  `kube-system:ebs-csi-controller-sa` service account. Permissions come from the
  AWS-managed `AmazonEBSCSIDriverPolicy`, whose destructive actions are already
  conditioned on the driver's own resource tags, so it cannot detach or delete volumes
  it did not create. An additional inline `Deny` blocks `ec2:CreateVolume` unless the
  request carries this cluster's ownership tag, so the driver cannot create untracked
  volumes that teardown would miss.
- **Controller sized down.** The addon defaults to two controller replicas with
  anti-affinity. This sandbox runs two nodes already carrying the full fleet, so it runs
  a single replica with explicit small requests.
- **gp3, not gp2.** gp3 includes 3000 IOPS and 125 MiB/s in the base price; the class
  pins exactly that baseline so no extra provisioned performance is billed. Volumes are
  encrypted and `allowVolumeExpansion` is enabled.
- **`WaitForFirstConsumer`.** Volumes are created in the same AZ as the pod that mounts
  them; immediate binding can strand a volume in an AZ with no schedulable node.
- **`reclaimPolicy: Delete`.** A retained volume outlives `terraform destroy` and keeps
  billing with nothing left to explain it. In a disposable environment, teardown
  correctness outweighs data preservation.

`scripts/aws-apply.sh` waits for `deployment/ebs-csi-controller` to be Available, strips
the default annotation from the dead `gp2` class, applies `gp3`, and then calls
`verify_default_storage_class`, which fails unless exactly one default exists and it is
served by `ebs.csi.aws.com`.

### Kafka now uses a real volume

The broker previously used `emptyDir`, which discards every topic, consumer offset and
the KRaft metadata log whenever the pod is rescheduled — a single-broker StatefulSet that
silently loses consumer-group progress mid-session. It now uses an 8 GiB `gp3`
`volumeClaimTemplate` mounted at `/var/lib/kafka/data`, with `KAFKA_LOG_DIRS` pointed at
the same path. Without that variable the image would keep writing to its default
`/tmp/kraft-combined-logs` and the attached volume would sit empty.

### Explicit Kafka topics, including retry and DLT

The broker sets `KAFKA_AUTO_CREATE_TOPICS_ENABLE=false`. Auto-creation would make every topic
with the broker default of one partition, and would only create a `.retry` or `.dlt` topic when
the first failure is published to it, so a missing topic would only show up during an incident.
`06-kafka-topics.yaml` is a Job that reuses the broker image (`apache/kafka:3.9.1`, non-root,
read-only root filesystem, no ServiceAccount token). It runs `kafka-topics.sh --create --if-not-exists`
for the same five domain topics as `scripts/local-kafka-init.sh`:

- each domain topic gets 3 partitions;
- each `.retry` and `.dlt` topic gets 1 partition;
- that is 15 topics in total, all with replication factor 1.

If a topic already exists with fewer partitions than expected, the Job raises the count. It never
lowers one. At the end it lists the topics and fails if any of the 15 is missing.

`aws-apply.sh` waits for the broker rollout, deletes any leftover Job and applies it. It then waits
for completion before any JVM workload starts. On failure it prints the Job logs, which contain
only topic names, and stops. On success it deletes the Job. Re-running apply is safe.

`aws-validate.sh` checks four things:

- auto-creation is disabled;
- the Job's topic list matches the local init script;
- the Job creates both suffixes;
- the apply script orders broker, then topic Job, then fleet.

A deliberate regression was used to prove each check fails. The Job script was also run twice
against a local `apache/kafka:3.9.1` broker with auto-creation off:

- the first run created 15 topics;
- the second run created none and exited 0;
- a domain topic that had been created with 1 partition was raised to 3.

### Teardown: CSI volumes are not Terraform's

Dynamically provisioned volumes are created by the driver at runtime, so Terraform has no
record of them and `terraform destroy` will not remove them. Destroying the cluster first
orphans them: they keep billing hourly and carry only Kubernetes tags. Two controls
prevent that:

1. `scripts/aws-destroy.sh` calls `release_persistent_volumes` **before** deleting the
   namespace or the cluster, while the driver still has credentials and somewhere to run.
   StatefulSets are deleted first, because the controller recreates a PVC deleted from
   under a running pod.
2. `scripts/aws-post-destroy-check.sh` queries `ec2 describe-volumes` for volumes tagged
   for this cluster and fails if any survive, plus a matching snapshot check.

### Node capacity

Adding the driver and a stateful broker changes the sizing arithmetic, so it is worth
stating explicitly. Against two nodes:

| Instance type | Allocatable memory | Fleet pod *requests* | Fleet pod *limits* |
| --- | --- | --- | --- |
| 2 x `t4g.medium` | ~6.4 GiB | 4.1 GiB (66%) | 8.4 GiB (**134%**) |
| 2 x `t4g.large` | ~13.7 GiB | 4.1 GiB (30%) | 8.4 GiB (61%) |

Scheduling only considers requests, so the fleet *fits* on `t4g.medium` and then degrades
under load: warmed-up JVMs approach their limits, the node crosses its eviction threshold,
and pods are killed for no visible reason. `t4g.large` is therefore the floor for the full
fleet. This roughly doubles worker cost (about $0.067/hour extra) and is the single
largest line item after the control plane; the estimate in `scripts/aws-cost-estimate.sh`
reflects it, along with the Kafka volume.

## CPU architecture: nodes and images must agree

The workstation used to build these images is Apple Silicon (`arm64`). A plain
`docker build` there produces an `arm64` image. Pushing that to `x86_64` EKS nodes
does not fail at build or push time: it fails much later, after the whole rollout,
with every pod in `CrashLoopBackOff` and `exec format error` in the container log.
That is an expensive way to discover a one-word configuration mismatch, so
architecture is a single explicit setting rather than an implicit default on each side.

**The sandbox targets `arm64` (AWS Graviton).** Workers are `t4g.large` and RDS is
already `db.t4g.small`. Graviton is chosen because it is roughly 19% cheaper per node
hour than the equivalent `t3` type, and because it matches the build host, so images
build natively instead of under QEMU emulation (emulated JVM builds are dramatically
slower). Every image in the fleet is multi-architecture upstream —
`eclipse-temurin:21-jre-jammy`, `maven:3.9.9-eclipse-temurin-21`, `mysql:8.4`,
`redis:7.4.11`, `apache/kafka:3.9.1` and `amazon/aws-cli:2.17.0` — so nothing blocks
`arm64`.

`var.node_architecture` (`amd64` or `arm64`) is the single source of truth:

| Consumer | How it uses the value |
| -------- | --------------------- |
| Node AMI | `ami_type` becomes `AL2023_ARM_64_STANDARD` or `AL2023_x86_64_STANDARD`. Previously unset, which silently defaulted to x86_64. |
| Instance type | A `precondition` rejects a Graviton instance type paired with `amd64`, or a non-Graviton type paired with `arm64`. |
| Image build | Exported as the `node_architecture` Terraform output and read by `scripts/aws-apply.sh`, which builds `--platform linux/$NODE_ARCH`. |

Four independent controls prevent a mismatch reaching the cluster:

1. **Terraform precondition** — an architecture/instance-type mismatch fails at plan time.
2. **Explicit build platform** — `aws-apply.sh` uses `docker buildx build --platform linux/$ARCH --push` on a dedicated `pharmacy-sandbox` builder, where `$ARCH` comes from the Terraform output, never from `uname`. If the host differs it registers `binfmt` emulation and warns that builds will be slower.
3. **Post-push image verification** — `verify_image_architecture` inspects the pushed manifest with `docker buildx imagetools inspect` and aborts if it is not the expected architecture, so a bad image is caught at push rather than at rollout.
4. **Live node verification** — `verify_node_architecture` reads `.status.nodeInfo.architecture` from the real nodes and refuses to deploy if it disagrees with the images.

`scripts/aws-validate.sh` additionally fails if any AWS script reintroduces a bare
`docker build`, or if `aws-apply.sh` loses `--platform` or either verification helper.

To switch back to x86_64, set both values together (Terraform rejects them individually):

```bash
NODE_ARCH=amd64 NODE_INSTANCE_TYPE=t3.large ./scripts/aws-plan.sh
```

## Kubernetes version and extended-support policy

The EKS control plane version is pinned explicitly to **1.35**.

EKS gives each minor version roughly 14 months of *standard* support, then a further
12 months of *extended* support. Extended support is enabled by default on AWS and is
billed at a substantially higher per-cluster-hour rate (roughly six times the standard
control-plane rate). A cluster left on a stale version therefore keeps running, and
keeps costing more, with no error and no warning.

Version lifecycle as verified against the AWS EKS documentation on **2026-09-30**:

| Version | Support status    | End of standard support |
| ------- | ----------------- | ----------------------- |
| 1.36    | Standard          | 2027-08-02              |
| 1.35    | Standard          | 2027-03-27              |
| 1.34    | Standard          | 2026-12-02              |
| 1.33    | Extended (paid)   | 2026-07-17              |
| 1.32    | Extended (paid)   | 2026-03-23              |
| 1.31    | Extended (paid)   | 2026-01-23              |

`1.35` is chosen over `1.36` because it has been generally available longer, and over
`1.34` because `1.34` leaves standard support in December 2026, which is close enough
that a pin left in place would soon start billing extended-support fees.

Three independent controls keep this from regressing:

1. `var.kubernetes_version` has a Terraform `validation` block in both
   `modules/eks/main.tf` and `envs/sandbox/variables.tf` that accepts only
   `1.34`, `1.35` or `1.36`. A stale or hand-edited `terraform.tfvars` naming an
   extended-support version fails at plan time instead of provisioning.
2. The cluster sets `upgrade_policy { support_type = "STANDARD" }`, so AWS will not
   move it into paid extended support.
3. `scripts/aws-check-eks-version.sh` runs before every `scripts/aws-plan.sh` and calls
   `aws eks describe-cluster-versions`, refusing to continue unless the requested
   version reports `STANDARD_SUPPORT`. If the API is unreachable (for example when the
   SSO session has expired) it warns instead of failing, because plan-time validation
   and the `upgrade_policy` still apply.

### Upgrade policy

Because this environment is ephemeral and recreated per session, there is no in-place
upgrade path to maintain: each session creates a cluster at the pinned version and
destroys it afterwards. The maintenance task is therefore only to keep the *pin* fresh.

Before a session, confirm the pin is still in standard support:

```bash
aws eks describe-cluster-versions --region us-east-1 --profile pharmacy-sandbox \
  --query "clusterVersions[?status=='STANDARD_SUPPORT'].[clusterVersion,endOfStandardSupportDate]" \
  --output table
```

`scripts/aws-plan.sh` performs this check automatically. When the pinned version nears
its end-of-standard-support date, bump the default and the validation lists in
`modules/eks/main.tf`, `envs/sandbox/variables.tf` and `terraform.tfvars.example`, and
update the table above with the date the lifecycle was re-verified. Override for a
single run with `KUBERNETES_VERSION=1.36 ./scripts/aws-plan.sh`; the value still has to
pass the validation block.

## Known application risk: registration role self-escalation

`auth-service` registration currently accepts caller-supplied roles in the request body, including
privileged roles. Any caller that can reach the gateway can therefore create a privileged account.
**This sandbox is unsafe to expose to the open internet.** Until registration ignores or authorizes
requested roles, the environment is only acceptable behind a narrow network allowlist.

Mitigation enforced by this infrastructure: by default the gateway is not publicly reachable at all
(`gateway_exposure = "port-forward"`); only principals with EKS access from `operator_cidr` can reach
it. If the HTTPS ALB is opted into, its security group accepts 443 only from `alb_ingress_cidrs`, an
explicit list of `/32` addresses. Terraform validation requires a non-empty list in `alb-https` mode
(and an empty one otherwise) and rejects non-`/32` entries and wildcard addresses, and
`scripts/aws-plan.sh` refuses `alb-https` unless `ALB_INGRESS_CIDRS` is set, so the configuration
fails closed rather than defaulting to public exposure. The allowlist limits who can exploit the
escalation; it does not fix it, so even the HTTPS ALB is not safe for the open internet.

## Private observability on EKS

`scripts/aws-apply.sh` installs `infra/helm/observability` (release and namespace
`pharmacy-observability`) after the fleet is ready, using the chart's `values-eks.yaml` plus
`infra/k8s/sandbox/observability-values-aws.yaml`. The overlay disables Alertmanager, node-exporter
and control-plane scrapers, bounds every container, and shortens retention (2 days) and node-local
storage so it fits beside the fleet on two `t4g.large` workers (30 GiB node disks). Applications
export traces to the in-cluster collector and expose Actuator metrics through annotated Services.

- All observability Services are ClusterIP; use `kubectl port-forward --address 127.0.0.1`.
- The Grafana admin password is generated in-process and piped to `kubectl apply` via stdin (never
  argv, stdout or disk); the chart's `ensure-grafana-secret.sh` then verifies the Secret and leaves it
  unchanged. Read it only when needed with `kubectl get secret grafana-admin`.
- Capacity (checked by `scripts/aws-validate.sh`): memory limits are about 10.6 GiB (fleet 6.9 +
  observability 3.7) against a 12.7 GiB two-node budget (84%); CPU requests are about 1.7 of 3.3
  cores. Every container has a memory limit, or validation fails.
- `scripts/aws-destroy.sh` uninstalls the release and deletes `pharmacy-observability` before the
  application namespace and before `terraform destroy`.
- Data is ephemeral: history is lost when pods are replaced or the cluster is destroyed.

## Gateway exposure and transport security

`/api/v1/auth/login` receives passwords and returns bearer JWTs, and every other API call carries
that JWT. Sending them over plain HTTP on the public internet exposes them to any on-path observer,
and **using synthetic data does not make that acceptable**: a captured token or password is still a
working credential for the environment (including privileged accounts, given the registration risk
above). An earlier revision of this sandbox exposed the gateway through an internet-facing
HTTP:80-only ALB and its smoke test posted credentials to `http://<alb>`; that design was unsafe and
has been removed. Plain-HTTP public exposure is no longer a supported option.

| `gateway_exposure` | What is created | How clients connect |
|---|---|---|
| `port-forward` (**default**) | No ALB, no ALB security group, no ALB controller or its IAM role, no Ingress | `kubectl port-forward --address 127.0.0.1 svc/api-gateway 18080:8080`; traffic is tunnelled over the TLS-protected, IAM-authenticated EKS API endpoint (restricted to `operator_cidr`) and plain HTTP exists only on loopback |
| `alb-https` (opt-in) | ALB controller + IRSA role, ALB security group admitting 443 only from `/32`s, Ingress with a single `HTTPS:443` listener, ACM certificate, `ELBSecurityPolicy-TLS13-1-2-2021-06`, host rule | `https://<gateway_hostname>` with certificate validation |

Fail-closed rules for `alb-https`:

- `gateway_certificate_arn`, `gateway_hostname` and `alb_ingress_cidrs` are all required and are
  rejected in `port-forward` mode, so a partial configuration cannot silently create an ALB.
- `scripts/aws-check-gateway-certificate.sh` runs before planning and refuses a certificate that is
  not `ISSUED`, is outside the sandbox account/region, expires within a day, or does not cover the
  hostname (exact name or a single-label wildcard).
- There is **no HTTP listener, not even a redirect**: a redirect still lets a client send its
  first request, credentials included, in plaintext.
- The ALB's `*.elb.amazonaws.com` name cannot match any certificate, so a hostname you control is
  required. Creating the DNS CNAME (and the ACM certificate/validation) is the operator's job and is
  outside Terraform here; the smoke test uses `curl --connect-to` so it can verify TLS against the
  hostname before DNS propagates.
- `scripts/aws-smoke-test.sh` refuses any base URL that is not `https://` or
  `http://127.0.0.1:<port>`, checks that the live Ingress listens on exactly `HTTPS:443` before
  sending anything, sends payloads through stdin and the bearer token through a mode-0600 header
  file (never argv, never printed), and uses a random per-run synthetic account.
- `scripts/aws-smoke-test.sh` sends **all** authenticated traffic (register, login, bearer calls and
  the full Newman suite, on by default) only through the loopback `kubectl port-forward` tunnel, in
  every mode. In `alb-https` mode the ALB receives a single unauthenticated HTTPS JWKS request with
  certificate verification and nothing else.
- There is no plain-HTTP ALB mode, not even for "synthetic, non-credential" traffic: an HTTP
  listener cannot stop a client from sending credentials, so the only safe HTTP ALB is none. Without
  an ACM certificate, use `port-forward`; unauthenticated health checks are available there too.
- `scripts/aws-validate.sh` fails if an HTTP listener, a port-80 ALB rule, a non-loopback `http://`
  client URL or a non-`port-forward` default is reintroduced, and proves each check fires.

Traffic between the ALB and the gateway pod, and between services inside the cluster, is plain HTTP
inside the VPC. That is acceptable for this synthetic sandbox but is not production-grade; production
needs end-to-end TLS or a service mesh.

## Data ownership

| Service | Schema | Database user |
|---|---|---|
| auth-service | `auth_service` | `auth_user` |
| product-service | `pharmacy_product` | `product_user` |
| customer-service | `pharmacy_customer` | `customer_user` |
| pharmacy-service | `pharmacy_pharmacy` | `pharmacy_user` |
| inventory-service | `pharmacy_inventory` | `inventory_user` |
| prescription-service | `prescription_service` | `prescription_user` |
| order-service | `pharmacy_order` | `order_user` |
| payment-service | `pharmacy_payment` | `payment_user` |
| notification-service | `pharmacy_notification` | `notification_user` |
| audit-service | `pharmacy_audit` | `audit_user` |

The gateway and external mock own no RDS schema. Services do not share tables. RDS master credentials
are used only by the temporary schema-bootstrap Job and are stored in Secrets Manager. Workload
passwords and the JWT private key are never rendered into manifests or logged. Terraform state
contains sensitive values and must remain in the encrypted, access-restricted backend.

### Credentials and Terraform state

**Terraform no longer generates any credential.** This is the single most important property in
this section, and it exists because of how Terraform state works: every value a resource produces
is written to state in **plaintext**, regardless of whether the attribute is marked `sensitive`.
`sensitive` only suppresses CLI output. The state bucket is **versioned**, so each apply writes a
new object version and keeps the previous ones. `terraform destroy` empties the *current* state but
does not remove prior versions. A destroyed environment would therefore leave the ten database
passwords and the JWT RSA **private key** readable in S3 indefinitely, for anyone who can read the
bucket.

An earlier revision of this environment had exactly that problem: it used `random_password` for
eleven passwords and `tls_private_key` for the JWT keypair. It is not accurate to describe such a
sandbox as leaving no credentials behind after teardown.

Credentials are now produced outside Terraform:

| Credential | Generated by | Reaches Terraform state? |
| --- | --- | --- |
| RDS master password | AWS, via `manage_master_user_password` on the DB instance | No — AWS creates and owns the secret |
| Ten per-service DB passwords | `aws secretsmanager get-random-password` in `scripts/aws-seed-secrets.sh` | No |
| JWT RSA keypair | `openssl genpkey` in `scripts/aws-seed-secrets.sh` | No |

Terraform creates the Secrets Manager entries **empty** and `scripts/aws-seed-secrets.sh` populates
them after apply, passing values to the CLI as `file://` references so they never appear in `argv`.
The `random` and `tls` providers have been removed from `required_providers` so those resources
cannot be reintroduced by accident, and `scripts/aws-validate.sh` fails if any `random_password`,
`random_string`, `tls_private_key` or `aws_secretsmanager_secret_version` resource reappears, or if
the RDS module stops using `manage_master_user_password`.

State still contains infrastructure detail — ARNs, endpoints, security group IDs, subnet layout —
which is not secret but is useful to an attacker, so the bucket remains encrypted, private and
access-restricted.

#### Historic state versions

Removing credentials from state going forward does **not** clean up state objects written earlier.
If this environment was ever applied before this change, the old values are still in noncurrent
versions of the state object and must be dealt with explicitly:

1. The state bucket now has a lifecycle rule expiring noncurrent versions after
   `state_history_retention_days` (default 7), which bounds how long any state history survives.
2. `scripts/aws-prune-state-history.sh` permanently deletes **all** versions of the state object
   when retiring the exercise. It requires `STATE_BUCKET` explicitly, refuses to run while
   Terraform state still tracks resources, matches the state key exactly so unrelated objects are
   untouched, and requires typing `prune` to confirm. It is irreversible.
3. Anything ever stored in Secrets Manager by a previous run should be considered compromised if
   that state history was not pruned. Because every session builds a fresh environment with freshly
   generated credentials, the practical exposure is limited to that retired environment.

Retirement order: `scripts/aws-destroy.sh` -> `scripts/aws-post-destroy-check.sh` ->
`scripts/aws-prune-state-history.sh`.

No production data is ever loaded into this environment; all data is synthetic, which is what keeps
this a bounded cleanup problem rather than a data-breach one.

### Secret exposure rules

Terraform root outputs deliberately expose no credential: no database password, no RDS master
password and no JWT key material. `scripts/aws-apply.sh` never writes the full output set to disk.
It pipes `terraform output -json` straight into `scripts/aws-filter-outputs.py`, which keeps only an
allowlist of identifiers, endpoints, secret ARNs and tags, and fails closed if any allowlisted output
is ever marked sensitive. The filtered file is created under `umask 077` in a per-run temporary
directory and deleted when the script exits.

Credentials are never placed in process arguments or environment variables that appear in `argv`.
In particular the schema bootstrap does not use `kubectl run --env="MYSQL_PWD=..."`, which would
expose the master password to every process listing on the node. The bootstrap Job instead writes a
MySQL client config to a memory-backed volume and passes `--defaults-extra-file`. Workload pods fetch
their own secret through IRSA into mode-0600 files on memory-backed volumes. No secret is echoed,
`tee`d, or written to a predictable shared path such as `/tmp/pharmacy-sandbox-tf-outputs.json`.

`scripts/aws-validate.sh` enforces these rules automatically: it scans every AWS script and sandbox
manifest for `MYSQL_PWD`, `kubectl run --env`, command-line passwords, unfiltered
`terraform output -json` redirection, predictable shared temporary output paths, secret `tee`
pipelines and credential `echo`, and fails the build if any reappear.

## AWS contract for the shared Helm chart

This section is the authoritative interface the AWS environment expects from
`infra/helm/pharmacy-platform`. The chart is owned separately; the AWS scripts currently render the
manifests in `infra/k8s/sandbox/` and will switch to installing the chart only after this contract is
implemented and explicitly coordinated.

The namespace is `pharmacy`. `api-gateway` listens on `8080` with management port `9081`, which the
ALB health check targets. Remaining services keep ports `8081`-`8090`, and the external mock uses
`8080`. Only `api-gateway` can be exposed (through port-forward by default, or the opt-in HTTPS
ALB); every Service, including the gateway, stays ClusterIP.

Shared non-secret settings belong in one ConfigMap named `pharmacy-platform-config`: `DB_HOST` (RDS
address), `DB_PORT=3306`, `KAFKA_BOOTSTRAP_SERVERS=kafka.pharmacy.svc.cluster.local:9092`,
`JWT_ISSUER=http://auth-service:8081`, `JWT_AUDIENCE=pharmacy-api` and `ENVIRONMENT=aws`. Per-service
schema, user, port and image stay in each service's values entry.

Images are immutable ECR tags of the form
`<account-id>.dkr.ecr.<region>.amazonaws.com/<name-prefix>-<service>:<tag>`; the AWS renderer supplies
them for all twelve workloads. Service account names equal service names and carry the
`eks.amazonaws.com/role-arn` annotation for that service's IRSA role. `db-bootstrap` keeps a
separate service account and role; `aws-load-balancer-controller` has one only in `alb-https` mode.
The chart must never render an Ingress or `LoadBalancer`/`NodePort` Service for AWS: public exposure
is owned exclusively by `infra/k8s/sandbox/04-ingress.yaml`. `api-gateway` and
`external-mock-service` need no AWS permissions.

Credentials must never appear in chart values, rendered manifests, logs or committed files. Chart
values may carry only Secrets Manager ARNs. The preferred mechanism is the Secrets Store CSI driver
with the AWS provider and IRSA, synced into the Kubernetes Secret names the chart already uses:
`<service>-db` with key `password`, and `auth-service-jwt` with keys `private_key.pem` and
`public_key.pem`. The sandbox manifests currently use an equivalent init-container retrieval that
writes mode-0600 files onto memory-backed volumes without logging secret values; either mechanism
satisfies this contract as long as no secret value is printed or committed.

## Create -> validate -> use -> destroy

1. Configure the SSO profile, AWS Budget alerts, independently verify the account ID, choose a
   current operator IPv4 `/32`, and copy the sandbox tfvars example. The default
   `GATEWAY_EXPOSURE=port-forward` needs nothing else. Only for the opt-in HTTPS ALB, set
   `GATEWAY_EXPOSURE=alb-https`, `GATEWAY_CERTIFICATE_ARN`, `GATEWAY_HOSTNAME` and
   `ALB_INGRESS_CIDRS`; plans fail closed if any is missing or the certificate check fails. Review
   `scripts/aws-cost-estimate.sh <hours>` and agree the session time box before applying.
2. Run `scripts/aws-validate.sh`; create/review the plan using `scripts/aws-plan.sh`. Plan artifacts
   contain sensitive values: inspect locally only and do not commit or share them.
3. Run `scripts/aws-apply.sh` with its explicit confirmation. It provisions AWS, seeds the empty
   Secrets Manager entries via `scripts/aws-seed-secrets.sh` (so no credential enters Terraform
   state), builds and pushes all twelve images, installs EBS CSI storage, creates schemas/users
   with a temporary Job, then deploys Kafka and Redis, creates the 15 domain/retry/DLT topics with an idempotent Job,
   and deploys the JVM fleet. Only in `alb-https` mode does
   it install the ALB controller and apply the HTTPS-only gateway Ingress; then create the DNS CNAME
   it prints.
4. Run `scripts/aws-smoke-test.sh` against the verified cluster context. By default it opens a
   loopback `kubectl port-forward`; with `GATEWAY_EXPOSURE=alb-https GATEWAY_HOSTNAME=...` it uses
   verified HTTPS. It uses a random synthetic account and never prints or passes the token in argv.
5. Run `scripts/aws-destroy.sh`. It verifies account, region and workspace; requires exact
   account/region confirmation and explicit acceptance of no RDS final snapshot; deletes the
   application namespace before Terraform resources so, in `alb-https` mode, the ALB controller can
   clean up the ALB. It reads the gateway mode from state; an `alb-https` stack also needs
   `ALB_INGRESS_CIDRS` set to pass validation.
6. Run `scripts/aws-post-destroy-check.sh`. It exits unsuccessfully if target EKS/worker/ALB/RDS,
   NAT/EIP, ECR, Secrets Manager or EKS log-group resources remain.

The S3 state bucket is deliberately outside the environment destroy. Remove it only after the main
Terraform state is empty and the inventory check is clean. Never use `terraform apply -auto-approve`
or `terraform destroy -auto-approve`.
