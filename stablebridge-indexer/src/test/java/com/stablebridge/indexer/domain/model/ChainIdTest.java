package com.stablebridge.indexer.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ChainId")
class ChainIdTest {

    @ParameterizedTest
    @EnumSource(ChainId.class)
    @DisplayName("all chain IDs have a non-null network type")
    void allChainIdsHaveNonNullNetworkType(ChainId chainId) {
        // given — the enum constant

        // when
        var networkType = chainId.networkType();

        // then
        assertThat(networkType).isNotNull();
    }

    @ParameterizedTest
    @CsvSource({
            "ETHEREUM, EVM",
            "POLYGON, EVM",
            "ARBITRUM, EVM",
            "OPTIMISM, EVM",
            "BASE, EVM",
            "AVALANCHE, EVM",
            "BSC, EVM",
            "SEPOLIA, EVM",
            "BASE_SEPOLIA, EVM",
            "SOLANA_CHAIN, SOLANA",
            "SOLANA_DEVNET, SOLANA",
            "BITCOIN_CHAIN, BITCOIN",
            "BITCOIN_TESTNET, BITCOIN",
            "TRON_CHAIN, TRON",
            "TRON_SHASTA, TRON",
            "APTOS_CHAIN, APTOS",
            "APTOS_DEVNET, APTOS",
            "SUI_CHAIN, SUI",
            "SUI_DEVNET, SUI",
            "NOBLE, COSMOS",
            "OSMOSIS, COSMOS",
            "COSMOS_HUB, COSMOS",
            "TON_CHAIN, TON",
            "TON_TESTNET, TON"
    })
    @DisplayName("maps chain ID to correct network type")
    void mapsChainIdToCorrectNetworkType(ChainId chainId, NetworkType expectedNetworkType) {
        // given — the enum constant and expected network type

        // when
        var actualNetworkType = chainId.networkType();

        // then
        assertThat(actualNetworkType).isEqualTo(expectedNetworkType);
    }

    @Test
    @DisplayName("total chain ID count matches expected value")
    void totalChainIdCountMatchesExpected() {
        // given
        var expectedCount = 24;

        // when
        var actualCount = ChainId.values().length;

        // then
        assertThat(actualCount).isEqualTo(expectedCount);
    }
}
