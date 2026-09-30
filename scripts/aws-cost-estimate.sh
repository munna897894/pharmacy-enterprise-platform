#!/usr/bin/env bash
# Offline, time-boxed cost estimate for the temporary full-fleet AWS sandbox.
# Makes no AWS API calls, reads no credentials and prints no account data.
# Rates are hard-coded us-east-1 on-demand list prices captured when this file
# was written; AWS pricing changes, so treat the output as a planning estimate
# and reconcile against Cost Explorer for actual charges.
set -euo pipefail

HOURS="${1:-4}"
GATEWAY_EXPOSURE="${GATEWAY_EXPOSURE:-port-forward}"
python3 - "$HOURS" "$GATEWAY_EXPOSURE" <<'PY'
import sys

try:
    hours = float(sys.argv[1])
except ValueError:
    raise SystemExit("usage: aws-cost-estimate.sh <hours-running>")
if hours <= 0:
    raise SystemExit("Hours must be greater than zero.")
exposure = sys.argv[2]
if exposure not in ("port-forward", "alb-https"):
    raise SystemExit("GATEWAY_EXPOSURE must be port-forward or alb-https.")

MONTH_HOURS = 730.0

# (label, hourly USD, note)
line_items = [
    ("EKS control plane", 0.10, "standard-support rate; extended support costs ~6x more per hour"),
    ("2 x t4g.large workers", 2 * 0.0672, "Graviton; sized for pod memory LIMITS, not requests"),
    ("RDS db.t4g.small (single-AZ)", 0.032, "ten schemas share one instance"),
    ("RDS 20 GiB gp3 storage", 20 * 0.115 / MONTH_HOURS, "autoscaling disabled; backups retained 0 days"),
    ("2 x 30 GiB gp3 node volumes", 60 * 0.08 / MONTH_HOURS, "room for images + bounded observability storage"),
    ("8 GiB gp3 Kafka PVC", 8 * 0.08 / MONTH_HOURS, "CSI-provisioned; deleted with the PVC on teardown"),
    ("Secrets Manager (13 secrets)", 13 * 0.40 / MONTH_HOURS, "prorated hourly; deleted immediately on destroy"),
    ("CloudWatch Logs (api+authenticator)", 0.0042, "~0.2 GiB/day ingest; enabling 'audit' raises this sharply"),
    ("ECR storage (~6 GiB of images)", 6 * 0.10 / MONTH_HOURS, "twelve images with lifecycle cleanup"),
    ("Public data transfer", 0.005, "rough allowance for pulls and gateway traffic"),
]

if exposure == "alb-https":
    line_items.append(("HTTPS Application Load Balancer", 0.0225 + 0.008, "alb-https opt-in only; hourly + ~1 LCU"))

hourly = sum(item[1] for item in line_items)

print("Temporary full-fleet AWS sandbox - estimated cost (NOT a spending cap)")
print(f"Gateway exposure: {exposure}" + ("" if exposure == "alb-https" else " (no load balancer is created)"))
print()
print(f"{'Component':38} {'USD/hour':>9}  Note")
print("-" * 100)
for label, rate, note in line_items:
    print(f"{label:38} {rate:9.4f}  {note}")
print("-" * 100)
print(f"{'Estimated total':38} {hourly:9.4f}  per hour while the environment exists")
print()
print(f"Estimated cost for the requested {hours:g}-hour session: ${hourly * hours:,.2f}")
print()
print(f"{'Time box':28} {'Estimated USD':>14}")
for label, span in (
    ("2-hour session", 2), ("4-hour session", 4), ("8-hour session", 8),
    ("left running 24 hours", 24), ("left running 7 days", 24 * 7), ("left running 30 days", 24 * 30),
):
    print(f"{label:28} {hourly * span:14,.2f}")
print()
print("Excluded: MSK (never created), NAT Gateway (none), ElastiCache and managed observability.")
print("Redis, Kafka and the private observability stack (Prometheus, Grafana, Loki, Tempo, OTel,")
print("Alloy) run in-cluster on the same two workers, so they consume node capacity rather than")
print("adding line items; their node-local storage is inside the 30 GiB node volumes above.")
print()
print("EKS extended support is NOT included above and must stay excluded: the Kubernetes")
print("version is pinned to a standard-support release and the cluster sets")
print("upgrade_policy.support_type = STANDARD, so it cannot roll into the higher")
print("extended-support per-cluster-hour rate. scripts/aws-check-eks-version.sh re-verifies")
print("this against the EKS API before every plan.")
print()
print("IMPORTANT: AWS Budgets send alerts only. They DO NOT stop provisioning, cap usage or")
print("terminate resources, and alert evaluation commonly lags actual spend by several hours.")
print("A $20 budget will not prevent this environment from exceeding $20 if it is left running:")
print(f"the estimate above crosses $20 after roughly {20 / hourly:,.0f} hours.")
print("The only reliable control is destroying the environment: scripts/aws-destroy.sh followed")
print("by scripts/aws-post-destroy-check.sh.")
PY
