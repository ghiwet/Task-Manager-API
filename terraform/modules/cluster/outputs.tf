output "cluster_name" {
  value = kind_cluster.this.name
}

output "kubeconfig_path" {
  value = kind_cluster.this.kubeconfig_path
}

# Connection details for the kubernetes/helm providers used by later modules.
output "endpoint" {
  value = kind_cluster.this.endpoint
}

output "client_certificate" {
  value = kind_cluster.this.client_certificate
}

output "client_key" {
  value = kind_cluster.this.client_key
}

output "cluster_ca_certificate" {
  value = kind_cluster.this.cluster_ca_certificate
}
