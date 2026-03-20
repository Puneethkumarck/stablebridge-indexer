package com.stablebridge.indexer.domain.port;

import com.stablebridge.indexer.domain.model.BlockResult;
import com.stablebridge.indexer.domain.model.ChainId;

/**
 * Port for chain-specific block indexing.
 *
 * <p>Each implementation handles a single blockchain (e.g., Ethereum, Base, Polygon)
 * and is responsible for fetching finalized blocks and parsing transfers from them.
 *
 * <p>Implementations must only return finalized blocks — never unfinalized chain tips.
 */
public interface ChainIndexer {

    /**
     * Fetches and parses a single finalized block, returning its metadata and matched transfers.
     *
     * @param blockNumber the block number to index
     * @return the block result containing indexed block metadata and parsed transfers
     */
    BlockResult indexBlock(long blockNumber);

    /**
     * Returns the latest finalized block number for this chain.
     *
     * <p>For chains using the {@code finalized} tag (e.g., Ethereum), this calls
     * {@code eth_getBlockByNumber("finalized")}. For chains using confirmation-based
     * finality (e.g., Base, Polygon), this returns {@code latest - minConfirmations}.
     *
     * @return the latest finalized block number
     */
    long getLatestFinalizedBlockNumber();

    /**
     * Identifies which chain this indexer handles.
     *
     * @return the chain identifier
     */
    ChainId getChainId();
}
