#!/usr/bin/env sh
set -eu
kubectl apply --dry-run=client -f infra/kubernetes/base/namespace.yaml
kubectl apply --dry-run=client -f infra/kubernetes/base/common-configmap.yaml
kubectl apply --dry-run=client -f infra/kubernetes/base/common-secret.example.yaml
kubectl apply --dry-run=client -f infra/kubernetes/base/api-gateway.yaml
kubectl apply --dry-run=client -f infra/kubernetes/base/product-service.yaml
kubectl apply --dry-run=client -f infra/kubernetes/base/auth-service.yaml
