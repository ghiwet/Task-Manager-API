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

# The app's image and config are now declared in the Argo CD Application (argocd/applications),
# not passed from Terraform — Terraform only bootstraps the cluster, backing services, and Argo CD.
