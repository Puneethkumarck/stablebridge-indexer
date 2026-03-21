# ---------------------------------------------------------------------------
# StableBridge Indexer — Local Infrastructure (Docker)
#
# Manages the same services as docker-compose.yml via Terraform:
#   PostgreSQL 16, Redis Stack (Bloom), Redpanda (Kafka), Prometheus, Grafana
#
# Usage:
#   terraform init
#   terraform apply
#   terraform destroy
# ---------------------------------------------------------------------------

locals {
  project_root = abspath("${path.module}/../..")
}

# ---------------------------------------------------------------------------
# Docker Network
# ---------------------------------------------------------------------------
resource "docker_network" "indexer" {
  name = var.network_name
}

# ---------------------------------------------------------------------------
# Docker Images
# ---------------------------------------------------------------------------
resource "docker_image" "postgres" {
  name         = var.postgres_image
  keep_locally = true
}

resource "docker_image" "redis" {
  name         = var.redis_image
  keep_locally = true
}

resource "docker_image" "redpanda" {
  name         = var.redpanda_image
  keep_locally = true
}

resource "docker_image" "redpanda_console" {
  name         = var.redpanda_console_image
  keep_locally = true
}

resource "docker_image" "prometheus" {
  name         = var.prometheus_image
  keep_locally = true
}

resource "docker_image" "grafana" {
  name         = var.grafana_image
  keep_locally = true
}

# ---------------------------------------------------------------------------
# Docker Volumes
# ---------------------------------------------------------------------------
resource "docker_volume" "pg_data" {
  name = "indexer_pg_data"
}

resource "docker_volume" "redis_data" {
  name = "indexer_redis_data"
}

resource "docker_volume" "redpanda_data" {
  name = "indexer_redpanda_data"
}

resource "docker_volume" "grafana_data" {
  name = "indexer_grafana_data"
}

# ---------------------------------------------------------------------------
# PostgreSQL
# ---------------------------------------------------------------------------
resource "docker_container" "postgres" {
  name  = "indexer-postgres"
  image = docker_image.postgres.image_id

  env = [
    "POSTGRES_DB=${var.postgres_db}",
    "POSTGRES_USER=${var.postgres_user}",
    "POSTGRES_PASSWORD=${var.postgres_password}",
  ]

  ports {
    internal = 5432
    external = var.postgres_port
  }

  volumes {
    volume_name    = docker_volume.pg_data.name
    container_path = "/var/lib/postgresql/data"
  }

  networks_advanced {
    name = docker_network.indexer.name
  }

  restart = "unless-stopped"

  healthcheck {
    test         = ["CMD-SHELL", "pg_isready -U ${var.postgres_user} -d ${var.postgres_db}"]
    interval     = "10s"
    timeout      = "5s"
    retries      = 5
    start_period = "10s"
  }
}

# ---------------------------------------------------------------------------
# Redis Stack (includes Bloom filter module)
# ---------------------------------------------------------------------------
resource "docker_container" "redis" {
  name  = "indexer-redis"
  image = docker_image.redis.image_id

  ports {
    internal = 6379
    external = var.redis_port
  }

  ports {
    internal = 8001
    external = var.redis_insight_port
  }

  volumes {
    volume_name    = docker_volume.redis_data.name
    container_path = "/data"
  }

  networks_advanced {
    name = docker_network.indexer.name
  }

  restart = "unless-stopped"

  healthcheck {
    test         = ["CMD", "redis-cli", "ping"]
    interval     = "10s"
    timeout      = "5s"
    retries      = 5
    start_period = "10s"
  }
}

# ---------------------------------------------------------------------------
# Redpanda (Kafka-compatible streaming)
# ---------------------------------------------------------------------------
resource "docker_container" "redpanda" {
  name  = "indexer-redpanda"
  image = docker_image.redpanda.image_id

  command = [
    "redpanda", "start",
    "--smp", "1",
    "--memory", var.redpanda_memory,
    "--overprovisioned",
    "--kafka-addr", "internal://0.0.0.0:9092,external://0.0.0.0:19092",
    "--advertise-kafka-addr", "internal://indexer-redpanda:9092,external://localhost:${var.redpanda_kafka_port}",
    "--pandaproxy-addr", "internal://0.0.0.0:8082,external://0.0.0.0:18082",
    "--advertise-pandaproxy-addr", "internal://indexer-redpanda:8082,external://localhost:${var.redpanda_proxy_port}",
  ]

  ports {
    internal = 19092
    external = var.redpanda_kafka_port
  }

  ports {
    internal = 18082
    external = var.redpanda_proxy_port
  }

  volumes {
    volume_name    = docker_volume.redpanda_data.name
    container_path = "/var/lib/redpanda/data"
  }

  networks_advanced {
    name = docker_network.indexer.name
  }

  restart = "unless-stopped"
}

