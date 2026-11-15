terraform {
  required_providers {
    helm = {
      source  = "hashicorp/helm"
      version = "~> 2.17"
    }
    null = {
      source  = "hashicorp/null"
      version = "~> 3.2"
    }
  }
}

# Installs Argo CD into the cluster. Terraform bootstraps the GitOps controller (push); from here
# the application itself is delivered by Argo CD syncing this repo (pull) — see the argocd/ manifests.
resource "helm_release" "argocd" {
  name             = "argocd"
  namespace        = var.namespace
  create_namespace = true
  repository       = "https://argoproj.github.io/argo-helm"
  chart            = "argo-cd"
  version          = var.chart_version

  wait    = true
  timeout = 600

  # Local dev: serve the API/UI over plain HTTP so a `kubectl port-forward` reaches it without TLS
  # (kind has no ingress or load balancer in front of the server).
  set {
    name  = "configs.params.server\\.insecure"
    value = "true"
  }
}

# Threat #49: delete the initial admin secret right after install so the one-time bootstrap
# password can't linger as a standing credential.
resource "null_resource" "delete_initial_admin_secret" {
  triggers = {
    argocd_release = helm_release.argocd.id
  }

  provisioner "local-exec" {
    command = "kubectl delete secret argocd-initial-admin-secret -n ${var.namespace} --ignore-not-found"
  }
}
