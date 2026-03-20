# ---------------------------------------------------------------------------
# Docker
# ---------------------------------------------------------------------------
variable "docker_host" {
  description = "Docker daemon socket"
  type        = string
  default     = "unix:///var/run/docker.sock"
}

variable "network_name" {
  description = "Docker network for all containers"
  type        = string
  default     = "indexer-network"
}

# ---------------------------------------------------------------------------
# PostgreSQL
# ---------------------------------------------------------------------------
variable "postgres_image" {
  description = "PostgreSQL Docker image"
  type        = string
  default     = "postgres:16-alpine"
}

variable "postgres_db" {
  description = "Database name"
  type        = string
  default     = "indexer"
}

variable "postgres_user" {
  description = "Database user"
  type        = string
  default     = "indexer"
}

variable "postgres_password" {
  description = "Database password"
  type        = string
  default     = "indexer"
  sensitive   = true
}

variable "postgres_port" {
  description = "Host port for PostgreSQL"
  type        = number
  default     = 5432
}

# ---------------------------------------------------------------------------
# Redis
# ---------------------------------------------------------------------------
variable "redis_image" {
  description = "Redis Stack image (includes Bloom module)"
  type        = string
  default     = "redis/redis-stack:latest"
}

variable "redis_port" {
  description = "Host port for Redis"
  type        = number
  default     = 6379
}

variable "redis_insight_port" {
  description = "Host port for Redis Insight UI"
  type        = number
  default     = 8001
}

# ---------------------------------------------------------------------------
# Redpanda (Kafka-compatible)
# ---------------------------------------------------------------------------
variable "redpanda_image" {
  description = "Redpanda image"
  type        = string
  default     = "redpandadata/redpanda:v24.3.1"
}

variable "redpanda_kafka_port" {
  description = "Host port for Kafka protocol"
  type        = number
  default     = 19092
}

variable "redpanda_proxy_port" {
  description = "Host port for Pandaproxy (HTTP)"
  type        = number
  default     = 18082
}

variable "redpanda_console_image" {
  description = "Redpanda Console image"
  type        = string
  default     = "redpandadata/console:v2.8.0"
}

variable "redpanda_console_port" {
  description = "Host port for Redpanda Console"
  type        = number
  default     = 9090
}

variable "redpanda_memory" {
  description = "Memory limit for Redpanda"
  type        = string
  default     = "512M"
}

# ---------------------------------------------------------------------------
# Prometheus
# ---------------------------------------------------------------------------
variable "prometheus_image" {
  description = "Prometheus image"
  type        = string
  default     = "prom/prometheus:v3.4.0"
}

variable "prometheus_port" {
  description = "Host port for Prometheus"
  type        = number
  default     = 9091
}

variable "prometheus_retention" {
  description = "Prometheus data retention period"
  type        = string
  default     = "30d"
}

# ---------------------------------------------------------------------------
# Grafana
# ---------------------------------------------------------------------------
variable "grafana_image" {
  description = "Grafana image"
  type        = string
  default     = "grafana/grafana:11.6.0"
}

variable "grafana_port" {
  description = "Host port for Grafana"
  type        = number
  default     = 3000
}

variable "grafana_admin_user" {
  description = "Grafana admin username"
  type        = string
  default     = "admin"
}

variable "grafana_admin_password" {
  description = "Grafana admin password"
  type        = string
  default     = "admin"
  sensitive   = true
}
