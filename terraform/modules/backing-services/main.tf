terraform {
  required_providers {
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.35"
    }
  }
}

locals {
  postgres = { app = "postgres" }
  redis    = { app = "redis" }
  kafka    = { app = "kafka" }
  keycloak = { app = "keycloak" }
}

# ---------------- Postgres (pgvector: the vector extension the V6 migration needs) ----------------
resource "kubernetes_deployment" "postgres" {
  metadata {
    name      = "postgres"
    namespace = var.namespace
    labels    = local.postgres
  }
  spec {
    replicas = 1
    selector { match_labels = local.postgres }
    template {
      metadata { labels = local.postgres }
      spec {
        container {
          name  = "postgres"
          image = var.postgres_image
          env {
            name  = "POSTGRES_DB"
            value = var.postgres_db
          }
          env {
            name  = "POSTGRES_USER"
            value = var.postgres_user
          }
          env {
            name  = "POSTGRES_PASSWORD"
            value = var.postgres_password
          }
          port { container_port = 5432 }
          readiness_probe {
            exec { command = ["pg_isready", "-U", var.postgres_user] }
            initial_delay_seconds = 5
            period_seconds        = 5
          }
        }
      }
    }
  }
}

resource "kubernetes_service" "postgres" {
  metadata {
    name      = "postgres"
    namespace = var.namespace
  }
  spec {
    selector = local.postgres
    port {
      port        = 5432
      target_port = 5432
    }
  }
}

# ---------------- Redis (distributed rate limiting + cache) ----------------
resource "kubernetes_deployment" "redis" {
  metadata {
    name      = "redis"
    namespace = var.namespace
    labels    = local.redis
  }
  spec {
    replicas = 1
    selector { match_labels = local.redis }
    template {
      metadata { labels = local.redis }
      spec {
        container {
          name  = "redis"
          image = var.redis_image
          port { container_port = 6379 }
          readiness_probe {
            exec { command = ["redis-cli", "ping"] }
            initial_delay_seconds = 5
            period_seconds        = 5
          }
        }
      }
    }
  }
}

resource "kubernetes_service" "redis" {
  metadata {
    name      = "redis"
    namespace = var.namespace
  }
  spec {
    selector = local.redis
    port {
      port        = 6379
      target_port = 6379
    }
  }
}

# ---------------- Kafka (single-node KRaft) ----------------
# Advertised as the in-cluster service DNS ("kafka:9092"), so pods reach it correctly — unlike the
# docker-compose broker that advertises localhost.
resource "kubernetes_deployment" "kafka" {
  metadata {
    name      = "kafka"
    namespace = var.namespace
    labels    = local.kafka
  }
  spec {
    replicas = 1
    selector { match_labels = local.kafka }
    template {
      metadata { labels = local.kafka }
      spec {
        container {
          name  = "kafka"
          image = var.kafka_image
          port { container_port = 9092 }
          env {
            name  = "KAFKA_NODE_ID"
            value = "1"
          }
          env {
            name  = "KAFKA_PROCESS_ROLES"
            value = "broker,controller"
          }
          env {
            name  = "KAFKA_LISTENERS"
            value = "PLAINTEXT://:9092,CONTROLLER://:9093"
          }
          env {
            name  = "KAFKA_ADVERTISED_LISTENERS"
            value = "PLAINTEXT://kafka:9092"
          }
          env {
            name  = "KAFKA_CONTROLLER_QUORUM_VOTERS"
            value = "1@localhost:9093"
          }
          env {
            name  = "KAFKA_CONTROLLER_LISTENER_NAMES"
            value = "CONTROLLER"
          }
          env {
            name  = "KAFKA_LISTENER_SECURITY_PROTOCOL_MAP"
            value = "CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT"
          }
          env {
            name  = "KAFKA_INTER_BROKER_LISTENER_NAME"
            value = "PLAINTEXT"
          }
          env {
            name  = "KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR"
            value = "1"
          }
          env {
            name  = "KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR"
            value = "1"
          }
          env {
            name  = "KAFKA_TRANSACTION_STATE_LOG_MIN_ISR"
            value = "1"
          }
          env {
            name  = "KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS"
            value = "0"
          }
        }
      }
    }
  }
}

resource "kubernetes_service" "kafka" {
  metadata {
    name      = "kafka"
    namespace = var.namespace
  }
  spec {
    selector = local.kafka
    port {
      port        = 9092
      target_port = 9092
    }
  }
}

# ---------------- Keycloak (imports the myrealm export the app authenticates against) ----------------
resource "kubernetes_config_map" "keycloak_realm" {
  metadata {
    name      = "keycloak-realm"
    namespace = var.namespace
  }
  data = {
    "realm-export.json" = file(var.realm_export_path)
  }
}

resource "kubernetes_deployment" "keycloak" {
  metadata {
    name      = "keycloak"
    namespace = var.namespace
    labels    = local.keycloak
  }
  spec {
    replicas = 1
    selector { match_labels = local.keycloak }
    template {
      metadata { labels = local.keycloak }
      spec {
        container {
          name  = "keycloak"
          image = var.keycloak_image
          args  = ["start-dev", "--import-realm"]
          env {
            name  = "KEYCLOAK_ADMIN"
            value = "admin"
          }
          env {
            name  = "KEYCLOAK_ADMIN_PASSWORD"
            value = "admin"
          }
          port { container_port = 8080 }
          volume_mount {
            name       = "realm"
            mount_path = "/opt/keycloak/data/import"
            read_only  = true
          }
          readiness_probe {
            http_get {
              path = "/realms/myrealm/.well-known/openid-configuration"
              port = 8080
            }
            initial_delay_seconds = 20
            period_seconds        = 10
            failure_threshold     = 30
          }
        }
        volume {
          name = "realm"
          config_map {
            name = kubernetes_config_map.keycloak_realm.metadata[0].name
          }
        }
      }
    }
  }
}

resource "kubernetes_service" "keycloak" {
  metadata {
    name      = "keycloak"
    namespace = var.namespace
  }
  spec {
    selector = local.keycloak
    port {
      port        = 8080
      target_port = 8080
    }
  }
}
