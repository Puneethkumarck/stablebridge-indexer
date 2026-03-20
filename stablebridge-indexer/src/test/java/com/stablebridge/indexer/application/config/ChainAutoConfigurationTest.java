package com.stablebridge.indexer.application.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stablebridge.indexer.application.properties.ChainProperties;
import com.stablebridge.indexer.application.properties.ConfirmationStrategy;
import com.stablebridge.indexer.application.properties.IndexerProperties;
import com.stablebridge.indexer.application.properties.RpcProperties;
import com.stablebridge.indexer.application.properties.TokenContractProperties;
import com.stablebridge.indexer.infrastructure.chain.evm.EvmChainConfig;
import com.stablebridge.indexer.infrastructure.chain.evm.EvmChainTokenConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.stablebridge.indexer.domain.model.ChainId.BASE;
import static com.stablebridge.indexer.domain.model.ChainId.ETHEREUM;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ChainAutoConfiguration")
class ChainAutoConfigurationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
            var indexers = configuration.chainIndexers(indexerProperties, OBJECT_MAPPER);

            // then
            assertThat(indexers).hasSize(2);
            assertThat(indexers.stream().map(i -> i.getChainId()).toList())
                    .containsExactly(ETHEREUM, BASE);
        }

        @Test
        @DisplayName("skips non-EVM chain types")
        void skipsNonEvmChainTypes() {
            // given
            var chains = new LinkedHashMap<String, ChainProperties>();
            chains.put("ethereum_mainnet", anEvmChainProperties(true, ConfirmationStrategy.FINALIZED, 0));
            chains.put("solana_mainnet", aSolanaChainProperties());

            var indexerProperties = new IndexerProperties(chains, null, null);

            // when
            var indexers = configuration.chainIndexers(indexerProperties, OBJECT_MAPPER);

            // then
            assertThat(indexers).hasSize(1);
            assertThat(indexers.getFirst().getChainId()).isEqualTo(ETHEREUM);
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
            var indexers = configuration.chainIndexers(indexerProperties, OBJECT_MAPPER);

            // then
            assertThat(indexers).isEmpty();
        }

        @Test
        @DisplayName("returns empty list when no chains are configured")
        void returnsEmptyListWhenNoChainsConfigured() {
            // given
            var indexerProperties = new IndexerProperties(Map.of(), null, null);

            // when
            var indexers = configuration.chainIndexers(indexerProperties, OBJECT_MAPPER);

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
                List.of()
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
                50
        );
    }
}
