package com.stablebridge.indexer.application.config;

import com.stablebridge.indexer.application.properties.ChainProperties;
import com.stablebridge.indexer.application.properties.ConfirmationStrategy;
import com.stablebridge.indexer.application.properties.IndexerProperties;
import com.stablebridge.indexer.application.properties.RpcProperties;
import com.stablebridge.indexer.application.properties.TokenContractProperties;
import com.stablebridge.indexer.infrastructure.chain.bitcoin.BitcoinChainConfig;
import com.stablebridge.indexer.infrastructure.chain.evm.EvmChainConfig;
import com.stablebridge.indexer.infrastructure.chain.evm.EvmChainTokenConfig;
import com.stablebridge.indexer.infrastructure.chain.solana.SolanaChainConfig;
import com.stablebridge.indexer.infrastructure.chain.solana.SolanaChainTokenConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.stablebridge.indexer.domain.model.ChainId.BASE;
import static com.stablebridge.indexer.domain.model.ChainId.BITCOIN_CHAIN;
import static com.stablebridge.indexer.domain.model.ChainId.ETHEREUM;
import static com.stablebridge.indexer.domain.model.ChainId.SOLANA_CHAIN;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ChainAutoConfiguration")
class ChainAutoConfigurationTest {

    private static final MeterRegistry METER_REGISTRY = new SimpleMeterRegistry();

    private final ChainAutoConfiguration configuration = new ChainAutoConfiguration();

    @Nested
    @DisplayName("chainIndexers")
    class ChainIndexers {

        @Test
        @DisplayName("creates chain indexers for enabled EVM chains only, skipping disabled")
        void createsChainIndexersForEnabledEvmChainsOnly() {
            // given
            var chains = new LinkedHashMap<String, ChainProperties>();
            chains.put("ethereum_mainnet", anEvmChainProperties(true, ConfirmationStrategy.FINALIZED, 0));
            chains.put("base_mainnet", anEvmChainProperties(true, ConfirmationStrategy.CONFIRMATIONS, 10));
            chains.put("polygon_mainnet", anEvmChainProperties(false, ConfirmationStrategy.CONFIRMATIONS, 128));

            var indexerProperties = new IndexerProperties(chains, null, null);

            // when
            var indexers = configuration.chainIndexers(indexerProperties, METER_REGISTRY);

            // then
            assertThat(indexers).hasSize(2);
            assertThat(indexers.stream().map(i -> i.getChainId()).toList())
                    .containsExactly(ETHEREUM, BASE);
        }

        @Test
        @DisplayName("creates both EVM and Solana chain indexers")
        void createsBothEvmAndSolanaChainIndexers() {
            // given
            var chains = new LinkedHashMap<String, ChainProperties>();
            chains.put("ethereum_mainnet", anEvmChainProperties(true, ConfirmationStrategy.FINALIZED, 0));
            chains.put("solana_mainnet", aSolanaChainProperties());

            var indexerProperties = new IndexerProperties(chains, null, null);

            // when
            var indexers = configuration.chainIndexers(indexerProperties, METER_REGISTRY);

            // then
            assertThat(indexers).hasSize(2);
            assertThat(indexers.stream().map(i -> i.getChainId()).toList())
                    .containsExactly(ETHEREUM, SOLANA_CHAIN);
        }

        @Test
        @DisplayName("creates only Solana indexers when no EVM chains are configured")
        void createsOnlySolanaIndexersWhenNoEvmChainsConfigured() {
            // given
            var chains = new LinkedHashMap<String, ChainProperties>();
            chains.put("solana_mainnet", aSolanaChainProperties());

            var indexerProperties = new IndexerProperties(chains, null, null);

            // when
            var indexers = configuration.chainIndexers(indexerProperties, METER_REGISTRY);

            // then
            assertThat(indexers).hasSize(1);
            assertThat(indexers.getFirst().getChainId()).isEqualTo(SOLANA_CHAIN);
        }

