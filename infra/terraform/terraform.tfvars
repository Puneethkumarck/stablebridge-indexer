# Default values for local development
# Override by creating terraform.tfvars.local (gitignored)

postgres_db       = "indexer"
postgres_user     = "indexer"
postgres_password = "indexer"

redpanda_memory = "512M"

grafana_admin_user     = "admin"
grafana_admin_password = "admin"

prometheus_retention = "30d"
