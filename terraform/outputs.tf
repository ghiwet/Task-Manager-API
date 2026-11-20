output "cluster_name" {
  description = "Name of the provisioned kind cluster."
  value       = module.cluster.cluster_name
}

output "kubeconfig_path" {
  description = "Path to the kubeconfig for the kind cluster."
  value       = module.cluster.kubeconfig_path
}

output "app_port_forward" {
  description = "Command to reach the API locally on http://localhost:8080. Actuator is on the pod's management port and not in the Service: kubectl port-forward deploy/task-manager-task-manager 8091:8091, then http://localhost:8091/actuator/health."
  value       = "kubectl --context kind-${var.cluster_name} -n ${var.namespace} port-forward svc/task-manager-task-manager 8080:8080"
}
