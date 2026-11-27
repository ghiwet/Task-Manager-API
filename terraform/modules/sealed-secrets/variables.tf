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

variable "sealing_cert_path" {
  description = "Path to the PEM public cert for the controller's sealing key. Missing file = controller self-generates."
  type        = string
  default     = ""
}

variable "sealing_key_path" {
  description = "Path to the PEM private key for the controller's sealing key. Missing file = controller self-generates. A path, not file content, keeps the key out of state (threat #43)."
  type        = string
  default     = ""
}

variable "kubeconfig_path" {
  description = "Kubeconfig of the target cluster, used by the key-seeding kubectl (not the default context)."
  type        = string
}
