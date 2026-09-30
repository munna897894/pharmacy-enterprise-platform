#!/usr/bin/env bash
# Idempotently provisions the Grafana admin Secret consumed through existingSecret.
# An existing Secret is never overwritten unless GRAFANA_ADMIN_ROTATE=true.
set -euo pipefail

namespace="${OBSERVABILITY_NAMESPACE:-pharmacy-observability}"
secret_name="${GRAFANA_ADMIN_SECRET_NAME:-grafana-admin}"
admin_user="${GRAFANA_ADMIN_USER:-admin}"
rotate="${GRAFANA_ADMIN_ROTATE:-false}"
kube() {
  if [[ -n "${KUBE_CONTEXT:-}" ]]; then
    kubectl --context "$KUBE_CONTEXT" "$@"
  else
    kubectl "$@"
  fi
}

command -v kubectl >/dev/null || { echo "kubectl is required" >&2; exit 1; }

if ! kube get namespace "$namespace" >/dev/null 2>&1; then
  kube create namespace "$namespace" >/dev/null
  echo "Created namespace $namespace"
fi

if kube -n "$namespace" get secret "$secret_name" >/dev/null 2>&1 && [[ "$rotate" != "true" ]]; then
  for key in admin-user admin-password; do
    value="$(kube -n "$namespace" get secret "$secret_name" -o "jsonpath={.data.$key}")"
    [[ -n "$value" ]] || { echo "Secret $namespace/$secret_name is missing key $key" >&2; exit 1; }
  done
  echo "Secret $namespace/$secret_name already exists; left unchanged"
  exit 0
fi

password="${GRAFANA_ADMIN_PASSWORD:-}"
if [[ -z "$password" ]]; then
  command -v openssl >/dev/null || { echo "openssl is required to generate a password" >&2; exit 1; }
  password="$(openssl rand -base64 36 | tr -d '\n/+=' | cut -c1-32)"
fi
if (( ${#password} < 16 )); then
  echo "GRAFANA_ADMIN_PASSWORD must be at least 16 characters" >&2
  exit 1
fi

b64() {
  printf '%s' "$1" | base64 | tr -d '\n'
}

# The manifest is streamed on stdin so the password never appears in any argv.
{
  printf 'apiVersion: v1\nkind: Secret\ntype: Opaque\n'
  printf 'metadata:\n  name: %s\n  namespace: %s\n' "$secret_name" "$namespace"
  printf '  labels:\n    app.kubernetes.io/part-of: pharmacy-observability\n'
  printf 'data:\n  admin-user: %s\n  admin-password: %s\n' "$(b64 "$admin_user")" "$(b64 "$password")"
} | kube apply -f - >/dev/null
unset password

echo "Secret $namespace/$secret_name is ready; the password was not printed"
if [[ "$rotate" == "true" ]] && kube -n "$namespace" get deployment pharmacy-observability-grafana >/dev/null 2>&1; then
  kube -n "$namespace" rollout restart deployment/pharmacy-observability-grafana >/dev/null
  kube -n "$namespace" rollout status deployment/pharmacy-observability-grafana --timeout=180s >/dev/null
  # Grafana persists the admin password in its database; reset it from the pod's Secret-backed env via stdin.
  kube -n "$namespace" exec deploy/pharmacy-observability-grafana -c grafana -- \
    sh -c 'printf %s "$GF_SECURITY_ADMIN_PASSWORD" | grafana cli admin reset-admin-password --password-from-stdin' >/dev/null
  echo "Restarted Grafana and applied the rotated password"
fi
echo "Read it with: kubectl -n $namespace get secret $secret_name -o jsonpath='{.data.admin-password}' | base64 --decode"
