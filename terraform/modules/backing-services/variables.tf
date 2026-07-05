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
  type    = string
  default = "taskpass"
}

variable "redis_image" {
  type    = string
  default = "redis:7-alpine"
}

variable "kafka_image" {
  type    = string
  default = "apache/kafka:3.9.0"
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
