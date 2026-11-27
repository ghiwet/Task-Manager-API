variable "cluster_name" {
  description = "Name of the local kind cluster."
  type        = string
  default     = "task-manager"
}

variable "namespace" {
  description = "Kubernetes namespace for the app and its backing services."
  type        = string
  default     = "task-manager"
}

variable "postgres_owner_password" {
  description = "Postgres owner (Flyway) role password. No default (threat #18): set it in terraform.tfvars (gitignored); it must match the migration SealedSecret."
  type        = string
  sensitive   = true
}

# The app's image and config are now declared in the Argo CD Application (argocd/applications),
# not passed from Terraform — Terraform only bootstraps the cluster, backing services, and Argo CD.

variable "kafka_app_password" {
  description = "SCRAM password for the app's Kafka user (taskmanager-app). No default: set it in terraform.tfvars (gitignored); it must match the JAAS line in the app SealedSecret."
  type        = string
  sensitive   = true

  # It's spliced into kafka-storage's --add-scram '[name=...,password=...]' spec and the JAAS line.
  validation {
    condition     = can(regex("^[A-Za-z0-9_-]{16,}$", var.kafka_app_password))
    error_message = "kafka_app_password must be at least 16 characters of letters, digits, '_' or '-'."
  }
}
