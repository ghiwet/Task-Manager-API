terraform {
  required_providers {
    helm = {
      source  = "hashicorp/helm"
      version = "~> 2.17"
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
