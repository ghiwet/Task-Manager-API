output "cluster_name" {
  description = "Name of the provisioned kind cluster."
  value       = module.cluster.cluster_name
}

output "kubeconfig_path" {
  description = "Path to the kubeconfig for the kind cluster."
  value       = module.cluster.kubeconfig_path
}

output "app_port_forward" {
  description = "Command to reach the app locally, then hit http://localhost:8080/actuator/health."
  value       = "kubectl --context kind-${var.cluster_name} -n ${var.namespace} port-forward svc/task-manager-task-manager 8080:8080"
}
