terraform {
  required_providers {
    helm = {
      source  = "hashicorp/helm"
      version = "~> 2.17"
    }
  }
}

# Deploys the Helm chart, pointed at the in-cluster backing services.
resource "helm_release" "app" {
  name      = "task-manager"
  namespace = var.namespace
  chart     = var.chart_path

  wait    = true
  timeout = 300

  set {
    name  = "image.repository"
    value = var.image_repository
  }
  set {
    name  = "image.tag"
    value = var.image_tag
  }
  set {
    name  = "image.pullPolicy"
    value = var.image_pull_policy
  }
  set {
    name  = "config.datasourceUrl"
    value = "jdbc:postgresql://${var.postgres_service}:5432/taskdb"
  }
  set {
    name  = "config.redisHost"
    value = var.redis_service
  }
  set {
    name  = "config.kafkaBootstrapServers"
    value = var.kafka_bootstrap
  }
  set {
    name  = "config.keycloakBaseUrl"
    value = var.keycloak_base_url
  }
  set_sensitive {
    name  = "secrets.openaiApiKey"
    value = var.openai_api_key
  }
}
