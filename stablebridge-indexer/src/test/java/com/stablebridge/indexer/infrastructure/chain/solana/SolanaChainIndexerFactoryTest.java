package com.stablebridge.indexer.infrastructure.chain.solana;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static com.stablebridge.indexer.domain.model.ChainId.SOLANA_CHAIN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SolanaChainIndexerFactory")
class SolanaChainIndexerFactoryTest {

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("creates chain indexer with correct chain ID for solana_mainnet")
        void createsChainIndexerWithCorrectChainIdForSolanaMainnet() {
            // given
            var config = aSolanaChainConfig("solana_mainnet");

            // when
            var indexer = SolanaChainIndexerFactory.create(config);

            // then
            assertThat(indexer.getChainId()).isEqualTo(SOLANA_CHAIN);
        }

        @Test
        @DisplayName("creates chain indexer with multiple token contracts")
        void createsChainIndexerWithMultipleTokenContracts() {
            // given
            var tokenContracts = List.of(
                    SolanaChainTokenConfig.builder()
                            .mintAddress("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v")
                            .symbol("USDC")
                            .decimals(6)
                            .build(),
                    SolanaChainTokenConfig.builder()
                            .mintAddress("Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB")
                            .symbol("USDT")
                            .decimals(6)
                            .build()
            );
            var config = aSolanaChainConfigWithTokens("solana_mainnet", tokenContracts);

            // when
            var indexer = SolanaChainIndexerFactory.create(config);

            // then
            assertThat(indexer).isNotNull();
            assertThat(indexer.getChainId()).isEqualTo(SOLANA_CHAIN);
        }
    }

    @Nested
    @DisplayName("resolveChainId")
    class ResolveChainId {

        @Test
        @DisplayName("resolves solana_mainnet to SOLANA_CHAIN")
        void resolvesSolanaMainnetToSolanaChain() {
            // given / when / then
            assertThat(SolanaChainIndexerFactory.resolveChainId("solana_mainnet")).isEqualTo(SOLANA_CHAIN);
        }

        @Test
        @DisplayName("throws for unknown network ID")
        void throwsForUnknownNetworkId() {
            // given
            var unknownNetworkId = "unknown_chain";

            // when / then
            assertThatThrownBy(() -> SolanaChainIndexerFactory.resolveChainId(unknownNetworkId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unknown Solana network ID: 'unknown_chain'");
        }
    }

    // -- Factory methods for test data (package-private ACL types, cannot be in testFixtures) --

    private static SolanaChainConfig aSolanaChainConfig(String networkId) {
        return SolanaChainConfig.builder()
                .networkId(networkId)
                .rpcUrl("https://api.mainnet-beta.solana.com")
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
    }

    private static SolanaChainConfig aSolanaChainConfigWithTokens(String networkId,
                                                                    List<SolanaChainTokenConfig> tokenContracts) {
        return SolanaChainConfig.builder()
                .networkId(networkId)
                .rpcUrl("https://api.mainnet-beta.solana.com")
                .rpcTimeout(Duration.ofSeconds(10))
                .indexNativeTransfers(false)
                .nativeDecimals(9)
                .tokenContracts(tokenContracts)
                .build();
    }
}
