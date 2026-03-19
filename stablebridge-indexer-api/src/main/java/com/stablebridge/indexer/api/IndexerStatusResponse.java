package com.stablebridge.indexer.api;

public record IndexerStatusResponse(
        String chainId,
        String networkType,
        String workerState,
        Long lastProcessedBlock,
        Long latestFinalizedBlock,
        Long blocksBehind,
        boolean enabled) {}
