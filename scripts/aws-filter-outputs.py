#!/usr/bin/env python3
"""Filter `terraform output -json` down to a non-sensitive allowlist.

Reads the full output JSON on stdin and writes only the keys the deployment
renderer needs. Aborts if any allowlisted key is marked sensitive, so a future
sensitive output can never reach disk or a rendered manifest. Nothing is
printed to stderr except the offending key names; no values are ever echoed.
"""
import json
import sys

# Identifiers, endpoints, ARNs and tags only. Deliberately excludes every
# credential: database passwords, the RDS master password and JWT key material
# stay in Secrets Manager and Terraform state and are read at runtime by
# IRSA-scoped pods.
ALLOWED_OUTPUTS = (
    "ecr_repository_urls",
    "eks_cluster_name",
    "node_architecture",
    "rds_endpoint",
    "rds_port",
    "database_secret_arns",
    "rds_master_secret_arn",
    "rds_master_username",
    "jwt_keypair_secret_arn",
    "jwt_public_key_secret_arn",
    "workload_role_arns",
    "db_bootstrap_role_arn",
    "alb_controller_role_arn",
    "alb_security_group_id",
    "gateway_exposure",
    "gateway_hostname",
    "gateway_certificate_arn",
    "vpc_id",
    "resource_tags",
)


def main() -> int:
    try:
        raw = json.load(sys.stdin)
    except json.JSONDecodeError:
        print("Could not parse Terraform output JSON.", file=sys.stderr)
        return 1

    filtered = {}
    sensitive = []
    missing = []
    for name in ALLOWED_OUTPUTS:
        if name not in raw:
            missing.append(name)
            continue
        entry = raw[name]
        if entry.get("sensitive"):
            sensitive.append(name)
            continue
        filtered[name] = entry

    if sensitive:
        print(
            "Refusing to render: these allowlisted outputs are marked sensitive: "
            + ", ".join(sorted(sensitive)),
            file=sys.stderr,
        )
        return 1
    if missing:
        print(
            "Terraform outputs are missing required keys: " + ", ".join(sorted(missing)),
            file=sys.stderr,
        )
        return 1

    json.dump(filtered, sys.stdout)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
