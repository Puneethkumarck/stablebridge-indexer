package com.stablebridge.indexer.domain.model;

import static com.stablebridge.indexer.domain.model.NetworkType.BITCOIN;
import static com.stablebridge.indexer.domain.model.NetworkType.EVM;
import static com.stablebridge.indexer.domain.model.NetworkType.SOLANA;

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
    BITCOIN_TESTNET(BITCOIN);

    private final NetworkType networkType;

    ChainId(NetworkType networkType) {
        this.networkType = networkType;
    }

    public NetworkType networkType() {
        return networkType;
    }
}
