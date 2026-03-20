package com.stablebridge.indexer.infrastructure.chain.evm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static com.stablebridge.indexer.domain.model.ChainId.BASE;
import static com.stablebridge.indexer.domain.model.ChainId.ETHEREUM;
import static com.stablebridge.indexer.domain.model.ChainId.POLYGON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("EvmChainIndexerFactory")
class EvmChainIndexerFactoryTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("creates chain indexer with correct chain ID for ethereum_mainnet")
        void createsChainIndexerWithCorrectChainIdForEthereum() {
            // given
            var config = anEvmChainConfig("ethereum_mainnet", true, 0);

            // when
            var indexer = EvmChainIndexerFactory.create(config, OBJECT_MAPPER);

            // then
            assertThat(indexer.getChainId()).isEqualTo(ETHEREUM);
        }

        @Test
        @DisplayName("creates chain indexer with correct chain ID for polygon_mainnet")
        void createsChainIndexerWithCorrectChainIdForPolygon() {
            // given
            var config = anEvmChainConfig("polygon_mainnet", false, 128);

            // when
            var indexer = EvmChainIndexerFactory.create(config, OBJECT_MAPPER);

            // then
            assertThat(indexer.getChainId()).isEqualTo(POLYGON);
        }

        @Test
        @DisplayName("creates chain indexer with correct chain ID for base_mainnet")
        void createsChainIndexerWithCorrectChainIdForBase() {
            // given
            var config = anEvmChainConfig("base_mainnet", false, 10);

            // when
            var indexer = EvmChainIndexerFactory.create(config, OBJECT_MAPPER);

            // then
            assertThat(indexer.getChainId()).isEqualTo(BASE);
        }

        @Test
        @DisplayName("creates chain indexer with multiple token contracts")
        void createsChainIndexerWithMultipleTokenContracts() {
            // given
            var tokenContracts = List.of(
                    EvmChainTokenConfig.builder().address("0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48")
                            .symbol("USDC").decimals(6).build(),
                    EvmChainTokenConfig.builder().address("0xdAC17F958D2ee523a2206206994597C13D831ec7")
                            .symbol("USDT").decimals(6).build(),
                    EvmChainTokenConfig.builder().address("0x6B175474E89094C44Da98b954EedeAC495271d0F")
                            .symbol("DAI").decimals(18).build()
            );
            var config = anEvmChainConfigWithTokens("ethereum_mainnet", tokenContracts);

            // when
            var indexer = EvmChainIndexerFactory.create(config, OBJECT_MAPPER);

            // then
            assertThat(indexer).isNotNull();
            assertThat(indexer.getChainId()).isEqualTo(ETHEREUM);
        }
    }

    @Nested
    @DisplayName("resolveChainId")
    class ResolveChainId {

        @Test
        @DisplayName("resolves all supported EVM network IDs")
        void resolvesAllSupportedEvmNetworkIds() {
            // given / when / then
            assertThat(EvmChainIndexerFactory.resolveChainId("ethereum_mainnet")).isEqualTo(ETHEREUM);
            assertThat(EvmChainIndexerFactory.resolveChainId("polygon_mainnet")).isEqualTo(POLYGON);
            assertThat(EvmChainIndexerFactory.resolveChainId("base_mainnet")).isEqualTo(BASE);
        }

        @Test
        @DisplayName("throws for unknown network ID")
        void throwsForUnknownNetworkId() {
            // given
            var unknownNetworkId = "unknown_chain";

            // when / then
            assertThatThrownBy(() -> EvmChainIndexerFactory.resolveChainId(unknownNetworkId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unknown EVM network ID: 'unknown_chain'");
        }
    }

    // -- Factory methods for test data (package-private ACL types, cannot be in testFixtures) --

    private static EvmChainConfig anEvmChainConfig(String networkId,
                                                     boolean useFinalizedTag,
                                                     int minConfirmations) {
        return EvmChainConfig.builder()
                .networkId(networkId)
                .rpcUrl("https://rpc.example.com/v2/demo")
                .rpcBatchSize(50)
                .useBlockReceipts(false)
                .rpcTimeout(Duration.ofSeconds(10))
                .maxRetries(3)
                .rateLimitRps(25)
                .rateLimitBurst(50)
                .useFinalizedTag(useFinalizedTag)
                .minConfirmations(minConfirmations)
                .indexNativeTransfers(false)
                .nativeDecimals(18)
                .tokenContracts(List.of(
                        EvmChainTokenConfig.builder()
                                .address("0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48")
                                .symbol("USDC")
                                .decimals(6)
                                .build()))
                .build();
    }

    private static EvmChainConfig anEvmChainConfigWithTokens(String networkId,
                                                               List<EvmChainTokenConfig> tokenContracts) {
        return EvmChainConfig.builder()
                .networkId(networkId)
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
                .tokenContracts(tokenContracts)
                .build();
    }
}
