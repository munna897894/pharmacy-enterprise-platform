# Kubernetes base learning manifests

> **Learning slice, not the canonical local deployment.** These hand-written
> manifests retain the original gateway/auth/product exercise. For the current
> 12-workload Docker Desktop deployment, use
> `infra/helm/pharmacy-platform/values-local.yaml`,
> [`docs/self-run-guide.md`](../../../docs/self-run-guide.md), and the
> [local architecture diagram](../../../docs/architecture-local.md).

Apply the namespace, shared config, Redis, and the service slice:

```bash
kubectl apply -f namespace.yaml
kubectl apply -f common-configmap.yaml
kubectl apply -f common-secret.example.yaml
kubectl apply -f redis.yaml
kubectl apply -f api-gateway.yaml
kubectl apply -f product-service.yaml
kubectl apply -f auth-service.yaml
```

Build and push the learning-slice images to Docker Hub:

```bash
docker build -t munna897/pharmacy-api-gateway:local -f services/api-gateway/Dockerfile .
docker build -t munna897/pharmacy-product-service:local -f services/product-service/Dockerfile .
docker build -t munna897/pharmacy-auth-service:local -f services/auth-service/Dockerfile .
docker push munna897/pharmacy-api-gateway:local
docker push munna897/pharmacy-product-service:local
docker push munna897/pharmacy-auth-service:local
```

For Kubernetes, create a real secret from local environment values:

```bash
kubectl -n pharmacy create secret generic pharmacy-secrets \
  --from-literal=DB_PASSWORD=auth_password \
  --from-literal=AUTH_DB_PASSWORD=auth_password \
  --from-literal=PRODUCT_DB_PASSWORD=product_password \
  --from-file=private_key.pem=services/auth-service/src/test/resources/keys/private_key.pem \
  --from-file=public_key.pem=services/auth-service/src/test/resources/keys/public_key.pem
```

The checked-in key pair is for local learning only. Production must use a
separately generated, managed key pair. The default `runtime` auth image does
not include test keys; only the local Compose `local` build target includes
these fixtures.

Local dependency expectations for this isolated learning slice:

- MySQL runs on your machine at `host.docker.internal:3306`
- Kafka runs on your machine at `host.docker.internal:9092`
- Redis stays in cluster

These ports deliberately describe the original manually provisioned exercise,
not the canonical project-scoped MySQL (`3308`) and Kafka (`29092` from pods)
used by the full Helm deployment.

Verify the slice with:

```bash
kubectl apply --dry-run=client -f infra/kubernetes/base/
kubectl describe pod -n pharmacy -l app=api-gateway
kubectl logs -n pharmacy deploy/api-gateway
kubectl get events -n pharmacy --sort-by=.lastTimestamp
kubectl rollout status deploy/api-gateway -n pharmacy
```
