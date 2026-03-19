package com.stablebridge.indexer.domain.model;

import lombok.Builder;

/**
 * Domain model representing the current status of a chain indexer.
 *
 * @param chainName           the network identifier (e.g., {@code "ethereum_mainnet"})
 * @param networkType         the network type (EVM, SOLANA, BITCOIN)
 * @param workerState         current worker state (RUNNING, PARKED, STOPPED)
 * @param lastProcessedBlock  last block that was successfully indexed, or {@code null} if no progress
 * @param latestFinalizedBlock latest finalized block from the chain, or {@code null} if not available
 * @param blocksBehind        number of blocks behind the tip, or {@code null} if not available
 * @param enabled             whether this chain is enabled for indexing
 */
@Builder
public record ChainStatus(
        String chainName,
        NetworkType networkType,
        WorkerState workerState,
        Long lastProcessedBlock,
        Long latestFinalizedBlock,
        Long blocksBehind,
        boolean enabled) {}
