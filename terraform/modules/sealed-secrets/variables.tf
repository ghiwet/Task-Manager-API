variable "namespace" {
  description = "Namespace for the Sealed Secrets controller (kube-system keeps kubeseal zero-config)."
  type        = string
  default     = "kube-system"
}

variable "chart_version" {
  description = "sealed-secrets Helm chart version (bitnami/sealed-secrets)."
  type        = string
  default     = "2.5.19"
}

variable "sealing_cert" {
  description = "PEM public cert for the controller's sealing key. Empty = controller self-generates."
  type        = string
  default     = ""
}

variable "sealing_key" {
  description = "PEM private key for the controller's sealing key. Empty = controller self-generates."
  type        = string
  default     = ""
  sensitive   = true
}
