package com.stablebridge.indexer.api;

import lombok.Builder;

@Builder
public record IndexerStatusResponse(
        String chainId,
        String networkType,
        String workerState,
        Long lastProcessedBlock,
        Long latestFinalizedBlock,
        Long blocksBehind,
        boolean enabled) {}
