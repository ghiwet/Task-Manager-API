terraform {
  required_providers {
    kind = {
      source  = "tehcyx/kind"
      version = "~> 0.9"
    }
  }
}

# A throwaway local Kubernetes cluster (kind = Kubernetes in Docker).
resource "kind_cluster" "this" {
  name           = var.cluster_name
  wait_for_ready = true
}
