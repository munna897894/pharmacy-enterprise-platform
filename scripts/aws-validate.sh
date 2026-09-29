#!/usr/bin/env bash
# Formats, validates and security-scans all Terraform under infra/terraform.
# Safe to run anytime — read-only / no AWS credentials required except for
# `terraform validate` on modules referencing data sources (none do at plan
# time without a configured backend, so this works offline too for fmt/lint).
set -euo pipefail

# Prefer a native-arch terraform binary if one was installed to ~/bin
# (Homebrew's Intel-only install run under Rosetta on Apple Silicon is
# dramatically slower for large providers like AWS's).
if [ -x "$HOME/bin/terraform" ]; then
  export PATH="$HOME/bin:$PATH"
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TF_DIR="$ROOT_DIR/infra/terraform"

echo "== terraform fmt -recursive -check =="
terraform -chdir="$TF_DIR" fmt -recursive -check -diff || {
  echo "Run 'terraform fmt -recursive' in $TF_DIR to fix formatting." >&2
  exit 1
}

for dir in "$TF_DIR/bootstrap" "$TF_DIR/envs/sandbox"; do
  echo "== terraform validate: $dir =="
  (cd "$dir" && terraform init -backend=false -input=false >/dev/null && terraform validate)
done

if command -v tfsec >/dev/null 2>&1; then
  echo "== tfsec (security scan) =="
  tfsec "$TF_DIR"
elif command -v checkov >/dev/null 2>&1; then
  echo "== checkov (security scan) =="
  checkov -d "$TF_DIR"
else
  echo "NOTE: neither tfsec nor checkov is installed. Install one for security scanning:"
  echo "  brew install tfsec"
  echo "  (or) brew install checkov"
fi

echo "All checks passed."
