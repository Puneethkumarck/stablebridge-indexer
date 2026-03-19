package com.stablebridge.indexer.domain.model;

import lombok.Builder;

import java.time.Instant;

@Builder
public record IndexedBlock(
        long blockNumber,
        String blockHash,
        String parentHash,
        Instant timestamp,
        ChainId chainId,
        int transactionCount) {}
