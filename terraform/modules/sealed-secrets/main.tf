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

# Seed a fixed sealing keypair so the controller adopts it instead of generating a fresh one per
# cluster — committed SealedSecrets then decrypt across recreates. Skipped when the keypair is absent
# (controller self-generates). Private key gitignored; public cert committed for kubeseal.
#
# Threat #43: a kubernetes_secret resource would write the plaintext key into terraform.tfstate.
# local-exec + the key's file path keeps it out of state -- only a one-way filesha256 trigger lands there.
resource "null_resource" "sealing_key" {
  count = fileexists(var.sealing_cert_path) && fileexists(var.sealing_key_path) ? 1 : 0

  triggers = {
    cert_hash = filesha256(var.sealing_cert_path)
    key_hash  = filesha256(var.sealing_key_path)
    namespace = var.namespace
    # A recreated cluster gets a new API endpoint, so it's re-seeded rather than trusting old state.
    cluster = var.cluster_endpoint
  }

  # Point kubectl at the kind cluster, not the default context; taking the kubeconfig from the
  # cluster module also makes this wait for the cluster to exist.
  provisioner "local-exec" {
    interpreter = ["/bin/bash", "-c"]
    environment = {
      KUBECONFIG = var.kubeconfig_path
    }
    command = <<-EOT
      set -euo pipefail
      kubectl create secret tls sealed-secrets-key-bootstrap \
        --cert="${var.sealing_cert_path}" --key="${var.sealing_key_path}" \
        -n "${var.namespace}" --dry-run=client -o yaml \
        | kubectl label --local -f - "sealedsecrets.bitnami.com/sealed-secrets-key=active" -o yaml \
        | kubectl apply -f -
    EOT
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

  depends_on = [null_resource.sealing_key]
}
