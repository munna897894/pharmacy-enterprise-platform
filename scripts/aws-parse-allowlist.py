#!/usr/bin/env python3
"""Validate the fail-closed gateway ALB allowlist and emit it as Terraform JSON.

Rejects empty input, non-IPv4 entries, anything wider than /32 and wildcard
addresses so the sandbox gateway can never be exposed to the open internet.
Prints only the validated CIDR list; no credentials or request data.
"""
import ipaddress
import json
import sys


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: aws-parse-allowlist.py <comma-separated /32 list>", file=sys.stderr)
        return 2
    entries = [item.strip() for item in sys.argv[1].split(",") if item.strip()]
    if not entries:
        print("ALB_INGRESS_CIDRS must contain at least one /32 address.", file=sys.stderr)
        return 1
    for entry in entries:
        try:
            network = ipaddress.ip_network(entry, strict=True)
        except ValueError:
            print("ALB_INGRESS_CIDRS entries must be valid IPv4 /32 CIDRs.", file=sys.stderr)
            return 1
        if network.version != 4 or network.prefixlen != 32:
            print("Each ALB_INGRESS_CIDRS entry must be a single IPv4 /32 address.", file=sys.stderr)
            return 1
        if network.network_address.is_unspecified or network.network_address.is_multicast:
            print("ALB_INGRESS_CIDRS must not contain wildcard or multicast addresses.", file=sys.stderr)
            return 1
    print(json.dumps(entries))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
