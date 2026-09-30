#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$ROOT_DIR/scripts/aws-common.sh"
aws_sandbox_guard

LEFTOVERS=0
check_empty() {
  local label="$1"
  local result="$2"
  if [[ -n "$result" && "$result" != "None" ]]; then
    printf 'LEFTOVER %s:\n%s\n' "$label" "$result"
    LEFTOVERS=1
  else
    printf 'No sandbox %s remain.\n' "$label"
  fi
}

echo "== Checking target resources in the verified sandbox account =="
CLUSTERS=$(aws eks list-clusters --region "$AWS_REGION" --output json \
  | python3 -c "import json,sys; print('\\n'.join(x for x in json.load(sys.stdin)['clusters'] if x == '${NAME_PREFIX}'))")
check_empty EKS-cluster "$CLUSTERS"

INSTANCES=$(aws ec2 describe-instances --region "$AWS_REGION" \
  --filters "Name=instance-state-name,Values=pending,running,stopping,stopped" \
  --output json | python3 -c "
import json,sys
prefix='${NAME_PREFIX}'
matches=[]
for reservation in json.load(sys.stdin)['Reservations']:
  for instance in reservation['Instances']:
    tags={item['Key']:item['Value'] for item in instance.get('Tags', [])}
    if tags.get('eks:cluster-name') == prefix or tags.get('kubernetes.io/cluster/'+prefix) in ('owned','shared') or tags.get('Name','').startswith(prefix):
      matches.append(instance['InstanceId'])
print('\\n'.join(matches))
")
check_empty EKS-worker-instances "$INSTANCES"

DBS=$(aws rds describe-db-instances --region "$AWS_REGION" \
  --query "DBInstances[?DBInstanceIdentifier=='${NAME_PREFIX}-mysql'].DBInstanceIdentifier" \
  --output text)
check_empty RDS-instance "$DBS"

SNAPSHOTS=$(aws rds describe-db-snapshots --region "$AWS_REGION" --output json \
  | python3 -c "import json,sys; n='${NAME_PREFIX}-mysql'; print('\\n'.join(x['DBSnapshotIdentifier'] for x in json.load(sys.stdin)['DBSnapshots'] if x.get('DBInstanceIdentifier') == n))")
check_empty RDS-snapshot "$SNAPSHOTS"

LOAD_BALANCERS=$(python3 - "$AWS_REGION" "$AWS_PROFILE" "$NAME_PREFIX" <<'PY'
import json
import subprocess
import sys

region, profile, prefix = sys.argv[1:]
resources = json.loads(subprocess.check_output([
    "aws", "elbv2", "describe-load-balancers", "--region", region,
    "--profile", profile, "--output", "json",
], text=True))["LoadBalancers"]
arns = [item["LoadBalancerArn"] for item in resources]
found = []
for offset in range(0, len(arns), 20):
    tagged = json.loads(subprocess.check_output([
        "aws", "elbv2", "describe-tags", "--region", region, "--profile", profile,
        "--resource-arns", *arns[offset:offset + 20], "--output", "json",
    ], text=True))["TagDescriptions"]
    for item in tagged:
        tags = {tag["Key"]: tag["Value"] for tag in item["Tags"]}
        if tags.get("elbv2.k8s.aws/cluster") == prefix:
            found.append(item["ResourceArn"])
print("\n".join(found))
PY
)
check_empty gateway-load-balancer "$LOAD_BALANCERS"

TARGET_GROUPS=$(python3 - "$AWS_REGION" "$AWS_PROFILE" "$NAME_PREFIX" <<'PY'
import json
import subprocess
import sys

region, profile, prefix = sys.argv[1:]
resources = json.loads(subprocess.check_output([
    "aws", "elbv2", "describe-target-groups", "--region", region,
    "--profile", profile, "--output", "json",
], text=True))["TargetGroups"]
arns = [item["TargetGroupArn"] for item in resources]
found = []
for offset in range(0, len(arns), 20):
    tagged = json.loads(subprocess.check_output([
        "aws", "elbv2", "describe-tags", "--region", region, "--profile", profile,
        "--resource-arns", *arns[offset:offset + 20], "--output", "json",
    ], text=True))["TagDescriptions"]
    for item in tagged:
        tags = {tag["Key"]: tag["Value"] for tag in item["Tags"]}
        if tags.get("elbv2.k8s.aws/cluster") == prefix:
            found.append(item["ResourceArn"])
print("\n".join(found))
PY
)
check_empty gateway-target-group "$TARGET_GROUPS"

NAT_GATEWAYS=$(aws ec2 describe-nat-gateways --region "$AWS_REGION" \
  --filter "Name=tag:Name,Values=${NAME_PREFIX}-*" \
           "Name=state,Values=pending,available,deleting" \
  --query 'NatGateways[].NatGatewayId' --output text)
check_empty NAT-gateway "$NAT_GATEWAYS"

ADDRESSES=$(aws ec2 describe-addresses --region "$AWS_REGION" \
  --filters "Name=tag:Name,Values=${NAME_PREFIX}-*" \
  --query 'Addresses[].AllocationId' --output text)
check_empty Elastic-IP "$ADDRESSES"

# Dynamically provisioned EBS volumes are created by the CSI driver, not by
# Terraform, so `terraform destroy` never sees them. If the cluster is torn down
# before its PVCs are released the volumes survive, keep billing hourly, and
# carry only Kubernetes tags that make them easy to overlook.
CSI_VOLUMES=$(aws ec2 describe-volumes --region "$AWS_REGION" \
  --filters "Name=tag-key,Values=kubernetes.io/created-for/pvc/name" \
            "Name=status,Values=creating,available,in-use" \
  --query "Volumes[?Tags[?Key=='kubernetes.io/cluster/${NAME_PREFIX}']].VolumeId" \
  --output text)
check_empty CSI-provisioned-EBS-volume "$CSI_VOLUMES"

# Snapshots are not created by this environment, but a leftover one would keep
# billing after every other trace of the cluster is gone.
CSI_SNAPSHOTS=$(aws ec2 describe-snapshots --region "$AWS_REGION" --owner-ids self \
  --filters "Name=tag-key,Values=CSIVolumeSnapshotName" \
  --query 'Snapshots[].SnapshotId' --output text)
check_empty CSI-EBS-snapshot "$CSI_SNAPSHOTS"

VPCS=$(aws ec2 describe-vpcs --region "$AWS_REGION" \
  --filters "Name=tag:Name,Values=${NAME_PREFIX}-vpc" \
  --query 'Vpcs[].VpcId' --output text)
check_empty sandbox-VPC "$VPCS"

REPOSITORIES=$(aws ecr describe-repositories --region "$AWS_REGION" \
  --query "repositories[?starts_with(repositoryName, '${NAME_PREFIX}-')].repositoryName" \
  --output text)
check_empty ECR-repository "$REPOSITORIES"

SECRETS=$(aws secretsmanager list-secrets --region "$AWS_REGION" \
  --query "SecretList[?starts_with(Name, '${NAME_PREFIX}/')].Name" --output text)
check_empty Secrets-Manager-secret "$SECRETS"

LOG_GROUPS=$(aws logs describe-log-groups --region "$AWS_REGION" \
  --log-group-name-prefix "/aws/eks/${NAME_PREFIX}/cluster" \
  --query "logGroups[?logGroupName=='/aws/eks/${NAME_PREFIX}/cluster'].logGroupName" --output text)
check_empty EKS-control-plane-log-group "$LOG_GROUPS"

if [[ "$LEFTOVERS" -ne 0 ]]; then
  echo "Sandbox resources remain. Investigate and remove them before considering the session closed." >&2
  exit 1
fi

echo "No targeted billable AWS resources remain."
