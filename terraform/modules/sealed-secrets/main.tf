terraform {
  required_providers {
    helm = {
      source  = "hashicorp/helm"
      version = "~> 2.17"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.35"
    }
  }
}

# Seed a fixed sealing keypair so the controller adopts it instead of generating a fresh one per
# cluster — committed SealedSecrets then decrypt across recreates. Skipped when the keypair is absent
# (controller self-generates). Private key gitignored; public cert committed for kubeseal.
resource "kubernetes_secret" "sealing_key" {
  count = var.sealing_cert != "" && var.sealing_key != "" ? 1 : 0

  metadata {
    name      = "sealed-secrets-key-bootstrap"
    namespace = var.namespace
    labels = {
      "sealedsecrets.bitnami.com/sealed-secrets-key" = "active"
    }
  }
  type = "kubernetes.io/tls"
  data = {
    "tls.crt" = var.sealing_cert
    "tls.key" = var.sealing_key
  }
}

# Sealed Secrets controller: decrypts committed SealedSecret CRs into real Secrets. Named
# `sealed-secrets-controller` in kube-system so kubeseal reaches it with no extra flags.
resource "helm_release" "sealed_secrets" {
  name      = "sealed-secrets"
  namespace = var.namespace
  # OCI reference: Bitnami's HTTP chart index (charts.bitnami.com) is now gated, so pull the chart
  # from the OCI registry instead.
  chart   = "oci://registry-1.docker.io/bitnamicharts/sealed-secrets"
  version = var.chart_version

  wait    = true
  timeout = 300

  set {
    name  = "fullnameOverride"
    value = "sealed-secrets-controller"
  }

  depends_on = [kubernetes_secret.sealing_key]
}
