output "namespace" {
  value       = helm_release.argocd.namespace
  description = "Namespace Argo CD is installed in."
}
