package com.stablebridge.indexer.domain.model;

import lombok.Builder;

@Builder
public record ChainStatus(
        String chainName,
        NetworkType networkType,
        WorkerState workerState,
        Long lastProcessedBlock,
        Long latestFinalizedBlock,
        Long blocksBehind,
        boolean enabled) {}
