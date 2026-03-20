package com.stablebridge.indexer.infrastructure.chain.solana;

import lombok.Builder;

@Builder(toBuilder = true)
public record SolanaChainTokenConfig(
        String mintAddress,
        String symbol,
        int decimals) {}