        @Test
        @DisplayName("creates Bitcoin chain indexers for enabled Bitcoin chains")
        void createsBitcoinChainIndexersForEnabledBitcoinChains() {
            // given
            var chains = new LinkedHashMap<String, ChainProperties>();
            chains.put("bitcoin_mainnet", aBitcoinChainProperties(true, 6));

            var indexerProperties = new IndexerProperties(chains, null, null);

            // when
            var indexers = configuration.chainIndexers(indexerProperties, METER_REGISTRY);

            // then
            assertThat(indexers).hasSize(1);
            assertThat(indexers.getFirst().getChainId()).isEqualTo(BITCOIN_CHAIN);
        }

        @Test
        @DisplayName("combines EVM and Bitcoin chain indexers")
        void combinesEvmAndBitcoinChainIndexers() {
            // given
            var chains = new LinkedHashMap<String, ChainProperties>();
            chains.put("ethereum_mainnet", anEvmChainProperties(true, ConfirmationStrategy.FINALIZED, 0));
            chains.put("bitcoin_mainnet", aBitcoinChainProperties(true, 6));

            var indexerProperties = new IndexerProperties(chains, null, null);

            // when
            var indexers = configuration.chainIndexers(indexerProperties, METER_REGISTRY);

            // then
            assertThat(indexers).hasSize(2);
            assertThat(indexers.stream().map(i -> i.getChainId()).toList())
                    .containsExactly(ETHEREUM, BITCOIN_CHAIN);
        }

        @Test
        @DisplayName("skips disabled Bitcoin chains")
        void skipsDisabledBitcoinChains() {
            // given
            var chains = new LinkedHashMap<String, ChainProperties>();
            chains.put("bitcoin_mainnet", aBitcoinChainProperties(false, 6));

            var indexerProperties = new IndexerProperties(chains, null, null);

            // when
            var indexers = configuration.chainIndexers(indexerProperties, METER_REGISTRY);

            // then
            assertThat(indexers).isEmpty();
        }

        @Test
        @DisplayName("returns empty list when no chains are enabled")
        void returnsEmptyListWhenNoChainsEnabled() {
            // given
            var chains = Map.of(
                    "ethereum_mainnet", anEvmChainProperties(false, ConfirmationStrategy.FINALIZED, 0)
            );
            var indexerProperties = new IndexerProperties(chains, null, null);

            // when
            var indexers = configuration.chainIndexers(indexerProperties, METER_REGISTRY);

            // then
            assertThat(indexers).isEmpty();
        }

        @Test
        @DisplayName("returns empty list when no chains are configured")
        void returnsEmptyListWhenNoChainsConfigured() {
            // given
            var indexerProperties = new IndexerProperties(Map.of(), null, null);

            // when
            var indexers = configuration.chainIndexers(indexerProperties, METER_REGISTRY);

            // then
            assertThat(indexers).isEmpty();
        }
    }

    @Nested
    @DisplayName("toEvmChainConfig")
    class ToEvmChainConfig {

