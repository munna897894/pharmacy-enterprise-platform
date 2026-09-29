provider "aws" {
  region  = var.aws_region
  profile = var.aws_profile

  default_tags {
    tags = local.common_tags
  }
}

# Kubernetes/Helm providers talk to the cluster this same apply creates. On a
# completely fresh `apply` the cluster doesn't exist yet on the first plan —
# these providers only need valid config once the eks module's resources are
# actually being created/read, which Terraform handles via the module output
# references below (implicit dependency ordering).
provider "kubernetes" {
  host                   = module.eks.cluster_endpoint
  cluster_ca_certificate = base64decode(module.eks.cluster_certificate_authority_data)

  exec {
    api_version = "client.authentication.k8s.io/v1beta1"
    command     = "aws"
    args = [
      "eks", "get-token",
      "--cluster-name", module.eks.cluster_name,
      "--region", var.aws_region,
      "--profile", var.aws_profile,
    ]
  }
}

provider "helm" {
  kubernetes {
    host                   = module.eks.cluster_endpoint
    cluster_ca_certificate = base64decode(module.eks.cluster_certificate_authority_data)

    exec {
      api_version = "client.authentication.k8s.io/v1beta1"
      command     = "aws"
      args = [
        "eks", "get-token",
        "--cluster-name", module.eks.cluster_name,
        "--region", var.aws_region,
        "--profile", var.aws_profile,
      ]
    }
  }
}
