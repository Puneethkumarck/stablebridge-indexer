.PHONY: help build test integration-test clean run run-testnet \
       infra-up infra-down infra-status infra-logs \
       up up-testnet down \
       smoke-test api-test register-wallet check-status \
       docker-build terraform-init terraform-up terraform-down

# ---------------------------------------------------------------------------
# Help
# ---------------------------------------------------------------------------
help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | \
		awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-20s\033[0m %s\n", $$1, $$2}'

# ---------------------------------------------------------------------------
# Build & Test
# ---------------------------------------------------------------------------
build: ## Build everything (compile + Spotless + tests)
	./gradlew build

test: ## Run unit tests only
	./gradlew test

integration-test: ## Run integration tests (requires Docker services)
	./gradlew integrationTest

clean: ## Clean build artifacts
	./gradlew clean

# ---------------------------------------------------------------------------
# Run Application
# ---------------------------------------------------------------------------
run: ## Run with default profile (mainnet config)
	./gradlew :stablebridge-indexer:bootRun

run-testnet: ## Run with testnet profile (Sepolia, Base Sepolia, Solana Devnet)
	./gradlew :stablebridge-indexer:bootRun --args='--spring.profiles.active=testnet'

# ---------------------------------------------------------------------------
# One-command up/down (infra + app in containers)
# ---------------------------------------------------------------------------
up: docker-build ## Build image, start infra + app (mainnet)
	docker compose --profile app up -d
	@echo "Waiting for app to be healthy..."
	@until curl -sf http://localhost:8081/actuator/health > /dev/null 2>&1; do sleep 2; done
	@$(MAKE) --no-print-directory _print-urls

up-testnet: docker-build ## Build image, start infra + app (testnet profile)
	SPRING_PROFILES_ACTIVE=testnet docker compose --profile app up -d
	@echo "Waiting for app to be healthy..."
	@until curl -sf http://localhost:8081/actuator/health > /dev/null 2>&1; do sleep 2; done
	@$(MAKE) --no-print-directory _print-urls PROFILE=testnet

_print-urls:
	@echo ""
	@echo "============================================"
	@echo " StableBridge Indexer is running $(if $(PROFILE),($(PROFILE)),)"
	@echo "============================================"
	@echo ""
	@echo " App API:           http://localhost:8080/api/v1/status"
	@echo " Actuator Health:   http://localhost:8081/actuator/health"
	@echo " Prometheus Metrics: http://localhost:8081/actuator/prometheus"
	@echo ""
	@echo " Redpanda Console:  http://localhost:9090"
	@echo " Redis Insight:     http://localhost:8001"
	@echo " Prometheus:        http://localhost:9091"
	@echo " Grafana:           http://localhost:3000  (admin/admin)"
	@echo ""
	@echo " API Key Header:    X-API-Key: $${INDEXER_API_KEY:-change-me}"
	@echo "============================================"
	@echo ""

down: ## Stop everything (app + infra)
	docker compose --profile app down

# ---------------------------------------------------------------------------
# Docker Compose Infrastructure
# ---------------------------------------------------------------------------
infra-up: ## Start local infrastructure (PostgreSQL, Redis, Redpanda, Prometheus, Grafana)
	docker compose up -d

infra-down: ## Stop local infrastructure
	docker compose down

infra-clean: ## Stop infrastructure and delete all volumes
	docker compose down -v

infra-status: ## Show infrastructure container status
	docker compose ps

infra-logs: ## Tail infrastructure logs
	docker compose logs -f

# ---------------------------------------------------------------------------
# Terraform Local Infrastructure
# ---------------------------------------------------------------------------
terraform-init: ## Initialize Terraform (local Docker provider)
	cd infra/terraform && terraform init

terraform-plan: ## Show Terraform execution plan
	cd infra/terraform && terraform plan

terraform-up: ## Provision local infrastructure via Terraform
	cd infra/terraform && terraform apply -auto-approve

terraform-down: ## Destroy Terraform-managed infrastructure
	cd infra/terraform && terraform destroy -auto-approve

# ---------------------------------------------------------------------------
# Docker Image
# ---------------------------------------------------------------------------
docker-build: ## Build production Docker image via Jib
	./gradlew :stablebridge-indexer:jibDockerBuild \
		-Djib.applicationCache=/tmp/jib-cache \
		-Djib.baseImageCache=/tmp/jib-base-cache

# ---------------------------------------------------------------------------
# Testnet Operations
# ---------------------------------------------------------------------------
smoke-test: ## Run smoke test against running indexer
	./scripts/smoke-test.sh

api-test: ## Run Newman/Postman API tests against running indexer
	newman run postman/stablebridge-indexer.postman_collection.json \
		-e postman/local.postman_environment.json \
		--reporters cli,junit \
		--reporter-junit-export build/reports/newman/results.xml

api-test-testnet: ## Run Newman API tests against testnet instance
	newman run postman/stablebridge-indexer.postman_collection.json \
		-e postman/testnet.postman_environment.json \
		--reporters cli

register-wallet: ## Register a test wallet (usage: make register-wallet ADDR=0x... TYPE=EVM)
	@curl -s -X POST http://localhost:8080/api/v1/wallets \
		-H "X-API-Key: $${INDEXER_API_KEY:-change-me}" \
		-H "Content-Type: application/json" \
		-d '{"address": "$(ADDR)", "networkType": "$(TYPE)"}' | jq .

check-status: ## Check all chain statuses
	@curl -s -H "X-API-Key: $${INDEXER_API_KEY:-change-me}" \
		http://localhost:8080/api/v1/status | jq .

check-health: ## Check actuator health
	@curl -s http://localhost:8081/actuator/health | jq .

check-redis: ## Show Redis block progress and Bloom stats
	@echo "=== Block Progress ==="
	@docker exec indexer-redis redis-cli HGETALL indexer:progress
	@echo "\n=== Bloom Filter: EVM ==="
	@docker exec indexer-redis redis-cli BF.INFO indexer:bloom:EVM 2>/dev/null || echo "(not initialized)"
	@echo "\n=== Bloom Filter: SOLANA ==="
	@docker exec indexer-redis redis-cli BF.INFO indexer:bloom:SOLANA 2>/dev/null || echo "(not initialized)"

check-kafka: ## List Kafka topics and message counts
	@docker exec indexer-redpanda rpk topic list --brokers localhost:9092
