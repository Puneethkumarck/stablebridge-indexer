package com.stablebridge.indexer.domain.port;

import com.stablebridge.indexer.domain.model.ChainId;

import java.util.OptionalLong;
import java.util.Set;

/**
 * Port for tracking block indexing progress per chain.
 *
 * <p>Progress is stored in Redis (Hash for latest block, Sorted Set for failed blocks).
 * If Redis is unavailable on restart, workers simply re-index from the last known position —
 * all downstream processing is idempotent.
 *
 * <p>Progress must only be saved <em>after</em> transfer events have been published to Kafka
 * to maintain the at-least-once delivery guarantee.
 */
public interface BlockProgressStore {

    /**
     * Returns the last successfully processed block number for the given chain.
     *
     * @param chainId the chain to query
     * @return the last processed block number, or empty if no progress has been recorded
     */
    OptionalLong getLastProcessedBlock(ChainId chainId);

    /**
     * Saves the last successfully processed block number for the given chain.
     *
     * <p>This must only be called <em>after</em> all transfer events from the block
     * have been published to Kafka.
     *
     * @param chainId     the chain to update
     * @param blockNumber the block number that was successfully processed
     */
    void saveLastProcessedBlock(ChainId chainId, long blockNumber);

    /**
     * Records a block that failed processing, so it can be retried by the rescan worker.
     *
     * @param chainId     the chain where the failure occurred
     * @param blockNumber the block number that failed
     */
    void addFailedBlock(ChainId chainId, long blockNumber);

    /**
     * Returns all failed block numbers for the given chain, for retry by the rescan worker.
     *
     * @param chainId the chain to query
     * @return the set of failed block numbers (may be empty)
     */
    Set<Long> getFailedBlocks(ChainId chainId);

    /**
     * Removes a block from the failed set after it has been successfully retried.
     *
     * @param chainId     the chain to update
     * @param blockNumber the block number to remove from the failed set
     */
    void removeFailedBlock(ChainId chainId, long blockNumber);
}
