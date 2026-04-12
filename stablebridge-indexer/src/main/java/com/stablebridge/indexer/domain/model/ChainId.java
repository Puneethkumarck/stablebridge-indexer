package com.stablebridge.indexer.domain.model;

import static com.stablebridge.indexer.domain.model.NetworkType.APTOS;
import static com.stablebridge.indexer.domain.model.NetworkType.BITCOIN;
import static com.stablebridge.indexer.domain.model.NetworkType.COSMOS;
import static com.stablebridge.indexer.domain.model.NetworkType.EVM;
import static com.stablebridge.indexer.domain.model.NetworkType.SOLANA;
import static com.stablebridge.indexer.domain.model.NetworkType.SUI;
import static com.stablebridge.indexer.domain.model.NetworkType.TON;
import static com.stablebridge.indexer.domain.model.NetworkType.TRON;

public enum ChainId {

    ETHEREUM(EVM),
    POLYGON(EVM),
    ARBITRUM(EVM),
    OPTIMISM(EVM),
    BASE(EVM),
    AVALANCHE(EVM),
    BSC(EVM),
    SEPOLIA(EVM),
    BASE_SEPOLIA(EVM),
    SOLANA_CHAIN(SOLANA),
    SOLANA_DEVNET(SOLANA),
    BITCOIN_CHAIN(BITCOIN),
    BITCOIN_TESTNET(BITCOIN),
    TRON_CHAIN(TRON),
    TRON_SHASTA(TRON),
    APTOS_CHAIN(APTOS),
    APTOS_DEVNET(APTOS),
    SUI_CHAIN(SUI),
    SUI_DEVNET(SUI),
    NOBLE(COSMOS),
    OSMOSIS(COSMOS),
    COSMOS_HUB(COSMOS),
    TON_CHAIN(TON),
    TON_TESTNET(TON);

    private final NetworkType networkType;

    ChainId(NetworkType networkType) {
        this.networkType = networkType;
    }

    public NetworkType networkType() {
        return networkType;
    }
}
