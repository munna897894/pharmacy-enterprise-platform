#!/usr/bin/env bash
# Formats and validates Terraform, checks AWS script syntax, and parses rendered
# Kubernetes YAML without contacting or changing an AWS/Kubernetes environment.
set -euo pipefail

# Prefer a native-arch terraform binary if one was installed to ~/bin
# (Homebrew's Intel-only install run under Rosetta on Apple Silicon is
# dramatically slower for large providers like AWS's).
if [ -x "$HOME/bin/terraform" ]; then
  export PATH="$HOME/bin:$PATH"
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TF_DIR="$ROOT_DIR/infra/terraform"

echo "== terraform fmt -check (Terraform source only; leaves local tfvars untouched) =="
while IFS= read -r -d '' tf_file; do
  terraform fmt -check -diff "$tf_file" >/dev/null || {
    echo "Terraform formatting check failed for $tf_file." >&2
    exit 1
  }
done < <(find "$TF_DIR" -path "$TF_DIR/.terraform" -prune -o -type f -name '*.tf' -print0)

BOOTSTRAP_DIR="$TF_DIR/bootstrap"
echo "== terraform validate: $BOOTSTRAP_DIR =="
(cd "$BOOTSTRAP_DIR" && terraform init -backend=false -input=false >/dev/null && terraform validate)

