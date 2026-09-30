#!/usr/bin/env bash

aws_sandbox_guard() {
  : "${AWS_PROFILE:=pharmacy-sandbox}"
  : "${AWS_REGION:=us-east-1}"
  : "${EXPECTED_AWS_ACCOUNT_ID:?Set EXPECTED_AWS_ACCOUNT_ID to the independently verified 12-digit sandbox account ID.}"
  : "${NAME_PREFIX:=pharmacy-sbx}"

  if [[ ! "$EXPECTED_AWS_ACCOUNT_ID" =~ ^[0-9]{12}$ ]]; then
    echo "EXPECTED_AWS_ACCOUNT_ID must contain exactly 12 digits." >&2
    return 1
  fi
  if [[ "$AWS_REGION" != "us-east-1" ]]; then
    echo "This temporary environment is restricted to us-east-1." >&2
    return 1
  fi
  if [[ ! "$NAME_PREFIX" =~ ^[a-zA-Z0-9][a-zA-Z0-9-]{2,25}$ ]]; then
    echo "NAME_PREFIX must be 3-26 alphanumeric/hyphen characters and start with a letter or digit." >&2
    return 1
  fi

  export AWS_PROFILE AWS_REGION NAME_PREFIX
  local identity account
  identity=$(aws sts get-caller-identity --profile "$AWS_PROFILE" --output json)
  account=$(python3 -c 'import json,sys; print(json.load(sys.stdin)["Account"])' <<<"$identity")
  if [[ "$account" != "$EXPECTED_AWS_ACCOUNT_ID" ]]; then
    echo "Refusing AWS operation: active profile account does not match EXPECTED_AWS_ACCOUNT_ID." >&2
    return 1
  fi
  printf 'Verified AWS account %s in %s using profile %s.\n' "$account" "$AWS_REGION" "$AWS_PROFILE"
}

terraform_command() {
  if [[ -x "$HOME/bin/terraform" ]]; then
    export PATH="$HOME/bin:$PATH"
  fi
}

require_default_workspace() {
  local workspace
  workspace=$(terraform workspace show)
  if [[ "$workspace" != "default" ]]; then
    echo "Refusing operation in unexpected Terraform workspace '$workspace' (expected 'default')." >&2
    return 1
  fi
}

# Reads one scalar from the allowlist-filtered Terraform outputs file. Missing
# keys fail closed rather than yielding an empty string.
output_value() {
  python3 - "$1" "$2" <<'PY'
import json
import pathlib
import sys
outputs = json.loads(pathlib.Path(sys.argv[1]).read_text())
if sys.argv[2] not in outputs:
    raise SystemExit(f"Terraform output {sys.argv[2]} is missing.")
print(outputs[sys.argv[2]]["value"])
PY
}

select_sandbox_kube_context() {
  local cluster_name="$1"
  local expected_context="pharmacy-sandbox-${cluster_name}"
  aws eks update-kubeconfig \
    --name "$cluster_name" \
    --region "$AWS_REGION" \
    --profile "$AWS_PROFILE" \
    --alias "$expected_context" >/dev/null
  local current_context
  current_context=$(kubectl config current-context)
  if [[ "$current_context" != "$expected_context" ]]; then
    echo "Refusing Kubernetes operation: current context '$current_context' is not '$expected_context'." >&2
    return 1
  fi
  export AWS_KUBE_CONTEXT="$expected_context"
}

