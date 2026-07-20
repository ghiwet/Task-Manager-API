output "namespace" {
  value       = helm_release.sealed_secrets.namespace
  description = "Namespace the Sealed Secrets controller runs in."
}
