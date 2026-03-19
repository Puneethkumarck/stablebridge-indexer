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
    SOLANA_CHAIN(SOLANA),
    BITCOIN_CHAIN(BITCOIN);

    private final NetworkType networkType;

    ChainId(NetworkType networkType) {
        this.networkType = networkType;
    }

    public NetworkType networkType() {
        return networkType;
    }
}