echo "== terraform validate: sandbox AWS configuration without remote-state access =="
VALIDATE_DIR="$(mktemp -d)"
trap 'rm -rf "$VALIDATE_DIR"' EXIT
mkdir -p "$VALIDATE_DIR/infra/terraform/envs/sandbox" "$VALIDATE_DIR/infra/terraform/modules"
cp "$TF_DIR"/envs/sandbox/*.tf "$VALIDATE_DIR/infra/terraform/envs/sandbox/"
rm -f "$VALIDATE_DIR/infra/terraform/envs/sandbox/backend.tf"
cp "$TF_DIR/envs/sandbox/.terraform.lock.hcl" "$VALIDATE_DIR/infra/terraform/envs/sandbox/"
cp -R "$TF_DIR/modules/." "$VALIDATE_DIR/infra/terraform/modules/"
(
  cd "$VALIDATE_DIR/infra/terraform/envs/sandbox"
  terraform init -backend=false -input=false >/dev/null
  terraform validate
)
rm -rf "$VALIDATE_DIR"
trap - EXIT

echo "== AWS shell script syntax =="
bash -n "$ROOT_DIR"/scripts/aws-*.sh "$ROOT_DIR/scripts/aws-common.sh"

echo "== Secret-handling guards for AWS scripts and manifests =="
python3 - "$ROOT_DIR" <<'PY'
import pathlib
import re
import sys

root = pathlib.Path(sys.argv[1])
# This validator is excluded: it necessarily contains the forbidden patterns
# themselves as detection rules.
targets = [
    path for path in (
        sorted(root.glob("scripts/aws-*.sh")) + sorted(root.glob("scripts/aws-*.py"))
        + sorted((root / "infra/k8s/sandbox").glob("*.yaml"))
        + sorted((root / "infra/k8s/sandbox").glob("*.tmpl"))
    )
    if path.name != "aws-validate.sh"
]

# Patterns that would place credentials in process argv, logs or a predictable
# shared path. These mirror mistakes present in the manual guide.
forbidden = [
    (r"MYSQL_PWD", "passes a database password through the environment/argv"),
    (r"kubectl\s+run\b[^\r\n]*--env", "exposes secrets via kubectl run argv"),
    (r"--password=\$", "passes a password on a command line"),
    (r"terraform\s+output\s+-json\s*>", "writes the unfiltered Terraform output set to disk"),
    (r"/tmp/pharmacy[\w.-]*\.json", "uses a predictable shared temporary path for outputs"),
    (r"\btee\b[^\r\n]*secret", "tees secret material into logs"),
    (r"echo\s+\"?\$(MASTER_PW|DB_PASSWORD|MASTER_PASSWORD)", "echoes a credential"),
]

failures = []
for path in targets:
    text = path.read_text()
    for pattern, reason in forbidden:
        if re.search(pattern, text):
            failures.append(f"{path.relative_to(root)}: {reason}")

if failures:
    print("Forbidden secret-handling pattern found:", file=sys.stderr)
    for failure in failures:
        print("  " + failure, file=sys.stderr)
    raise SystemExit(1)
print(f"Checked {len(targets)} AWS files: no argv/log/tmp credential exposure patterns.")
PY

echo "== Terraform state-secrecy guard (no credentials may enter state) =="
ruby -e '
# Any value a Terraform resource generates is stored in plaintext in the state
# file. The state bucket is versioned, so such values survive `terraform destroy`
# in noncurrent object versions. Credentials are therefore generated outside
# Terraform (scripts/aws-seed-secrets.sh) and this guard keeps it that way.
root = ARGV[0]
failures = []

forbidden = {
  "random_password"                    => "generates a password into Terraform state",
  "random_string"                      => "generates a value into Terraform state",
  "tls_private_key"                    => "generates a private key into Terraform state",
  "aws_secretsmanager_secret_version"  => "writes a secret value through Terraform state",
}

Dir.glob(File.join(root, "infra", "terraform", "**", "*.tf")).sort.each do |path|
  next if path.include?("/.terraform/")
  rel = path.sub(root + "/", "")
  File.readlines(path).each_with_index do |line, index|
    stripped = line.strip
    next if stripped.start_with?("#")
    forbidden.each do |token, reason|
      failures << "#{rel}:#{index + 1}: #{token} #{reason}" if stripped =~ /^(resource|data)\s+"#{token}"/
    end
  end
end

versions = File.read(File.join(root, "infra", "terraform", "envs", "sandbox", "versions.tf"))
["hashicorp/random", "hashicorp/tls"].each do |provider|
  failures << "versions.tf must not require #{provider}: its resources persist secrets in state" if versions.include?(provider)
end

rds = File.read(File.join(root, "infra", "terraform", "modules", "rds", "main.tf"))
failures << "modules/rds/main.tf must use manage_master_user_password so AWS owns the master secret" unless rds.include?("manage_master_user_password")

unless failures.empty?
  warn "Terraform state-secrecy guard failed:"
  failures.each { |f| warn "  #{f}" }
  exit 1
end

puts "No Terraform resource generates or stores a credential value."
' "$ROOT_DIR"

echo "== Image/node architecture guard for AWS scripts =="
python3 - "$ROOT_DIR" <<'PY'
import pathlib
import re
import sys

# A container image built for the wrong CPU architecture crash-loops every pod
# with "exec format error". Image builds must always name the target platform
# explicitly rather than inheriting the workstation's architecture.
root = pathlib.Path(sys.argv[1])
failures = []

for path in sorted(root.glob("scripts/aws-*.sh")):
    if path.name == "aws-validate.sh":
        continue
    for lineno, line in enumerate(path.read_text().splitlines(), start=1):
        stripped = line.strip()
        if stripped.startswith("#"):
            continue
        if re.search(r"\bdocker\s+build\b", stripped):
            failures.append(f"{path.name}:{lineno}: use 'docker buildx build --platform'")
        if re.search(r"\bdocker\s+buildx\s+build\b", stripped) and "--platform" not in path.read_text():
            failures.append(f"{path.name}:{lineno}: buildx build without --platform")

apply_sh = (root / "scripts" / "aws-apply.sh").read_text()
for required, description in (
    ("--platform", "explicit build platform"),
    ("verify_image_architecture", "pushed-image architecture check"),
    ("verify_node_architecture", "live node architecture check"),
):
    if required not in apply_sh:
        failures.append(f"aws-apply.sh is missing the {description} ({required})")

eks_tf = (root / "infra" / "terraform" / "modules" / "eks" / "main.tf").read_text()
if "ami_type" not in eks_tf:
    failures.append("modules/eks/main.tf must set ami_type so the node AMI matches node_architecture")

if failures:
    print("Architecture-handling guard failed:", file=sys.stderr)
    for failure in failures:
        print(f"  {failure}", file=sys.stderr)
    raise SystemExit(1)

print("Image builds pin an explicit platform and verify it against the nodes.")
PY

echo "== Persistent storage guard (PVCs require a working provisioner) =="
ruby -ryaml -e '
# EKS has no usable default StorageClass: the legacy in-tree gp2 provisioner was
# removed in Kubernetes 1.31, so a PVC without the EBS CSI driver and an explicit
# class stays Pending indefinitely with no clear event explaining why.
root = ARGV[0]
sandbox = File.join(root, "infra", "k8s", "sandbox")
failures = []
classes = []
claim_classes = []

Dir.glob(File.join(sandbox, "*.yaml")).sort.each do |path|
  name = File.basename(path)
  YAML.load_stream(File.read(path)) do |doc|
    next unless doc.is_a?(Hash)
    case doc["kind"]
    when "StorageClass"
      classes << doc["metadata"]["name"]
      failures << "#{name}: StorageClass must use ebs.csi.aws.com" if doc["provisioner"] != "ebs.csi.aws.com"
      failures << "#{name}: reclaimPolicy must be Delete so volumes do not outlive teardown" if doc["reclaimPolicy"] != "Delete"
    when "StatefulSet"
      (doc["spec"]["volumeClaimTemplates"] || []).each do |claim|
        sc = claim["spec"]["storageClassName"]
        if sc.nil? || sc.empty?
          failures << "#{name}: volumeClaimTemplate must name a storageClassName explicitly"
        else
          claim_classes << sc
        end
      end
    when "PersistentVolumeClaim"
      sc = doc["spec"]["storageClassName"]
      if sc.nil? || sc.empty?
        failures << "#{name}: PersistentVolumeClaim must name a storageClassName explicitly"
      else
        claim_classes << sc
      end
    end
  end
end

claim_classes.uniq!
missing = claim_classes - classes
failures << "claims reference undefined StorageClass(es): #{missing.join(", ")}" unless missing.empty?

eks_tf = File.read(File.join(root, "infra", "terraform", "modules", "eks", "main.tf"))
failures << "manifests use PVCs but the aws-ebs-csi-driver addon is not provisioned" if !claim_classes.empty? && !eks_tf.include?("aws-ebs-csi-driver")
failures << "EBS CSI IRSA role must be bound to the ebs-csi-controller-sa service account" unless eks_tf.include?("ebs-csi-controller-sa")

destroy_sh = File.read(File.join(root, "scripts", "aws-destroy.sh"))
failures << "aws-destroy.sh must release PVCs before the cluster is destroyed" if !claim_classes.empty? && !destroy_sh.include?("release_persistent_volumes")

check_sh = File.read(File.join(root, "scripts", "aws-post-destroy-check.sh"))
failures << "aws-post-destroy-check.sh must check for leaked CSI-provisioned EBS volumes" unless check_sh.include?("describe-volumes")

unless failures.empty?
  warn "Persistent storage guard failed:"
  failures.each { |f| warn "  #{f}" }
  exit 1
end

puts "PVCs bind to #{claim_classes.sort.join(", ")} via the EBS CSI driver and are released on destroy."
' "$ROOT_DIR"

echo "== Gateway transport guard (no plaintext public credentials) =="
# /api/v1/auth/login carries passwords and returns JWTs. No public listener,
# security group rule or client URL may use plain HTTP; the only plain-HTTP
# URL allowed is the loopback end of the authenticated kubectl port-forward.
transport_guard() {
  ruby - "$1" <<'RUBY'
require "yaml"
root = ARGV.fetch(0)
failures = []
ingress_path = File.join(root, "infra/k8s/sandbox/04-ingress.yaml")
ingress = YAML.load_file(ingress_path)
notes = ingress.dig("metadata", "annotations") || {}
failures << "04-ingress.yaml listeners must be exactly HTTPS:443" unless notes["alb.ingress.kubernetes.io/listen-ports"] == '[{"HTTPS": 443}]'
failures << "04-ingress.yaml must not configure an HTTP redirect listener" if notes.key?("alb.ingress.kubernetes.io/ssl-redirect")
failures << "04-ingress.yaml must attach an ACM certificate" unless notes["alb.ingress.kubernetes.io/certificate-arn"].to_s.include?("CERTIFICATE_ARN")
failures << "04-ingress.yaml must pin a TLS 1.2+/1.3 policy" unless notes["alb.ingress.kubernetes.io/ssl-policy"].to_s.start_with?("ELBSecurityPolicy-TLS13")
failures << "04-ingress.yaml must restrict to the gateway hostname" unless ingress.dig("spec", "rules", 0, "host").to_s.include?("GATEWAY_HOSTNAME")
network = File.read(File.join(root, "infra/terraform/modules/network/main.tf"))
alb_sg = network[/resource "aws_security_group" "alb" \{.*?\n\}\n/m].to_s
failures << "ALB security group not found" if alb_sg.empty?
failures << "ALB security group must be created only for alb-https (count)" unless alb_sg.include?("count = var.create_gateway_alb ? 1 : 0")
ingress_blocks = alb_sg.scan(/^\s*ingress \{.*?^\s*\}/m)
ports = ingress_blocks.join.scan(/(from|to)_port\s*=\s*(\d+)/).map { |_, port| port.to_i }.uniq
failures << "ALB security group ingress may open port 443 only (found #{ports.inspect})" unless ingress_blocks.any? && ports == [443]
variables = File.read(File.join(root, "infra/terraform/envs/sandbox/variables.tf"))
failures << "gateway_exposure must default to port-forward" unless variables =~ /variable "gateway_exposure" \{\s*type\s*=\s*string\s*default\s*=\s*"port-forward"/m
Dir[File.join(root, "scripts/aws-*.sh")].each do |script|
  next if File.basename(script) == "aws-validate.sh"
  File.readlines(script).each_with_index do |line, index|
    next if line.strip.start_with?("#")
    line.scan(%r{http://[^\s"'/)]+}).each do |url|
      next if url.start_with?("http://127.0.0.1")
      failures << "#{File.basename(script)}:#{index + 1}: plain-HTTP URL #{url}"
    end
  end
end
smoke = File.read(File.join(root, "scripts/aws-smoke-test.sh"))
failures << "aws-smoke-test.sh authenticated calls must use only the loopback BASE_URL" unless smoke.scan(/^BASE_URL=.*$/) == ['BASE_URL="http://127.0.0.1:${LOCAL_PORT}"']
alb_section = smoke[/alb-https\)(.*?);;/m, 1].to_s
failures << "aws-smoke-test.sh must not send credentials/tokens to the ALB" if alb_section.match?(/auth\/login|auth\/register|auth-header|Authorization|--data|newman/)
failures << "aws-smoke-test.sh must run Newman against the loopback tunnel" unless smoke.include?('newman.sh --env-var "baseUrl=$BASE_URL"')
unless failures.empty?
  warn "Gateway transport guard failed:"
  failures.each { |f| warn "  #{f}" }
  exit 1
end
puts "Gateway is port-forward by default; the opt-in ALB is HTTPS:443-only with ACM, TLS1.3 policy and host rule."
RUBY
}
transport_guard "$ROOT_DIR"
# Prove the guard actually fires on the regressions it exists to prevent.
GUARD_DIR="$(mktemp -d)"
trap 'rm -rf "$GUARD_DIR"' EXIT
for regression in listener sg-port80 client-url default-mode; do
  rm -rf "$GUARD_DIR/copy" && mkdir -p "$GUARD_DIR/copy/infra/k8s" "$GUARD_DIR/copy/infra/terraform/modules" "$GUARD_DIR/copy/infra/terraform/envs"
  cp -R "$ROOT_DIR/infra/k8s/sandbox" "$GUARD_DIR/copy/infra/k8s/"
  cp -R "$ROOT_DIR/infra/terraform/modules/network" "$GUARD_DIR/copy/infra/terraform/modules/"
  cp -R "$ROOT_DIR/infra/terraform/envs/sandbox" "$GUARD_DIR/copy/infra/terraform/envs/"
  cp -R "$ROOT_DIR/scripts" "$GUARD_DIR/copy/"
  case "$regression" in
    listener) sed -i.bak 's/\[{"HTTPS": 443}\]/[{"HTTP": 80}, {"HTTPS": 443}]/' "$GUARD_DIR/copy/infra/k8s/sandbox/04-ingress.yaml" ;;
    sg-port80) sed -i.bak 's/from_port   = 443/from_port   = 80/' "$GUARD_DIR/copy/infra/terraform/modules/network/main.tf" ;;
    client-url) printf 'BASE_URL="http://%s"\n' '${ALB_HOST}' >>"$GUARD_DIR/copy/scripts/aws-smoke-test.sh" ;;
    default-mode) sed -i.bak '/variable "gateway_exposure"/,/^}/s/default = "port-forward"/default = "alb-https"/' "$GUARD_DIR/copy/infra/terraform/envs/sandbox/variables.tf" ;;
  esac
  if transport_guard "$GUARD_DIR/copy" >/dev/null 2>&1; then
    echo "Transport guard did not detect the '$regression' regression." >&2
    exit 1
  fi
done
rm -rf "$GUARD_DIR"
trap - EXIT
echo "Transport guard rejects HTTP listeners, port-80 rules, plaintext client URLs and a public default."

echo "== Rendered sandbox manifest YAML (both gateway exposure modes) =="
RENDER_DIR="$(mktemp -d)"
trap 'rm -rf "$RENDER_DIR"' EXIT
write_fake_outputs() {
  python3 - "$1" "$2" <<'PY'
import json
import pathlib
import sys

path, mode = sys.argv[1:]
services = [
    "auth-service", "product-service", "customer-service", "pharmacy-service",
    "inventory-service", "prescription-service", "order-service", "payment-service",
    "notification-service", "audit-service",
]
images = services + ["api-gateway", "external-mock-service"]
secrets = {service: f"arn:aws:secretsmanager:us-east-1:111111111111:secret:{service}" for service in services}
roles = {service: f"arn:aws:iam::111111111111:role/pharmacy-sbx-irsa-{service}" for service in services}
https = mode == "alb-https"
values = {
    "ecr_repository_urls": {
        f"pharmacy-sbx-{service}": f"111111111111.dkr.ecr.us-east-1.amazonaws.com/pharmacy-sbx-{service}"
        for service in images
    },
    "eks_cluster_name": "pharmacy-sbx",
    "rds_endpoint": "sandbox.example.invalid",
    "rds_port": 3306,
    "database_secret_arns": secrets,
    "rds_master_secret_arn": "arn:aws:secretsmanager:us-east-1:111111111111:secret:master",
    "jwt_keypair_secret_arn": "arn:aws:secretsmanager:us-east-1:111111111111:secret:jwt-private",
    "jwt_public_key_secret_arn": "arn:aws:secretsmanager:us-east-1:111111111111:secret:jwt-public",
    "rds_master_username": "admin_master",
    "workload_role_arns": roles,
    "db_bootstrap_role_arn": "arn:aws:iam::111111111111:role/pharmacy-sbx-db-bootstrap",
    "gateway_exposure": mode,
    "alb_controller_role_arn": "arn:aws:iam::111111111111:role/pharmacy-sbx-alb-controller" if https else "",
    "alb_security_group_id": "sg-0123456789abcdef0" if https else "",
    "gateway_hostname": "api.sandbox.example.dev" if https else "",
    "gateway_certificate_arn": (
        "arn:aws:acm:us-east-1:111111111111:certificate/12345678-1234-1234-1234-123456789012" if https else ""
    ),
    "vpc_id": "vpc-0123456789abcdef0",
    "resource_tags": {
        "project": "pharmacy-enterprise-platform",
        "owner": "validation",
        "environment": "sandbox-temp",
        "expiration": "2026-10-05",
        "cost-center": "learning-exercise",
    },
}
pathlib.Path(path).write_text(json.dumps({
    name: {"value": value, "type": "string", "sensitive": False}
    for name, value in values.items()
}))
PY
}

check_rendered_manifests() {
  ROOT_DIR="$ROOT_DIR" ruby - "$1" "$2" <<'RUBY'
require "yaml"
require "json"
dir, mode = ARGV.fetch(0), ARGV.fetch(1)
manifest_files = Dir[File.join(dir, "*.yaml")].reject { |file| File.basename(file) == "values-aws.yaml" }
documents = manifest_files.flat_map { |file| YAML.load_stream(File.read(file)) }.compact
deployments = documents.select { |document| document["kind"] == "Deployment" }.map { |document| document.dig("metadata", "name") }
expected = %w[auth-service api-gateway product-service customer-service pharmacy-service inventory-service prescription-service order-service payment-service notification-service audit-service external-mock-service]
missing = expected - deployments
abort("Missing JVM workloads from rendered manifests: #{missing.join(", ")}") unless missing.empty?
abort("Unexpected public Services; only the opt-in HTTPS Ingress may be public.") if documents.any? { |document| document["kind"] == "Service" && %w[LoadBalancer NodePort].include?(document.dig("spec", "type")) }
ingresses = documents.select { |document| document["kind"] == "Ingress" }
alb_sa = documents.any? { |d| d["kind"] == "ServiceAccount" && d.dig("metadata", "name") == "aws-load-balancer-controller" }
if mode == "port-forward"
  abort("port-forward mode must render no Ingress (found #{ingresses.length}).") unless ingresses.empty?
  abort("port-forward mode must not render the ALB controller ServiceAccount.") if alb_sa
else
  abort("alb-https mode expects exactly one gateway Ingress.") unless ingresses.length == 1
  abort("alb-https mode needs the ALB controller ServiceAccount.") unless alb_sa
  ingress = ingresses.first
  notes = ingress.dig("metadata", "annotations")
  abort("The only Ingress must target api-gateway.") unless ingress.dig("spec", "rules", 0, "http", "paths", 0, "backend", "service", "name") == "api-gateway"
  abort("Ingress listeners must be HTTPS:443 only.") unless JSON.parse(notes["alb.ingress.kubernetes.io/listen-ports"]) == [{ "HTTPS" => 443 }]
  abort("Ingress must carry the ACM certificate ARN.") unless notes["alb.ingress.kubernetes.io/certificate-arn"].start_with?("arn:aws:acm:us-east-1:")
  abort("Ingress host must be the gateway hostname.") unless ingress.dig("spec", "rules", 0, "host") == "api.sandbox.example.dev"
end
statefulsets = documents.select { |document| document["kind"] == "StatefulSet" }
abort("Kafka must be exactly one private broker.") unless statefulsets.length == 1 && statefulsets.first.dig("metadata", "name") == "kafka" && statefulsets.first.dig("spec", "replicas") == 1
abort("Kafka broker service must remain ClusterIP-only.") unless documents.any? { |document| document["kind"] == "Service" && document.dig("metadata", "name") == "kafka" && document.dig("spec", "type") == "ClusterIP" }
broker_env = (statefulsets.first.dig("spec", "template", "spec", "containers", 0, "env") || []).map { |entry| [entry["name"], entry["value"]] }.to_h
abort("Kafka broker must disable topic auto-creation so retry/DLT topics are explicit.") unless broker_env["KAFKA_AUTO_CREATE_TOPICS_ENABLE"] == "false"
topic_job = documents.find { |document| document["kind"] == "Job" && document.dig("metadata", "name") == "kafka-topic-bootstrap" }
abort("Missing kafka-topic-bootstrap Job.") unless topic_job
topic_script = topic_job.dig("spec", "template", "spec", "containers", 0, "args").join("\n")
abort("Topic Job must create topics idempotently.") unless topic_script.include?("--if-not-exists")
local_topics = File.read(File.join(ENV.fetch("ROOT_DIR"), "scripts/local-kafka-init.sh"))[/^topics="([^"]+)"/, 1].split
abort("Topic Job domain topics must match scripts/local-kafka-init.sh.") unless topic_script[/domain_topics="([^"]+)"/, 1].split.sort == local_topics.sort && local_topics.length == 5
%w[.retry .dlt].each { |suffix| abort("Topic Job must create #{suffix} topics.") unless topic_script.include?("\"$topic#{suffix}\"") }
database_services = %w[auth-service product-service customer-service pharmacy-service inventory-service prescription-service order-service payment-service notification-service audit-service]
database_services.each do |service|
  deployment = documents.find { |document| document["kind"] == "Deployment" && document.dig("metadata", "name") == service }
  fetcher = deployment&.dig("spec", "template", "spec", "initContainers")&.find { |container| container["name"].include?("fetch") }
  script = fetcher&.fetch("args", []).join("\n")
  abort("#{service} must fetch its database secret through an init container.") if script.empty?
  abort("#{service} secret retrieval must not log or trace secret values.") if script.match?(/\btee\b|set\s+-x/)
  abort("#{service} secret files must be restricted, atomically published files.") unless script.include?("umask 077") && script.include?("chmod 600") && script.include?(".tmp") && script.include?("mv ")
end
mock = documents.find { |d| d["kind"] == "Deployment" && d.dig("metadata", "name") == "external-mock-service" }
mock_env = mock.dig("spec", "template", "spec", "containers", 0, "env").to_h { |e| [e["name"], e["value"]] }
abort("external-mock-service must set EXTERNAL_MOCK_SERVICE_PORT=8080 (service-link env otherwise breaks server.port).") unless mock_env["EXTERNAL_MOCK_SERVICE_PORT"] == "8080"
abort("The chart values overlay must be rendered for chart-mode migration.") unless File.exist?(File.join(dir, "values-aws.yaml"))
overlay = YAML.load_file(File.join(dir, "values-aws.yaml"))
abort("The chart overlay must define all 12 workloads.") unless (expected - overlay.fetch("services").keys).empty?
abort("Chart overlay must set EXTERNAL_MOCK_SERVICE_PORT=8080 for external-mock-service.") unless overlay.dig("services", "external-mock-service", "env", "EXTERNAL_MOCK_SERVICE_PORT") == "8080"
abort("The chart overlay must not contain secret material.") if File.read(File.join(dir, "values-aws.yaml")).match?(/BEGIN [A-Z ]*PRIVATE KEY|password:\s*\S/)
abort("A rendered manifest still contains a placeholder.") if Dir[File.join(dir, "*.yaml")].any? { |file| File.read(file).match?(/__[A-Z0-9_]+__/) }
summary = mode == "port-forward" ? "no Ingress/ALB (port-forward)" : "one HTTPS-only gateway Ingress"
puts "[#{mode}] Parsed #{documents.length} YAML documents, all 12 JVM Deployments, #{summary}."
RUBY
}

for mode in alb-https port-forward; do
  mkdir -p "$RENDER_DIR/$mode"
  write_fake_outputs "$RENDER_DIR/$mode-outputs.json" "$mode"
  bash "$ROOT_DIR/scripts/aws-render-manifests.sh" "$RENDER_DIR/$mode-outputs.json" \
    "$ROOT_DIR/infra/k8s/sandbox" "$RENDER_DIR/$mode" us-east-1 pharmacy-sbx sha-validation
  check_rendered_manifests "$RENDER_DIR/$mode" "$mode"
done
# A port-forward render into a directory holding a previous alb-https render
# must remove the stale public manifests rather than leave them for kubectl.
cp "$RENDER_DIR/alb-https/04-ingress.yaml" "$RENDER_DIR/port-forward/"
bash "$ROOT_DIR/scripts/aws-render-manifests.sh" "$RENDER_DIR/port-forward-outputs.json" \
  "$ROOT_DIR/infra/k8s/sandbox" "$RENDER_DIR/port-forward" us-east-1 pharmacy-sbx sha-validation
check_rendered_manifests "$RENDER_DIR/port-forward" port-forward >/dev/null
# The renderer must fail closed on an unknown mode or an incomplete HTTPS set.
python3 - "$RENDER_DIR" <<'PY'
import json
import pathlib
import sys
d = pathlib.Path(sys.argv[1])
base = json.loads((d / "alb-https-outputs.json").read_text())
bad_mode = json.loads(json.dumps(base)); bad_mode["gateway_exposure"]["value"] = "alb-http"
no_cert = json.loads(json.dumps(base)); no_cert["gateway_certificate_arn"]["value"] = ""
(d / "bad-mode.json").write_text(json.dumps(bad_mode))
(d / "no-cert.json").write_text(json.dumps(no_cert))
PY
for broken in bad-mode no-cert; do
  mkdir -p "$RENDER_DIR/$broken"
  if bash "$ROOT_DIR/scripts/aws-render-manifests.sh" "$RENDER_DIR/$broken.json" \
      "$ROOT_DIR/infra/k8s/sandbox" "$RENDER_DIR/$broken" us-east-1 pharmacy-sbx sha-validation >/dev/null 2>&1; then
    echo "Renderer accepted invalid gateway outputs ($broken)." >&2
    exit 1
  fi
done
echo "Renderer rejects unknown exposure modes and HTTPS renders without a certificate."
MANIFEST_DIR="$RENDER_DIR/port-forward"

CHART_DIR="$ROOT_DIR/infra/helm/pharmacy-platform"
if command -v helm >/dev/null 2>&1 && [ -d "$CHART_DIR" ]; then
  echo "== helm template: canonical chart with the rendered AWS overlay =="
  helm template pharmacy-platform "$CHART_DIR" \
    --namespace pharmacy \
    --values "$MANIFEST_DIR/values-aws.yaml" >"$RENDER_DIR/chart-aws.yaml"
  ruby - "$RENDER_DIR/chart-aws.yaml" <<'RUBY'
require "yaml"
documents = YAML.load_stream(File.read(ARGV.fetch(0))).compact
deployments = documents.select { |d| d["kind"] == "Deployment" }.map { |d| d.dig("metadata", "name") }
expected = %w[auth-service api-gateway product-service customer-service pharmacy-service inventory-service prescription-service order-service payment-service notification-service audit-service external-mock-service]
missing = expected - deployments
abort("Chart render missing workloads: #{missing.join(", ")}") unless missing.empty?
abort("Chart render must not expose a LoadBalancer Service.") if documents.any? { |d| d["kind"] == "Service" && d.dig("spec", "type") == "LoadBalancer" }
gateway = documents.find { |d| d["kind"] == "Service" && d.dig("metadata", "name") == "api-gateway" }
port = gateway.dig("spec", "ports", 0, "port")
abort("Gateway Service port must be 8080, got #{port}.") unless port == 8080
puts "Chart rendered #{documents.length} documents for all 12 AWS workloads."
RUBY
else
  echo "NOTE: helm or the shared chart is unavailable; skipped chart-overlay render check."
fi

OBS_CHART="$ROOT_DIR/infra/helm/observability"
if command -v helm >/dev/null 2>&1 && compgen -G "$OBS_CHART/charts/*.tgz" >/dev/null; then
  echo "== Observability: EKS profile + AWS overlay, privacy and node-capacity budget =="
  helm template pharmacy-observability "$OBS_CHART" --namespace pharmacy-observability \
    -f "$OBS_CHART/values-eks.yaml" -f "$ROOT_DIR/infra/k8s/sandbox/observability-values-aws.yaml" \
    >"$RENDER_DIR/observability-aws.yaml"
  ruby - "$RENDER_DIR/observability-aws.yaml" "$MANIFEST_DIR" <<'RUBY'
require "yaml"
obs_file, fleet_dir = ARGV
def mem(v)
  return nil if v.nil?
  s = v.to_s; n = s.to_f
  case s
  when /Gi$/ then n * 1024
  when /Mi$/ then n
  when /Ki$/ then n / 1024
  else n / (1024 * 1024)
  end
end
def cpu(v)
  return 0 if v.nil?
  v.to_s.end_with?("m") ? v.to_f : v.to_f * 1000
end
def containers(doc)
  case doc["kind"]
  when "Prometheus", "Alertmanager" then [{ "name" => doc["kind"], "resources" => doc.dig("spec", "resources") || {} }]
  when "Deployment", "StatefulSet", "DaemonSet" then doc.dig("spec", "template", "spec", "containers") || []
  else []
  end
end
def totals(docs, label)
  mem_limit = mem_request = cpu_request = 0
  unbounded = []
  docs.each do |doc|
    replicas = doc["kind"] == "DaemonSet" ? 2 : (doc.dig("spec", "replicas") || 1)
    containers(doc).each do |c|
      limit = mem(c.dig("resources", "limits", "memory"))
      unbounded << "#{label}:#{doc.dig("metadata", "name")}/#{c["name"]}" if limit.nil?
      mem_limit += replicas * (limit || 0)
      mem_request += replicas * (mem(c.dig("resources", "requests", "memory")) || 0)
      cpu_request += replicas * cpu(c.dig("resources", "requests", "cpu"))
    end
  end
  [mem_limit, mem_request, cpu_request, unbounded]
end
obs = YAML.load_stream(File.read(obs_file)).compact
fleet = Dir[File.join(fleet_dir, "*.yaml")].reject { |f| File.basename(f) == "values-aws.yaml" }
  .flat_map { |f| YAML.load_stream(File.read(f)).compact }
abort("Observability must not create Ingress/LoadBalancer/NodePort.") if obs.any? { |d| d["kind"] == "Ingress" || %w[LoadBalancer NodePort].include?(d.dig("spec", "type")) }
abort("AWS overlay must disable node-exporter DaemonSet.") if obs.any? { |d| d["kind"] == "DaemonSet" }
abort("AWS overlay must disable Alertmanager.") if obs.any? { |d| d["kind"] == "Alertmanager" }
grafana = obs.find { |d| d["kind"] == "Deployment" && d.dig("metadata", "name") == "pharmacy-observability-grafana" }
env = grafana.dig("spec", "template", "spec", "containers").flat_map { |c| c["env"] || [] }
pw = env.find { |e| e["name"] == "GF_SECURITY_ADMIN_PASSWORD" }
abort("Grafana admin password must come from Secret grafana-admin.") unless pw && pw.dig("valueFrom", "secretKeyRef", "name") == "grafana-admin"
o_lim, o_req, o_cpu, o_unb = totals(obs, "obs")
f_lim, f_req, f_cpu, f_unb = totals(fleet, "fleet")
abort("Containers without a memory limit: #{(o_unb + f_unb).join(", ")}") unless (o_unb + f_unb).empty?
# Two t4g.large: ~7000Mi allocatable each after kube/system reservations and
# eviction threshold; keep 1 GiB for kube-system (CoreDNS, VPC CNI, EBS CSI).
mem_budget = 2 * 7000 - 1024
cpu_budget = 2 * 1930 - 600
total_lim = o_lim + f_lim
total_cpu = o_cpu + f_cpu
abort(format("Memory limits %.0fMi exceed the %dMi two-node budget.", total_lim, mem_budget)) if total_lim > mem_budget
abort(format("CPU requests %.0fm exceed the %dm two-node budget.", total_cpu, cpu_budget)) if total_cpu > cpu_budget
collector = "http://pharmacy-observability-opentelemetry-collector.pharmacy-observability.svc.cluster.local:4318/v1/traces"
jvm = %w[auth-service api-gateway product-service customer-service pharmacy-service inventory-service prescription-service order-service payment-service notification-service audit-service external-mock-service]
jvm.each do |name|
  d = fleet.find { |x| x["kind"] == "Deployment" && x.dig("metadata", "name") == name }
  abort("Missing Deployment #{name}.") unless d
  e = (d.dig("spec", "template", "spec", "containers", 0, "env") || []).to_h { |x| [x["name"], x["value"]] }
  abort("#{name} must export traces to the in-cluster collector.") unless e["OTEL_EXPORTER_OTLP_ENDPOINT"] == collector
  abort("#{name} must set TRACING_SAMPLING_PROBABILITY > 0.") unless e["TRACING_SAMPLING_PROBABILITY"].to_f > 0
  svc = fleet.find { |x| x["kind"] == "Service" && x.dig("metadata", "name") == name }
  notes = svc&.dig("metadata", "annotations") || {}
  abort("#{name} Service must carry prometheus.io scrape/path/port annotations.") unless notes["prometheus.io/scrape"] == "true" && notes["prometheus.io/path"] == "/actuator/prometheus"
  ports = (svc.dig("spec", "ports") || []).map { |x| x["port"].to_s }
  abort("#{name} prometheus.io/port #{notes["prometheus.io/port"]} is not a Service port.") unless ports.include?(notes["prometheus.io/port"].to_s)
end
printf("Observability is ClusterIP-only with bounded containers. Memory limits: fleet %.0fMi + obs %.0fMi = %.0fMi of %dMi (%.0f%%); requests %.0fMi; CPU requests %.0fm of %dm.\n",
  f_lim, o_lim, total_lim, mem_budget, 100.0 * total_lim / mem_budget, o_req + f_req, total_cpu, cpu_budget)
RUBY
  ruby -e '
    apply = File.read(ARGV[0]); destroy = File.read(ARGV[1])
    abort("aws-apply.sh must install the observability release with the AWS overlay.") unless apply.include?("helm upgrade --install pharmacy-observability") && apply.include?("observability-values-aws.yaml")
    abort("aws-apply.sh must provision Grafana credentials via the chart helper.") unless apply.include?("ensure-grafana-secret.sh")
    abort("aws-apply.sh must not pass the Grafana password on argv.") if apply.match?(/--from-literal=admin-password/)
    ns = destroy.index("delete namespace pharmacy-observability"); tf = destroy.index("terraform destroy")
    abort("aws-destroy.sh must delete the observability namespace before terraform destroy.") unless ns && tf && ns < tf
    puts "Apply installs observability; destroy removes it before Terraform."
  ' "$ROOT_DIR/scripts/aws-apply.sh" "$ROOT_DIR/scripts/aws-destroy.sh"
else
  echo "NOTE: helm or the observability chart dependencies are unavailable; skipped observability checks."
fi

echo "== EKS standard-support cost guard =="
ruby -e '
  root = ARGV[0]
  allowed = %w[1.34 1.35 1.36]
  eks = File.read(File.join(root, "infra/terraform/modules/eks/main.tf"))
  abort("aws_eks_cluster must set upgrade_policy { support_type = \"STANDARD\" }.") unless eks.match?(/upgrade_policy\s*\{\s*support_type\s*=\s*"STANDARD"\s*\}/m)
  abort("EKS must never opt into EXTENDED support.") if eks.include?("EXTENDED")
  files = ["infra/terraform/modules/eks/main.tf", "infra/terraform/envs/sandbox/variables.tf"]
  files.each do |file|
    text = File.read(File.join(root, file))
    block = text[/variable "kubernetes_version" \{.*?\n\}/m] or abort("#{file} lacks kubernetes_version.")
    abort("#{file} kubernetes_version default must be 1.35.") unless block.include?(%(default = "1.35"))
    list = block[/contains\(\[([^\]]+)\]/, 1].to_s.scan(/"([^"]+)"/).flatten
    abort("#{file} kubernetes_version validation must allow only standard-support #{allowed.join("/")}, got #{list.inspect}.") unless list.sort == allowed
  end
  %w[aws-plan.sh aws-destroy.sh].each do |script|
    defaults = File.read(File.join(root, "scripts", script)).scan(/KUBERNETES_VERSION:-([0-9.]+)/).flatten
    abort("#{script} must default KUBERNETES_VERSION to 1.35.") if defaults.empty? || defaults.any? { |v| v != "1.35" }
  end
  ["terraform.tfvars.example", "terraform.tfvars"].each do |name|
    path = File.join(root, "infra/terraform/envs/sandbox", name)
    next unless File.exist?(path)
    version = File.read(path)[/^\s*kubernetes_version\s*=\s*"([^"]+)"/, 1]
    abort("#{name} sets kubernetes_version=#{version}, which is not standard support.") if version && !allowed.include?(version)
  end
  puts "EKS pinned to 1.35 with upgrade_policy STANDARD; only 1.34/1.35/1.36 accepted."
' "$ROOT_DIR"

echo "== Kafka topic bootstrap ordering =="
ruby -e '
  apply = File.read(ARGV[0])
  broker = apply.index("rollout status statefulset/kafka")
  wait = apply.index("wait --for=condition=complete job/kafka-topic-bootstrap")
  fleet = apply.index("Applying all 12 internal JVM workloads")
  abort("aws-apply.sh must wait for the topic Job after the broker rollout and before the fleet.") unless broker && wait && fleet && broker < wait && wait < fleet
  puts "Topic Job runs after the broker is ready and before any producer or consumer."
' "$ROOT_DIR/scripts/aws-apply.sh"

if command -v tfsec >/dev/null 2>&1; then
  echo "== tfsec (security scan) =="
  tfsec "$TF_DIR"
elif command -v checkov >/dev/null 2>&1; then
  echo "== checkov (security scan) =="
  checkov -d "$TF_DIR"
else
  echo "NOTE: neither tfsec nor checkov is installed. Install one for security scanning:"
  echo "  brew install tfsec"
  echo "  (or) brew install checkov"
fi

echo "All checks passed."
