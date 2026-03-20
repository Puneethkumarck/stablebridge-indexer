<div align="center">

![Build](https://github.com/Puneethkumarck/stablebridge-indexer/actions/workflows/ci.yml/badge.svg)
![Java 25](https://img.shields.io/badge/Java-25-blue)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.3-brightgreen)
![Kafka](https://img.shields.io/badge/Kafka-streaming-orange)
![Redis](https://img.shields.io/badge/Redis-Bloom%20Filter-red)
![License](https://img.shields.io/badge/License-MIT-yellow)

# stablebridge-indexer

:chains: **Multichain stablecoin deposit detection — index finalized blocks across EVM, Solana, and Bitcoin with zero false positives and at-least-once delivery guarantees** :mag:

</div>

---

## The Problem

Watching millions of blockchain transactions across multiple chains for your specific wallet addresses is slow, error-prone, and expensive. Miss a deposit and you lose revenue. Detect a false positive and you credit money that never arrived.

## The Solution

StableBridge Indexer processes only **finalized blocks** (zero reorg risk) and uses a **Redis Bloom filter for sub-millisecond address matching** followed by **PostgreSQL confirmation** (zero false positives). Transfer events stream to Kafka with **at-least-once delivery** — duplicates are safe with idempotent consumers, but missed deposits are catastrophic.

## The Result

Real-time stablecoin deposit detection across three chain types, ready for payment matching, compliance, webhooks, and reconciliation.

<div align="center">

| Metric | Value |
|--------|-------|
| **Chains** | EVM + Solana + Bitcoin |
| **Finality** | Zero reorg risk (finalized blocks only) |
| **False positives** | Zero (Bloom + DB confirm) |
| **Delivery** | At-least-once (Kafka before Redis) |
| **Throughput** | Virtual Threads + batch JSON-RPC |
| **Recovery** | Auto-healing workers (PARKED -> RUNNING) |

</div>

---

## :building_construction: Architecture

```
Blockchain RPCs (finalized blocks only)
  -> Chain Indexers (EVM, Solana, Bitcoin)
    -> Worker Engine (Regular, Catchup, Rescan)
      -> Bloom Filter (Redis) + DB Confirm (PostgreSQL)
        -> Kafka Producer (per-chain topics, key=toAddress)
          -> Consumer Apps (payment matching, compliance, webhooks, reconciliation)
```

The project follows **hexagonal architecture** with ArchUnit enforcement (5 rules at build time):

```
domain/
  model/        Transfer, IndexedBlock, BlockResult, WalletAddress, enums
  port/         ChainIndexer, AddressFilter, TransferEventPublisher,
                BlockProgressStore, WalletAddressRepository
  service/      RegularWorker, CatchupWorker, RescanWorker,
                IndexerOrchestrator, WalletCommandHandler, StatusQueryHandler
  event/        TransferDetectedEvent

infrastructure/
  chain/evm/       EvmChainIndexer, EvmRpcClient, ERC-20 + native parsers
  chain/solana/    SolanaChainIndexer, SPL token + native parsers
  chain/bitcoin/   BitcoinChainIndexer, UTXO transfer parser
  bloom/           RedisBloomAddressFilter, GuavaBloomAddressFilter
  progress/        RedisBlockProgressStore
  persistence/     WalletAddressEntity, JPA adapter
  messaging/       KafkaTransferEventPublisher
  security/        ApiKeyAuthFilter

application/
  config/          ChainAutoConfiguration, KafkaConfig, RedisConfig
  controller/      WalletController, StatusController
  properties/      IndexerProperties, ChainProperties, RpcProperties
```

### Module Structure

| Module | Purpose |
|--------|---------|
| `stablebridge-indexer-api` | Shared DTOs: TransferEvent, NetworkType, WalletAddressRequest/Response |
| `stablebridge-indexer-client` | Feign client for wallet management API |
| `stablebridge-indexer` | Main application — domain, infrastructure, application layers |

---

## :rocket: Quick Start

### Prerequisites

- **Java 25** (Eclipse Temurin)
- **Docker** and Docker Compose
- **Gradle 9.0** (wrapper included)

### 1. Clone and start infrastructure

```bash
git clone https://github.com/Puneethkumarck/stablebridge-indexer.git
cd stablebridge-indexer
docker compose up -d
```

This starts PostgreSQL 16, Redis Stack (with Bloom), Redpanda (Kafka-compatible), Prometheus, and Grafana.

### 2. Configure environment

```bash
export INDEXER_API_KEY=your-api-key
export ETHEREUM_RPC_URL=https://eth-mainnet.g.alchemy.com/v2/YOUR_KEY
```

### 3. Build and run

```bash
./gradlew build
./gradlew :stablebridge-indexer:bootRun
```

### 4. Register a wallet address

```bash
curl -X POST http://localhost:8080/api/v1/wallets \
  -H "X-API-Key: $INDEXER_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"address": "0xYourAddress", "networkType": "EVM", "label": "merchant-1"}'
```

### 5. Check indexer status

```bash
curl http://localhost:8080/api/v1/status \
  -H "X-API-Key: $INDEXER_API_KEY"
```

---

## :gear: Configuration

All configuration lives in `application.yml` with environment variable overrides.

### Chain Configuration

```yaml
indexer:
  chains:
    ethereum_mainnet:
      enabled: true
      type: evm
      confirmation-strategy: finalized    # or "confirmations" with min-confirmations
      index-native-transfers: false       # true to track ETH transfers
      native-decimals: 18
      start-block: 0
      poll-interval: 12s
      batch-size: 10
      rpc:
        urls:
          - ${ETHEREUM_RPC_URL}
        timeout: 10s
        batch-size: 50                    # JSON-RPC batch size
        use-block-receipts: false         # true for eth_getBlockReceipts support
      token-contracts:
        - address: "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48"
          symbol: USDC
          decimals: 6
        - address: "0xdAC17F958D2ee523a2206206994597C13D831ec7"
          symbol: USDT
          decimals: 6
```

### Bloom Filter

```yaml
indexer:
  bloom:
    backend: redis          # "redis" for production, "guava" for local dev
    error-rate: 0.001       # 0.1% false positive rate
    expected-insertions: 1000000
```

### Supported Chains

| Chain | Type | Finality Strategy | Stablecoins |
|-------|------|-------------------|-------------|
| Ethereum Mainnet | EVM | `finalized` tag | USDC, USDT, DAI, PYUSD, EURC |
| Base Mainnet | EVM | 10 confirmations | USDC, EURC |
| Solana Mainnet | Solana | `finalized` commitment | USDC, USDT |
| Bitcoin Mainnet | Bitcoin | 6 confirmations | Native BTC (UTXO) |

---

## :satellite: API Reference

All endpoints require the `X-API-Key` header. The application runs on port `8080`, with management endpoints on port `8081`.

### Wallet Management

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/v1/wallets` | Register a wallet address for monitoring |
| `POST` | `/api/v1/wallets/batch` | Register multiple wallet addresses |
| `GET` | `/api/v1/wallets?networkType=EVM` | List wallets by network type |
| `DELETE` | `/api/v1/wallets/{address}?networkType=EVM` | Remove a wallet address |
| `POST` | `/api/v1/wallets/bloom/rebuild` | Rebuild the Bloom filter |

### Status & Health

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/v1/status` | All chain indexer statuses |
| `GET` | `/api/v1/status/{chainName}` | Status for a specific chain |
| `GET` | `/api/v1/status/bloom` | Bloom filter status |

### Management (port 8081)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/actuator/health` | Health check with component details |
| `GET` | `/actuator/prometheus` | Prometheus metrics |

---

## :bar_chart: Observability

### Prometheus Metrics

The indexer exposes custom metrics on port `8081` at `/actuator/prometheus`:

| Metric | Type | Description |
|--------|------|-------------|
| `indexer_chain_lag` | Gauge | Block lag per chain |
| `indexer_blocks_processed_total` | Counter | Blocks processed per chain |
| `indexer_blocks_failed_total` | Counter | Failed blocks per chain |
| `indexer_transfers_detected_total` | Counter | Transfers detected per chain |
| `indexer_kafka_publish_failed_total` | Counter | Kafka publish failures |
| `indexer_bloom_size` | Gauge | Bloom filter size per network type |
| `indexer_rpc_latency_seconds` | Histogram | RPC call latency per chain/method |

### Alert Rules

Six alert rules are preconfigured in `infra/prometheus/alerts.yml`:

| Alert | Severity | Condition |
|-------|----------|-----------|
| IndexerChainLagHigh | P1 Critical | Chain lag > 50 blocks for 5 min |
| IndexerChainDown | P1 Critical | Indexer unreachable for 3 min |
| IndexerKafkaPublishFailed | P1 Critical | Any Kafka publish failures |
| IndexerRpcErrorRate | P2 Warning | RPC error rate > 50% for 2 min |
| IndexerFailedBlocks | P2 Warning | > 10 failed blocks for 5 min |
| IndexerBloomFilterEmpty | P2 Warning | Empty Bloom filter for 5 min |

### Dashboards

Grafana is included in `docker-compose.yml` with a preconfigured dashboard:

- **URL**: http://localhost:3000 (admin/admin)
- **Dashboard**: `infra/grafana/dashboards/indexer-dashboard.json`
- **Datasource**: Auto-provisioned from `infra/grafana/provisioning/`

### Structured Logging

All logs use structured JSON format with 10 enriched MDC fields for audit trails and merchant dispute resolution. Powered by Logstash Logback encoder.

---

## :whale: Docker

### Development Infrastructure

```bash
docker compose up -d
```

| Service | Port | Description |
|---------|------|-------------|
| PostgreSQL 16 | 5432 | Wallet address storage |
| Redis Stack | 6379 / 8001 (UI) | Bloom filter, block progress, failed blocks |
| Redpanda | 19092 (Kafka) | Transfer event streaming |
| Redpanda Console | 9090 | Kafka topic browser |
| Prometheus | 9091 | Metrics collection |
| Grafana | 3000 | Dashboards and alerting |

### Production Image

Build a production Docker image with Jib (no Docker daemon required):

```bash
./gradlew :stablebridge-indexer:jibDockerBuild
```

Image: `stablebridge/indexer` based on `eclipse-temurin:25-jre`.

---

## :test_tube: Testing

The project has **414 tests** across **44 test files**, organized by category:

```bash
# Run all tests
./gradlew test

# Run integration tests (requires Docker services)
./gradlew integrationTest

# Run all checks (Spotless + compile + tests)
./gradlew build
```

### Testing Stack

| Tool | Purpose |
|------|---------|
| **JUnit 5** | Test framework |
| **BDDMockito** | `given()`/`then()` style mocking exclusively |
| **ArchUnit** | 5 architectural rules enforced at build time |
| **WireMock** | RPC client adapter tests |
| **Testcontainers** | Integration tests with real PostgreSQL and Redis |
| **TestFixtures** | Shared factory methods in `src/testFixtures/` |

### Architectural Rules (ArchUnit)

| Rule | Enforces |
|------|----------|
| Domain must not depend on infrastructure | Pure domain layer |
| Domain must not depend on application | Domain is innermost layer |
| Domain must not import Spring (except `@Service`, `@Transactional`) | Minimal framework coupling |
| Domain must not import `jakarta.persistence` | JPA stays in infrastructure |
| Infrastructure must not depend on controllers | No reverse dependencies |

---

## :arrows_counterclockwise: Worker Lifecycle

Three worker types process blocks concurrently per chain:

| Worker | Purpose |
|--------|---------|
| **RegularWorker** | Follows chain tip, processes new finalized blocks |
| **CatchupWorker** | Fills gaps between current progress and chain tip |
| **RescanWorker** | Retries failed blocks from the sorted set in Redis |

### State Machine

```
RUNNING -> (all RPCs fail) -> PARKED -> (RPC recovers, 60s health probe) -> RUNNING
                                     -> (shutdown signal) -> STOPPED
```

- Workers auto-recover from transient RPC failures
- PARKED state triggers a 60-second health probe cycle
- Graceful shutdown: SIGTERM -> stop polling -> drain 30s -> flush Kafka -> save progress -> exit

### Processing Order

```
Fetch block -> Parse transfers (whitelist only)
  -> Bloom check (sub-ms) -> DB confirm (zero false positives)
    -> Publish to Kafka -> Save progress to Redis
```

---

## :shield: Delivery Guarantees

| Guarantee | Implementation |
|-----------|---------------|
| **At-least-once delivery** | Kafka publish happens BEFORE Redis progress save. If the process crashes after Kafka but before Redis, the block is reprocessed on restart. |
| **Zero false positives** | Bloom filter match is always confirmed against PostgreSQL `wallet_addresses` table. |
| **Consumer idempotency** | Dedup key: `txHash + toAddress + networkId`. All consumers must implement this. |
| **Finality-first** | Only finalized blocks are indexed. No reorg detection needed. |
| **Whitelist-only tokens** | Only configured stablecoin contracts are parsed. Unknown tokens are skipped. |

### Kafka Topics

Events are published to per-chain topics with the pattern `transfer.events.<networkId>`:

- Key: `toAddress` (ensures per-wallet ordering for balance crediting)
- Retention: 30 days
- Serialization: JSON (Jackson)
- Idempotent producer enabled

---

## :key: Key Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Finalized blocks only | No reorg detection | Zero financial risk |
| Bloom + DB confirm | Double-check pattern | Zero false positives for financial correctness |
| Kafka before Redis | At-least-once ordering | Never lose a transfer event |
| Per-chain topics | `transfer.events.<networkId>` | Chain-level fault isolation |
| Virtual Threads | Single global executor | Cheap concurrency; RPC rate limits are the real constraint |
| JDK HttpClient | No Spring WebClient | Maximum RPC throughput, zero Spring coupling |
| JSON-RPC batch | Configurable batch size (default 50) | 150 receipt calls -> 3 batches per block |
| Redis for progress | Hash + Sorted Set | Write-heavy workload; PostgreSQL for wallet addresses only |
| API key auth | `X-API-Key` header | Internal service-to-service; OAuth2 deferred |

For the full set of 21 architecture decisions, see [`docs/architecture-decisions.md`](docs/architecture-decisions.md).

---

## :books: Documentation

| Document | Description |
|----------|-------------|
| [Architecture Decisions](docs/architecture-decisions.md) | 21 ADRs covering all major design choices |
| [Project Specification](docs/project-spec.md) | Detailed project specification and requirements |
| [Payment Lifecycle](docs/payment-lifecycle.md) | Business context for deposit detection |
| [Indexer Explained Simply](docs/indexer-explained-simply.md) | Non-technical explanation of how the indexer works |

---

## :handshake: Contributing

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/your-feature`
3. Ensure all checks pass: `./gradlew build`
4. Commit with a descriptive message
5. Push and open a pull request

### Code Style

- Java 25 with `var` for local variables
- Hexagonal architecture (domain must not depend on infrastructure)
- `@Builder(toBuilder = true)` on all records
- BDDMockito (`given`/`then`) for all tests
- Spotless enforced at build time

---

## :page_facing_up: License

This project is licensed under the MIT License.

---

<div align="center">

Inspired by [fystack/multichain-indexer](https://github.com/fystack/multichain-indexer) — reimplemented in Java/Spring Boot with hexagonal architecture.

</div>
