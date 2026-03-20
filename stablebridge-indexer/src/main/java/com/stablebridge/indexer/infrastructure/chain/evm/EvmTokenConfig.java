package com.stablebridge.indexer.infrastructure.chain.evm;

import lombok.Builder;

@Builder(toBuilder = true)
record EvmTokenConfig(
        String address,
        String symbol,
        int decimals) {
}