        @Test
        @DisplayName("maps chain properties to EVM chain config correctly")
        void mapsChainPropertiesToEvmChainConfigCorrectly() {
            // given
            var chainProperties = anEvmChainProperties(true, ConfirmationStrategy.FINALIZED, 0);

            var expected = EvmChainConfig.builder()
                    .networkId("ethereum_mainnet")
                    .rpcUrl("https://rpc.example.com/v2/demo")
                    .rpcBatchSize(50)
                    .useBlockReceipts(false)
                    .rpcTimeout(Duration.ofSeconds(10))
                    .maxRetries(3)
                    .rateLimitRps(25)
                    .rateLimitBurst(50)
                    .useFinalizedTag(true)
                    .minConfirmations(0)
                    .indexNativeTransfers(false)
                    .nativeDecimals(18)
                    .tokenContracts(List.of(
                            EvmChainTokenConfig.builder()
                                    .address("0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48")
                                    .symbol("USDC")
                                    .decimals(6)
                                    .build()))
                    .build();

            // when
            var result = ChainAutoConfiguration.toEvmChainConfig("ethereum_mainnet", chainProperties);

            // then
            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("maps CONFIRMATIONS strategy to useFinalizedTag=false")
        void mapsConfirmationsStrategyToUseFinalizedTagFalse() {
            // given
            var chainProperties = anEvmChainProperties(true, ConfirmationStrategy.CONFIRMATIONS, 10);

            var expected = EvmChainConfig.builder()
                    .networkId("base_mainnet")
                    .rpcUrl("https://rpc.example.com/v2/demo")
                    .rpcBatchSize(50)
                    .useBlockReceipts(false)
                    .rpcTimeout(Duration.ofSeconds(10))
                    .maxRetries(3)
                    .rateLimitRps(25)
                    .rateLimitBurst(50)
                    .useFinalizedTag(false)
                    .minConfirmations(10)
                    .indexNativeTransfers(false)
                    .nativeDecimals(18)
                    .tokenContracts(List.of(
                            EvmChainTokenConfig.builder()
                                    .address("0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48")
                                    .symbol("USDC")
                                    .decimals(6)
                                    .build()))
                    .build();

            // when
            var result = ChainAutoConfiguration.toEvmChainConfig("base_mainnet", chainProperties);

            // then
            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("toSolanaChainConfig")
    class ToSolanaChainConfig {

        @Test
        @DisplayName("maps chain properties to Solana chain config correctly")
        void mapsChainPropertiesToSolanaChainConfigCorrectly() {
            // given
            var chainProperties = aSolanaChainProperties();

            var expected = SolanaChainConfig.builder()
                    .networkId("solana_mainnet")
                    .rpcUrl("https://rpc.example.com/v2/demo")
                    .rpcTimeout(Duration.ofSeconds(10))
                    .indexNativeTransfers(false)
                    .nativeDecimals(9)
                    .tokenContracts(List.of(
                            SolanaChainTokenConfig.builder()
                                    .mintAddress("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v")
                                    .symbol("USDC")
                                    .decimals(6)
                                    .build()))
                    .build();

            // when
            var result = ChainAutoConfiguration.toSolanaChainConfig("solana_mainnet", chainProperties);

            // then
            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("toBitcoinChainConfig")
    class ToBitcoinChainConfig {

        @Test
        @DisplayName("maps chain properties to Bitcoin chain config correctly")
        void mapsChainPropertiesToBitcoinChainConfigCorrectly() {
            // given
            var chainProperties = aBitcoinChainProperties(true, 6);

            var expected = BitcoinChainConfig.builder()
                    .networkId("bitcoin_mainnet")
                    .rpcUrl("http://localhost:8332")
                    .rpcUsername("bitcoin")
                    .rpcPassword("secret")
                    .rpcTimeout(Duration.ofSeconds(10))
                    .minConfirmations(6)
                    .build();

            // when
            var result = ChainAutoConfiguration.toBitcoinChainConfig("bitcoin_mainnet", chainProperties);

            // then
            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }
    }

    // -- Factory methods for test chain properties --

    private static ChainProperties anEvmChainProperties(boolean enabled,
                                                          ConfirmationStrategy strategy,
                                                          int minConfirmations) {
        return new ChainProperties(
                enabled,
                "evm",
                strategy,
                minConfirmations,
                false,
                18,
                0L,
                Duration.ofSeconds(12),
                10,
                aRpcProperties(),
                List.of(new TokenContractProperties(
                        "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48", "USDC", 6))
        );
    }

    private static ChainProperties aBitcoinChainProperties(boolean enabled, int minConfirmations) {
        return new ChainProperties(
                enabled,
                "bitcoin",
                ConfirmationStrategy.CONFIRMATIONS,
                minConfirmations,
                true,
                8,
                0L,
                Duration.ofMinutes(10),
                1,
                aBitcoinRpcProperties(),
                List.of()
        );
    }

    private static ChainProperties aSolanaChainProperties() {
        return new ChainProperties(
                true,
                "solana",
                ConfirmationStrategy.FINALIZED,
                0,
                false,
                9,
                0L,
                Duration.ofSeconds(1),
                10,
                aRpcProperties(),
                List.of(new TokenContractProperties(
                        "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v", "USDC", 6))
        );
    }

    private static RpcProperties aRpcProperties() {
        return new RpcProperties(
                List.of("https://rpc.example.com/v2/demo"),
                50,
                false,
                Duration.ofSeconds(10),
                3,
                25,
                50,
                null,
                null
        );
    }

    private static RpcProperties aBitcoinRpcProperties() {
        return new RpcProperties(
                List.of("http://localhost:8332"),
                50,
                false,
                Duration.ofSeconds(10),
                3,
                25,
                50,
                "bitcoin",
                "secret"
        );
    }
}
