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

variable "image_repository" {
  description = "App image repo. For a fully local run, build + `kind load` an image and point here with pull policy Never."
  type        = string
  default     = "ghcr.io/ghiwet/task-manager-api"
}

variable "image_tag" {
  type    = string
  default = "0.1.1"
}

variable "image_pull_policy" {
  type    = string
  default = "IfNotPresent"
}

variable "openai_api_key" {
  description = "Optional OpenAI key for the AI assistant; blank leaves it degraded."
  type        = string
  default     = ""
  sensitive   = true
}
