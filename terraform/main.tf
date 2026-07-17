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

# Bootstrap the Argo CD Application (a CRD instance). Applied with the kubectl provider because the
# kubernetes provider's manifest resource requires the CRD to exist at plan time, which it does not
# on a fresh apply. After this, Argo CD owns the app's lifecycle by syncing the chart from Git.
resource "kubectl_manifest" "task_manager_app" {
  depends_on = [module.argocd, module.backing_services]
  yaml_body  = file("${path.root}/../argocd/applications/task-manager.yaml")
}
