variable "namespace" {
  description = "Namespace to deploy the backing services into."
  type        = string
}

variable "postgres_image" {
  type    = string
  default = "pgvector/pgvector:pg18"
}

variable "postgres_db" {
  type    = string
  default = "taskdb"
}

variable "postgres_user" {
  type    = string
  default = "taskuser"
}

variable "postgres_password" {
  description = "Owner role password; no default, so a dev value can't reach a real cluster (threat #18)."
  type        = string
  sensitive   = true
}

variable "kafka_app_password" {
  description = "SCRAM password for the app's Kafka user (taskmanager-app); no default, must match the app SealedSecret's JAAS line."
  type        = string
  sensitive   = true
}

variable "redis_image" {
  type    = string
  default = "redis:7-alpine"
}

variable "kafka_image" {
  # Must stay 4.3.0+ to match the SASL/KRaft setup in this module's init_container, and
  # docker-compose.yml's pin.
  type    = string
  default = "apache/kafka:4.3.0"
}

variable "keycloak_image" {
  type    = string
  default = "quay.io/keycloak/keycloak:24.0.3"
}

variable "elasticsearch_image" {
  type    = string
  default = "docker.elastic.co/elasticsearch/elasticsearch:9.4.2"
}

variable "realm_export_path" {
  description = "Path to the Keycloak realm export imported on startup."
  type        = string
}

variable "app_pod_label" {
  description = "Label selector for the app's pods, used by each NetworkPolicy's ingress allow-list (threat #51). Must match helm/task-manager's selectorLabels."
  type        = map(string)
  default     = { "app.kubernetes.io/name" = "task-manager" }
}