# ---------------------------------------------------------------------------
# Redpanda Console (Kafka topic browser)
# ---------------------------------------------------------------------------
resource "docker_container" "redpanda_console" {
  name  = "indexer-redpanda-console"
  image = docker_image.redpanda_console.image_id

  env = [
    "KAFKA_BROKERS=indexer-redpanda:9092",
  ]

  ports {
    internal = 8080
    external = var.redpanda_console_port
  }

  networks_advanced {
    name = docker_network.indexer.name
  }

  restart   = "unless-stopped"
  depends_on = [docker_container.redpanda]
}

# ---------------------------------------------------------------------------
# Prometheus
# ---------------------------------------------------------------------------
resource "docker_container" "prometheus" {
  name  = "indexer-prometheus"
  image = docker_image.prometheus.image_id

  command = [
    "--config.file=/etc/prometheus/prometheus.yml",
    "--storage.tsdb.retention.time=${var.prometheus_retention}",
  ]

  ports {
    internal = 9090
    external = var.prometheus_port
  }

  volumes {
    host_path      = "${local.project_root}/infra/prometheus/prometheus.yml"
    container_path = "/etc/prometheus/prometheus.yml"
    read_only      = true
  }

  volumes {
    host_path      = "${local.project_root}/infra/prometheus/alerts.yml"
    container_path = "/etc/prometheus/alerts.yml"
    read_only      = true
  }

  networks_advanced {
    name = docker_network.indexer.name
  }

  restart = "unless-stopped"
}

# ---------------------------------------------------------------------------
# Grafana
# ---------------------------------------------------------------------------
resource "docker_container" "grafana" {
  name  = "indexer-grafana"
  image = docker_image.grafana.image_id

  env = [
    "GF_SECURITY_ADMIN_USER=${var.grafana_admin_user}",
    "GF_SECURITY_ADMIN_PASSWORD=${var.grafana_admin_password}",
    "GF_USERS_ALLOW_SIGN_UP=false",
  ]

  ports {
    internal = 3000
    external = var.grafana_port
  }

  volumes {
    host_path      = "${local.project_root}/infra/grafana/provisioning"
    container_path = "/etc/grafana/provisioning"
    read_only      = true
  }

  volumes {
    host_path      = "${local.project_root}/infra/grafana/dashboards"
    container_path = "/var/lib/grafana/dashboards"
    read_only      = true
  }

  volumes {
    volume_name    = docker_volume.grafana_data.name
    container_path = "/var/lib/grafana"
  }

  networks_advanced {
    name = docker_network.indexer.name
  }

  restart    = "unless-stopped"
  depends_on = [docker_container.prometheus]
}

# ---------------------------------------------------------------------------
# Application
# ---------------------------------------------------------------------------
resource "docker_container" "app" {
  name  = "indexer-app"
  image = var.app_image

  env = [
    "SPRING_DATASOURCE_URL=jdbc:postgresql://indexer-postgres:5432/${var.postgres_db}",
    "SPRING_DATASOURCE_USERNAME=${var.postgres_user}",
    "SPRING_DATASOURCE_PASSWORD=${var.postgres_password}",
    "SPRING_DATA_REDIS_HOST=indexer-redis",
    "SPRING_DATA_REDIS_PORT=6379",
    "KAFKA_BOOTSTRAP_SERVERS=indexer-redpanda:9092",
    "INDEXER_API_KEY=${var.indexer_api_key}",
    "SPRING_PROFILES_ACTIVE=${var.spring_profiles_active}",
    "ETHEREUM_RPC_URL=${var.ethereum_rpc_url}",
    "SEPOLIA_RPC_URL=${var.sepolia_rpc_url}",
    "BASE_SEPOLIA_RPC_URL=${var.base_sepolia_rpc_url}",
    "SOLANA_DEVNET_RPC_URL=${var.solana_devnet_rpc_url}",
  ]

  ports {
    internal = 8080
    external = var.app_port
  }

  ports {
    internal = 8081
    external = var.app_mgmt_port
  }

  networks_advanced {
    name = docker_network.indexer.name
  }

  restart    = "unless-stopped"
  depends_on = [docker_container.postgres, docker_container.redis, docker_container.redpanda]

  healthcheck {
    test         = ["CMD-SHELL", "curl -sf http://localhost:8081/actuator/health || exit 1"]
    interval     = "15s"
    timeout      = "5s"
    retries      = 10
    start_period = "30s"
  }
}
