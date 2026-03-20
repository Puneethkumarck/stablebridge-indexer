package com.stablebridge.indexer.infrastructure.chain.evm;

import lombok.Builder;

@Builder(toBuilder = true)
public record EvmChainTokenConfig(
        String address,
        String symbol,
        int decimals
) {}
