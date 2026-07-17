variable "namespace" {
  description = "Namespace to install Argo CD into."
  type        = string
  default     = "argocd"
}

variable "chart_version" {
  description = "Argo CD Helm chart version (argo/argo-cd)."
  type        = string
  default     = "10.1.3"
}
