#!/usr/bin/env sh
set -eu

if [ "$(kubectl config current-context)" != "docker-desktop" ]; then
  echo "Expected Docker Desktop Kubernetes context; refusing to validate another cluster." >&2
  exit 1
fi

helm lint infra/helm/pharmacy-platform \
  -f infra/helm/pharmacy-platform/values-local.yaml
helm template pharmacy infra/helm/pharmacy-platform --namespace pharmacy \
  -f infra/helm/pharmacy-platform/values-local.yaml |
  kubectl create --dry-run=client -f - -o name
