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

module "app" {
  source = "./modules/app"
  # Wait for the backing services' pods to roll out, not just their Services to exist,
  # so the app doesn't start before Postgres/Kafka/Keycloak are ready on a fresh apply.
  depends_on = [module.backing_services]

  namespace          = kubernetes_namespace.app.metadata[0].name
  chart_path         = "${path.root}/../helm/task-manager"
  image_repository   = var.image_repository
  image_tag          = var.image_tag
  image_pull_policy  = var.image_pull_policy
  openai_api_key     = var.openai_api_key
  postgres_service   = module.backing_services.postgres_service
  redis_service      = module.backing_services.redis_service
  kafka_bootstrap    = module.backing_services.kafka_bootstrap
  keycloak_base_url  = module.backing_services.keycloak_base_url
  elasticsearch_uris = module.backing_services.elasticsearch_uris
}
