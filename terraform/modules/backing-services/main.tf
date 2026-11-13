terraform {
  required_providers {
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.35"
    }
  }
}

locals {
  postgres      = { app = "postgres" }
  redis         = { app = "redis" }
  kafka         = { app = "kafka" }
  keycloak      = { app = "keycloak" }
  elasticsearch = { app = "elasticsearch" }

  kafka_jaas_conf = <<-EOT
    KafkaServer {
      org.apache.kafka.common.security.scram.ScramLoginModule required
      username="inter-broker"
      password="inter-broker-secret";
    };
  EOT

  # Shared with the init container below so its one-time format step generates the same
  # server.properties the main container would (see docker-compose.yml for the matching setup).
  kafka_common_env = {
    KAFKA_NODE_ID                                   = "1"
    KAFKA_PROCESS_ROLES                             = "broker,controller"
    KAFKA_LISTENERS                                 = "SASL://:9092,CONTROLLER://:9093"
    KAFKA_ADVERTISED_LISTENERS                      = "SASL://kafka:9092"
    KAFKA_CONTROLLER_QUORUM_VOTERS                  = "1@localhost:9093"
    KAFKA_CONTROLLER_LISTENER_NAMES                 = "CONTROLLER"
    KAFKA_LISTENER_SECURITY_PROTOCOL_MAP            = "CONTROLLER:PLAINTEXT,SASL:SASL_PLAINTEXT"
    KAFKA_INTER_BROKER_LISTENER_NAME                = "SASL"
    KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR          = "1"
    KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR  = "1"
    KAFKA_TRANSACTION_STATE_LOG_MIN_ISR             = "1"
    KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS          = "0"
    CLUSTER_ID                                      = "MkU3OEVBNTcwNTJENDM2Qk"
    KAFKA_LOG_DIRS                                   = "/var/lib/kafka/data"
    KAFKA_SASL_ENABLED_MECHANISMS                   = "SCRAM-SHA-512"
    KAFKA_SASL_MECHANISM_INTER_BROKER_PROTOCOL      = "SCRAM-SHA-512"
    KAFKA_AUTHORIZER_CLASS_NAME                     = "org.apache.kafka.metadata.authorizer.StandardAuthorizer"
    # Only one real trust domain here (the app, producer + all consumer-group roles) plus the
    # broker's own inter-broker identity -- super-user status replaces hand-written per-topic ACLs.
    KAFKA_SUPER_USERS                               = "User:inter-broker;User:taskmanager-app"
    KAFKA_OPTS                                       = "-Djava.security.auth.login.config=/etc/kafka/secrets/kafka_server_jaas.conf"
  }
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

# ---------------- Elasticsearch (single-node, security off for the demo) ----------------
resource "kubernetes_deployment" "elasticsearch" {
  metadata {
    name      = "elasticsearch"
    namespace = var.namespace
    labels    = local.elasticsearch
  }
  spec {
    replicas = 1
    selector { match_labels = local.elasticsearch }
    template {
      metadata { labels = local.elasticsearch }
      spec {
        container {
          name  = "elasticsearch"
          image = var.elasticsearch_image
          env {
            name  = "discovery.type"
            value = "single-node"
          }
          env {
            name  = "xpack.security.enabled"
            value = "false"
          }
          env {
            name  = "ES_JAVA_OPTS"
            value = "-Xms512m -Xmx512m"
          }
          port { container_port = 9200 }
          readiness_probe {
            http_get {
              path = "/_cluster/health"
              port = 9200
            }
            initial_delay_seconds = 20
            period_seconds        = 10
            failure_threshold     = 30
          }
        }
      }
    }
  }
}

resource "kubernetes_service" "elasticsearch" {
  metadata {
    name      = "elasticsearch"
    namespace = var.namespace
  }
  spec {
    selector = local.elasticsearch
    port {
      port        = 9200
      target_port = 9200
    }
  }
}

# ---------------- Kafka (single-node KRaft) ----------------
# Advertised as the in-cluster service DNS ("kafka:9092"), so pods reach it correctly — unlike the
# docker-compose broker that advertises localhost.

# Mounted into both the init container and the main container's KAFKA_OPTS (see kafka_common_env).
resource "kubernetes_config_map" "kafka_jaas" {
  metadata {
    name      = "kafka-jaas"
    namespace = var.namespace
  }
  data = {
    "kafka_server_jaas.conf" = local.kafka_jaas_conf
  }
}

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
        volume {
          name = "kafka-data"
          empty_dir {}
        }
        volume {
          name = "kafka-jaas"
          config_map {
            name = kubernetes_config_map.kafka_jaas.metadata[0].name
          }
        }

        # No PVC for this single-replica dev cluster (storage is already wiped on every pod
        # restart), so formatting once per pod start here -- rather than persisting credentials
        # across restarts -- matches the existing ephemeral lifecycle. Seeds the SCRAM credentials
        # directly at format time (kafka-storage.sh format --add-scram, KIP-900) so the broker never
        # has to serve an unauthenticated listener to bootstrap them.
        init_container {
          name    = "kafka-init"
          image   = var.kafka_image
          command = ["/bin/sh", "-c"]
          args = [<<-EOT
            set -e
            /opt/kafka/bin/kafka-run-class.sh kafka.docker.KafkaDockerWrapper setup \
              --default-configs-dir /etc/kafka/docker \
              --mounted-configs-dir /mnt/shared/config \
              --final-configs-dir /opt/kafka/config
            /opt/kafka/bin/kafka-storage.sh format \
              --cluster-id "$CLUSTER_ID" \
              --config /opt/kafka/config/server.properties \
              --ignore-formatted \
              --add-scram 'SCRAM-SHA-512=[name=inter-broker,password=inter-broker-secret]' \
              --add-scram 'SCRAM-SHA-512=[name=taskmanager-app,password=taskmanager-secret]'
          EOT
          ]
          dynamic "env" {
            for_each = local.kafka_common_env
            content {
              name  = env.key
              value = env.value
            }
          }
          volume_mount {
            name       = "kafka-data"
            mount_path = "/var/lib/kafka/data"
          }
          volume_mount {
            name       = "kafka-jaas"
            mount_path = "/etc/kafka/secrets"
            read_only  = true
          }
        }

        container {
          name  = "kafka"
          image = var.kafka_image
          port { container_port = 9092 }
          dynamic "env" {
            for_each = local.kafka_common_env
            content {
              name  = env.key
              value = env.value
            }
          }
          volume_mount {
            name       = "kafka-data"
            mount_path = "/var/lib/kafka/data"
          }
          volume_mount {
            name       = "kafka-jaas"
            mount_path = "/etc/kafka/secrets"
            read_only  = true
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
