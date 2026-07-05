# In-cluster addresses the app is pointed at (consumed by the app module).
output "postgres_service" {
  value = kubernetes_service.postgres.metadata[0].name
}

output "redis_service" {
  value = kubernetes_service.redis.metadata[0].name
}

output "kafka_bootstrap" {
  value = "${kubernetes_service.kafka.metadata[0].name}:9092"
}

output "keycloak_base_url" {
  value = "http://${kubernetes_service.keycloak.metadata[0].name}:8080"
}

output "elasticsearch_uris" {
  value = "http://${kubernetes_service.elasticsearch.metadata[0].name}:9200"
}
