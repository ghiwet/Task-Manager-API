# Provisions a local kind cluster, then deploys the backing services (and, later, the app) into it.
provider "kind" {}

module "cluster" {
  source       = "./modules/cluster"
  cluster_name = var.cluster_name
}

# The kubernetes/helm providers talk to the kind cluster via the kubeconfig it writes.
provider "kubernetes" {
  config_path = module.cluster.kubeconfig_path
}

provider "helm" {
  kubernetes {
    config_path = module.cluster.kubeconfig_path
  }
}

provider "kubectl" {
  config_path      = module.cluster.kubeconfig_path
  load_config_file = true
}

# GitOps controller. Terraform installs Argo CD (push); Argo CD then delivers the app by syncing
# this repo (pull) — see modules/argocd and the argocd/ manifests.
module "argocd" {
  source = "./modules/argocd"
}

# Secrets management: install the Sealed Secrets controller and seed it a fixed keypair so committed
# SealedSecrets survive cluster recreates (private key gitignored, public cert committed).
module "sealed_secrets" {
  source       = "./modules/sealed-secrets"
  sealing_cert = fileexists(local.sealing_cert_path) ? file(local.sealing_cert_path) : ""
  sealing_key  = fileexists(local.sealing_key_path) ? file(local.sealing_key_path) : ""
}

locals {
  sealing_cert_path = "${path.root}/../sealed-secrets/tls.crt"
  sealing_key_path  = "${path.root}/../sealed-secrets/tls.key"
}

resource "kubernetes_namespace" "app" {
  metadata {
    name = var.namespace
  }
}

module "backing_services" {
  source            = "./modules/backing-services"
  namespace         = kubernetes_namespace.app.metadata[0].name
  realm_export_path = "${path.root}/../keycloak/realm-export.json"
}

# Bootstrap the Argo CD Application (a CRD instance) — a multi-source app that syncs both the Helm
# chart and the SealedSecret. Applied with the kubectl provider because the kubernetes provider's
# manifest resource needs the CRD at plan time, which a fresh apply lacks. From here Argo CD owns
# the app, syncing the chart + SealedSecret from Git.
resource "kubectl_manifest" "task_manager_app" {
  depends_on = [module.argocd, module.sealed_secrets, module.backing_services]
  yaml_body  = file("${path.root}/../argocd/applications/task-manager.yaml")
}
