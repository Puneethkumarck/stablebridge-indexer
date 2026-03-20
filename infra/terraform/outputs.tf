output "postgres_url" {
  description = "JDBC connection URL for PostgreSQL"
  value       = "jdbc:postgresql://localhost:${var.postgres_port}/${var.postgres_db}"
}

output "redis_url" {
  description = "Redis connection URL"
  value       = "redis://localhost:${var.redis_port}"
}

output "kafka_bootstrap_servers" {
  description = "Kafka bootstrap servers for the application"
  value       = "localhost:${var.redpanda_kafka_port}"
}

output "services" {
  description = "Service endpoints"
  value = {
    postgresql       = "localhost:${var.postgres_port}"
    redis            = "localhost:${var.redis_port}"
    redis_insight    = "http://localhost:${var.redis_insight_port}"
    kafka            = "localhost:${var.redpanda_kafka_port}"
    redpanda_console = "http://localhost:${var.redpanda_console_port}"
    prometheus       = "http://localhost:${var.prometheus_port}"
    grafana          = "http://localhost:${var.grafana_port}"
  }
}
