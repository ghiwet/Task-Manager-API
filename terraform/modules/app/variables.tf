variable "namespace" {
  type = string
}

variable "chart_path" {
  description = "Path to the task-manager Helm chart."
  type        = string
}

variable "image_repository" {
  type    = string
  default = "ghcr.io/ghiwet/task-manager-api"
}

variable "image_tag" {
  type    = string
  default = "1.0.0"
}

variable "image_pull_policy" {
  type    = string
  default = "IfNotPresent"
}

# In-cluster addresses of the backing services.
variable "postgres_service" {
  type = string
}

variable "redis_service" {
  type = string
}

variable "kafka_bootstrap" {
  type = string
}

variable "keycloak_base_url" {
  type = string
}

variable "elasticsearch_uris" {
  type = string
}

variable "openai_api_key" {
  type      = string
  default   = ""
  sensitive = true
}
