package com.stablebridge.indexer.infrastructure.chain.solana;

import lombok.Builder;

@Builder(toBuilder = true)
record SolanaTokenConfig(
        String mintAddress,
        String symbol,
        int decimals) {}
