#!/usr/bin/env sh
set -eu

if [ "$(kubectl config current-context)" != "docker-desktop" ]; then
  echo "Expected Docker Desktop Kubernetes context; refusing to create a Secret elsewhere." >&2
  exit 1
fi

env_file="${K8S_ENV_FILE:-.env}"
if [ ! -f "$env_file" ]; then
  echo "Missing $env_file; provision matching MySQL users first." >&2
  exit 1
fi

keys="AUTH_DB_PASSWORD PRODUCT_DB_PASSWORD CUSTOMER_DB_PASSWORD PHARMACY_DB_PASSWORD INVENTORY_DB_PASSWORD PRESCRIPTION_DB_PASSWORD ORDER_DB_PASSWORD PAYMENT_DB_PASSWORD NOTIFICATION_DB_PASSWORD AUDIT_DB_PASSWORD"
for key in $keys; do
  if ! grep -q "^${key}=" "$env_file"; then
    echo "Missing $key in $env_file." >&2
    exit 1
  fi
done

private_key=services/auth-service/src/test/resources/keys/private_key.pem
public_key=services/auth-service/src/test/resources/keys/public_key.pem
if [ ! -f "$private_key" ] || [ ! -f "$public_key" ]; then
  echo "Missing local-only JWT fixture keys." >&2
  exit 1
fi

grep -E '^(AUTH_DB_PASSWORD|PRODUCT_DB_PASSWORD|CUSTOMER_DB_PASSWORD|PHARMACY_DB_PASSWORD|INVENTORY_DB_PASSWORD|PRESCRIPTION_DB_PASSWORD|ORDER_DB_PASSWORD|PAYMENT_DB_PASSWORD|NOTIFICATION_DB_PASSWORD|AUDIT_DB_PASSWORD)=' "$env_file" |
  kubectl -n pharmacy create secret generic pharmacy-secrets \
    --from-env-file=/dev/stdin \
    --dry-run=client -o json |
  python3 -c 'import base64, json, pathlib, sys
secret = json.load(sys.stdin)
for path in map(pathlib.Path, sys.argv[1:]):
    secret["data"][path.name] = base64.b64encode(path.read_bytes()).decode("ascii")
json.dump(secret, sys.stdout)
' "$private_key" "$public_key" |
  kubectl apply -f -
