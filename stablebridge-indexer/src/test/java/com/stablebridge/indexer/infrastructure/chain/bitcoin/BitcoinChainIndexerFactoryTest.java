package com.stablebridge.indexer.infrastructure.chain.bitcoin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static com.stablebridge.indexer.domain.model.ChainId.BITCOIN_CHAIN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("BitcoinChainIndexerFactory")
class BitcoinChainIndexerFactoryTest {

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("creates chain indexer with correct chain ID for bitcoin_mainnet")
        void createsChainIndexerWithCorrectChainIdForBitcoinMainnet() {
            // given
            var config = aBitcoinChainConfig("bitcoin_mainnet", 6);

            // when
            var indexer = BitcoinChainIndexerFactory.create(config);

            // then
            assertThat(indexer.getChainId()).isEqualTo(BITCOIN_CHAIN);
        }

        @Test
        @DisplayName("creates non-null chain indexer")
        void createsNonNullChainIndexer() {
            // given
            var config = aBitcoinChainConfig("bitcoin_mainnet", 3);

            // when
            var indexer = BitcoinChainIndexerFactory.create(config);

            // then
            assertThat(indexer).isNotNull();
        }
    }

    @Nested
    @DisplayName("resolveChainId")
    class ResolveChainId {

        @Test
        @DisplayName("resolves bitcoin_mainnet to BITCOIN_CHAIN")
        void resolvesBitcoinMainnet() {
            // given / when / then
            assertThat(BitcoinChainIndexerFactory.resolveChainId("bitcoin_mainnet"))
                    .isEqualTo(BITCOIN_CHAIN);
        }

        @Test
        @DisplayName("throws for unknown network ID")
        void throwsForUnknownNetworkId() {
            // given
            var unknownNetworkId = "bitcoin_regtest";

            // when / then
            assertThatThrownBy(() -> BitcoinChainIndexerFactory.resolveChainId(unknownNetworkId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unknown Bitcoin network ID: 'bitcoin_regtest'");
        }
    }

    // -- Factory methods for test data (package-private ACL types, cannot be in testFixtures) --

    private static BitcoinChainConfig aBitcoinChainConfig(String networkId, int minConfirmations) {
        return BitcoinChainConfig.builder()
                .networkId(networkId)
                .rpcUrl("http://localhost:8332")
                .rpcUsername("bitcoin")
                .rpcPassword("secret")
                .rpcTimeout(Duration.ofSeconds(10))
                .minConfirmations(minConfirmations)
                .build();
    }
}
