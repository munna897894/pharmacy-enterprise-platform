terraform {
  required_version = ">= 1.9, < 2.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.70"
    }
  }

  # Intentionally local state for the bootstrap config: this creates the very
  # bucket that the main environment will use as its remote backend, so it
  # cannot itself depend on that backend (chicken-and-egg). Run this once,
  # keep the local terraform.tfstate file safe, and do not re-run casually.
}
