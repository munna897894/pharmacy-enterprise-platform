#!/usr/bin/env bash
set -euo pipefail

if [[ "$#" -ne 6 ]]; then
  echo "Usage: aws-render-manifests.sh <terraform-outputs.json> <sandbox-manifests-dir> <render-dir> <region> <name-prefix> <image-tag>" >&2
  exit 2
fi

OUTPUTS_FILE="$1"
K8S_DIR="$2"
RENDER_DIR="$3"
AWS_REGION="$4"
NAME_PREFIX="$5"
IMAGE_TAG="$6"

python3 - "$OUTPUTS_FILE" "$K8S_DIR" "$RENDER_DIR" "$AWS_REGION" "$NAME_PREFIX" "$IMAGE_TAG" <<'PY'
import json
import pathlib
import sys

outputs_path, k8s_dir, render_dir, region, prefix, tag = sys.argv[1:]
outputs = json.loads(pathlib.Path(outputs_path).read_text())
out = {key: value["value"] for key, value in outputs.items()}
repos = out["ecr_repository_urls"]
db_secrets = out["database_secret_arns"]
render = pathlib.Path(render_dir)
source = pathlib.Path(k8s_dir)
resource_tags = ",".join(f"{key}={value}" for key, value in sorted(out["resource_tags"].items()))

def repository(service):
    key = f"{prefix}-{service}"
    if key not in repos:
        raise SystemExit(f"Terraform output is missing ECR repository {key}.")
    return f"{repos[key]}:{tag}"

repos_services = (
    "auth-service", "api-gateway", "product-service", "customer-service", "pharmacy-service",
    "inventory-service", "prescription-service", "order-service", "payment-service",
    "notification-service", "audit-service", "external-mock-service",
)
replacements = {
    "__AWS_REGION__": region,
    "__AUTH_SERVICE_IMAGE__": repository("auth-service"),
    "__API_GATEWAY_IMAGE__": repository("api-gateway"),
    "__PRODUCT_SERVICE_IMAGE__": repository("product-service"),
    "__RDS_ENDPOINT__": out["rds_endpoint"],
    "__AUTH_DB_SECRET_ARN__": db_secrets["auth-service"],
    "__PRODUCT_DB_SECRET_ARN__": db_secrets["product-service"],
    "__JWT_PRIVATE_KEY_SECRET_ARN__": out["jwt_keypair_secret_arn"],
    "__JWT_PUBLIC_KEY_SECRET_ARN__": out["jwt_public_key_secret_arn"],
    "__AWS_RESOURCE_TAGS__": resource_tags,
    "__DB_BOOTSTRAP_ROLE_ARN__": out["db_bootstrap_role_arn"],
    "__RDS_MASTER_SECRET_ARN__": out["rds_master_secret_arn"],
    "__RDS_MASTER_USERNAME__": out["rds_master_username"],
    "__CUSTOMER_DB_SECRET_ARN__": db_secrets["customer-service"],
    "__PHARMACY_DB_SECRET_ARN__": db_secrets["pharmacy-service"],
    "__INVENTORY_DB_SECRET_ARN__": db_secrets["inventory-service"],
    "__PRESCRIPTION_DB_SECRET_ARN__": db_secrets["prescription-service"],
    "__ORDER_DB_SECRET_ARN__": db_secrets["order-service"],
    "__PAYMENT_DB_SECRET_ARN__": db_secrets["payment-service"],
    "__NOTIFICATION_DB_SECRET_ARN__": db_secrets["notification-service"],
    "__AUDIT_DB_SECRET_ARN__": db_secrets["audit-service"],
    "__EXTERNAL_MOCK_SERVICE_IMAGE__": repository("external-mock-service"),
}
for service, role_arn in out["workload_role_arns"].items():
    replacements[f"__{service.replace('-', '_').upper()}_ROLE_ARN__"] = role_arn
for service in repos_services:
    replacements[f"__{service.replace('-', '_').upper()}_IMAGE__"] = repository(service)

# Gateway exposure is fail-closed: anything other than the two known modes
# aborts, and the public ALB manifests are rendered only for alb-https with
# every HTTPS input present. Stale ALB manifests from an earlier render are
# removed so a port-forward apply can never pick them up.
exposure = out["gateway_exposure"]
alb_manifests = ("04-alb-controller-service-account.yaml", "04-ingress.yaml")
if exposure == "alb-https":
    alb_values = {
        "__ALB_SECURITY_GROUP_ID__": out["alb_security_group_id"],
        "__ALB_CONTROLLER_ROLE_ARN__": out["alb_controller_role_arn"],
        "__GATEWAY_CERTIFICATE_ARN__": out["gateway_certificate_arn"],
        "__GATEWAY_HOSTNAME__": out["gateway_hostname"],
    }
    missing = sorted(token for token, value in alb_values.items() if not value)
    if missing:
        raise SystemExit("alb-https is missing required Terraform outputs: " + ", ".join(missing))
    if not out["gateway_certificate_arn"].startswith(f"arn:aws:acm:{region}:"):
        raise SystemExit("Gateway certificate must be an ACM certificate in the cluster region.")
    replacements.update(alb_values)
elif exposure == "port-forward":
    alb_manifests = ()
    for stale in ("04-alb-controller-service-account.yaml", "04-ingress.yaml"):
        (render / stale).unlink(missing_ok=True)
else:
    raise SystemExit(f"Unsupported gateway_exposure: {exposure!r}")

static_manifests = (
    "00-service-accounts.yaml",
    "00-redis.yaml",
    "01-auth-service.yaml",
    "02-product-service.yaml",
    "03-api-gateway.yaml",
    *alb_manifests,
    "05-db-bootstrap.yaml",
    "06-kafka.yaml",
    "06-kafka-topics.yaml",
    "08-external-mock-service.yaml",
    "09-storage.yaml",
)
for filename in static_manifests:
    text = (source / filename).read_text()
    for token, value in replacements.items():
        text = text.replace(token, value)
    if "__" in text:
        raise SystemExit(f"Unresolved placeholder in rendered {filename}.")
    (render / filename).write_text(text)

services = (
    ("customer-service", 8083, "pharmacy_customer", "customer_user"),
    ("pharmacy-service", 8084, "pharmacy_pharmacy", "pharmacy_user"),
    ("inventory-service", 8085, "pharmacy_inventory", "inventory_user"),
    ("prescription-service", 8086, "prescription_service", "prescription_user"),
    ("order-service", 8087, "pharmacy_order", "order_user"),
    ("payment-service", 8088, "pharmacy_payment", "payment_user"),
    ("notification-service", 8089, "pharmacy_notification", "notification_user"),
    ("audit-service", 8090, "pharmacy_audit", "audit_user"),
)
values_template = (source / "values-aws.yaml.tmpl").read_text()
for token, value in replacements.items():
    values_template = values_template.replace(token, value)
if "__" in values_template:
    raise SystemExit("Unresolved placeholder in rendered chart values overlay.")
(render / "values-aws.yaml").write_text(values_template)

template = (source / "07-jvm-service-template.yaml").read_text()
for service, port, schema, username in services:
    values = {
        "__SERVICE_NAME__": service,
        "__SERVICE_PORT__": str(port),
        "__SERVICE_IMAGE__": repository(service),
        "__DB_SCHEMA__": schema,
        "__DB_USER__": username,
        "__DB_SECRET_ARN__": db_secrets[service],
    }
    text = template
    for token, value in {**replacements, **values}.items():
        text = text.replace(token, value)
    if "__" in text:
        raise SystemExit(f"Unresolved placeholder in rendered {service}.")
    (render / f"{service}.yaml").write_text(text)
PY
