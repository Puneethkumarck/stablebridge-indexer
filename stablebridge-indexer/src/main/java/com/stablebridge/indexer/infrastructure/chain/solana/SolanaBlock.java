package com.stablebridge.indexer.infrastructure.chain.solana;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;

import java.time.Instant;
import java.util.List;

@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
record SolanaBlock(
        long parentSlot,
        String blockhash,
        String previousBlockhash,
        Long blockTime,
        List<SolanaTransaction> transactions) {

    Instant blockTimestamp() {
        return blockTime != null ? Instant.ofEpochSecond(blockTime) : null;
    }
}