# Fails closed when a pushed image does not match the worker node architecture.
# Without this an arm64 image on x86_64 nodes (or the reverse) only surfaces as
# a CrashLoopBackOff with "exec format error" after the whole rollout.
verify_image_architecture() {
  local image_ref="$1"
  local expected_arch="$2"
  local actual_arch

  actual_arch=$(docker buildx imagetools inspect "$image_ref" --raw 2>/dev/null \
    | python3 -c '
import json
import sys

try:
    doc = json.load(sys.stdin)
except ValueError:
    sys.exit(0)

# Either an image index (multi-arch) or a single manifest.
manifests = doc.get("manifests")
if manifests:
    arches = sorted(
        {
            m["platform"]["architecture"]
            for m in manifests
            if m.get("platform", {}).get("os") not in (None, "unknown")
            and m.get("platform", {}).get("architecture") != "unknown"
        }
    )
    print(",".join(arches))
else:
    print(doc.get("architecture", ""))
')

  if [[ -z "$actual_arch" ]]; then
    echo "Refusing to continue: could not determine the architecture of $image_ref." >&2
    return 1
  fi

  if [[ "$actual_arch" != "$expected_arch" ]]; then
    echo "Refusing to continue: $image_ref is '$actual_arch' but the EKS nodes are '$expected_arch'." >&2
    echo "Pods would crash-loop with 'exec format error'. Rebuild with --platform linux/$expected_arch." >&2
    return 1
  fi
}

# Confirms the live nodes match the architecture the images were built for.
verify_node_architecture() {
  local expected_arch="$1"
  local actual
  actual=$(kubectl --context "$AWS_KUBE_CONTEXT" get nodes \
    -o jsonpath='{range .items[*]}{.status.nodeInfo.architecture}{"\n"}{end}' | sort -u)

  if [[ "$actual" != "$expected_arch" ]]; then
    echo "Refusing to deploy: EKS nodes report architecture '${actual//$'\n'/,}' but images target '$expected_arch'." >&2
    return 1
  fi
  echo "Node architecture matches the built images ($expected_arch)."
}

# Confirms exactly one default StorageClass exists and that it is backed by the
# EBS CSI driver. Two defaults make PVC binding non-deterministic; a default
# still pointing at the removed in-tree provisioner leaves PVCs Pending with no
# explanatory event, which is slow and confusing to diagnose mid-session.
verify_default_storage_class() {
  local defaults
  defaults=$(kubectl --context "$AWS_KUBE_CONTEXT" get storageclass \
    -o jsonpath='{range .items[?(@.metadata.annotations.storageclass\.kubernetes\.io/is-default-class=="true")]}{.metadata.name}{" "}{.provisioner}{"\n"}{end}')

  local count
  count=$(printf '%s' "$defaults" | grep -c . || true)
  if [[ "$count" -ne 1 ]]; then
    echo "Refusing to continue: expected exactly one default StorageClass, found $count." >&2
    printf '%s\n' "$defaults" >&2
    return 1
  fi

  if [[ "$defaults" != *"ebs.csi.aws.com"* ]]; then
    echo "Refusing to continue: the default StorageClass is not served by ebs.csi.aws.com." >&2
    echo "PersistentVolumeClaims would stay Pending. Found: $defaults" >&2
    return 1
  fi
  echo "Default StorageClass is gp3 via ebs.csi.aws.com."
}

# Deletes PVCs in the sandbox namespace so the CSI driver releases their EBS
# volumes while it still has credentials and a cluster to run on. Destroying
# the cluster first orphans those volumes: they keep billing, and nothing left
# in the account explains what they belonged to.
release_persistent_volumes() {
  local namespace="${1:-pharmacy}"
  local claims
  claims=$(kubectl --context "$AWS_KUBE_CONTEXT" get pvc -n "$namespace" \
    -o jsonpath='{range .items[*]}{.metadata.name}{"\n"}{end}' 2>/dev/null || true)

  if [[ -z "$claims" ]]; then
    echo "No PersistentVolumeClaims to release in namespace '$namespace'."
    return 0
  fi

  echo "Releasing PersistentVolumeClaims so their EBS volumes are deleted:"
  printf '%s\n' "$claims"
  # StatefulSet deletion must come first: the controller recreates a PVC that
  # disappears while its pod still exists.
  kubectl --context "$AWS_KUBE_CONTEXT" delete statefulset --all -n "$namespace" \
    --ignore-not-found --timeout=120s || true
  kubectl --context "$AWS_KUBE_CONTEXT" delete pvc --all -n "$namespace" \
    --ignore-not-found --timeout=180s || true

  # Give the driver a moment to action the deletes before the cluster goes away.
  local waited=0
  while kubectl --context "$AWS_KUBE_CONTEXT" get pv \
    -o jsonpath='{range .items[?(@.status.phase=="Released")]}{.metadata.name}{"\n"}{end}' 2>/dev/null | grep -q . ; do
    [[ "$waited" -ge 60 ]] && break
    sleep 5
    waited=$((waited + 5))
  done
}
