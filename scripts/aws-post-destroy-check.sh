#!/usr/bin/env bash
# Mandatory post-destroy leftover-resource inventory check, per the hard
# safety rule: "Check EKS, EC2, ELB, RDS, NAT, EIP and ECR resources after
# destroy." Also mirrors the exact command block committed in
# docs/aws-plan-review.md section 10 — keep both in sync if edited.
set -euo pipefail

: "${AWS_PROFILE:=pharmacy-sandbox}"
: "${AWS_REGION:=us-east-1}"

export AWS_PROFILE AWS_REGION

echo "--- Caller identity (confirm this is the sandbox account) ---"
aws sts get-caller-identity

echo
echo "--- EKS clusters ---"
aws eks list-clusters --region "$AWS_REGION"

echo
echo "--- EC2 instances (non-terminated) ---"
aws ec2 describe-instances --region "$AWS_REGION" \
  --filters "Name=instance-state-name,Values=pending,running,stopping,stopped" \
  --query 'Reservations[].Instances[].[InstanceId,State.Name,Tags]'

echo
echo "--- Load balancers (ALB/ELB) ---"
aws elbv2 describe-load-balancers --region "$AWS_REGION" \
  --query 'LoadBalancers[].[LoadBalancerArn,DNSName]'

echo
echo "--- RDS instances ---"
aws rds describe-db-instances --region "$AWS_REGION" \
  --query 'DBInstances[].[DBInstanceIdentifier,DBInstanceStatus]'

echo
echo "--- NAT Gateways (should always be empty for this exercise) ---"
aws ec2 describe-nat-gateways --region "$AWS_REGION" \
  --filter "Name=state,Values=pending,available,deleting" \
  --query 'NatGateways[].[NatGatewayId,State]'

echo
echo "--- Unattached Elastic IPs (classic leftover-cost trap) ---"
aws ec2 describe-addresses --region "$AWS_REGION" --query 'Addresses[?AssociationId==`null`]'

echo
echo "--- ECR repositories (informational — low/no cost, not auto-deleted by terraform destroy of EKS/RDS) ---"
aws ecr describe-repositories --region "$AWS_REGION" --query 'repositories[].repositoryName' || true

echo
echo "--- Secrets Manager secrets (should be gone — recovery_window_in_days=0) ---"
aws secretsmanager list-secrets --region "$AWS_REGION" --query 'SecretList[].Name'

echo
echo "--- Current budget spend ---"
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
aws budgets describe-budgets --account-id "$ACCOUNT_ID" \
  --query 'Budgets[].[BudgetName,CalculatedSpend.ActualSpend]'

echo
echo "=================================================================="
echo "If EKS/EC2/ELB/RDS/NAT/EIP show ANY results above, they are LEFTOVER"
echo "billable resources and must be manually investigated/deleted before"
echo "this exercise is considered closed. ECR repos are expected to remain"
echo "(low/no cost) unless you explicitly want to delete them too:"
echo "  aws ecr delete-repository --repository-name <name> --force --region $AWS_REGION"
echo "=================================================================="
