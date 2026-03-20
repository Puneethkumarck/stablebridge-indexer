package com.stablebridge.indexer.infrastructure.chain.bitcoin;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class BitcoinConfirmationTracker {

    private final BitcoinRpcClient rpcClient;
    private final int minConfirmations;

    BitcoinConfirmationTracker(BitcoinRpcClient rpcClient, int minConfirmations) {
        this.rpcClient = rpcClient;
        this.minConfirmations = minConfirmations;
    }

    long getLatestConfirmedBlockNumber() {
        var blockCount = rpcClient.getBlockCount();
        var confirmed = blockCount - minConfirmations;
        log.debug("Bitcoin latest confirmed block: blockCount={}, minConfirmations={}, confirmed={}",
                blockCount, minConfirmations, confirmed);
        return Math.max(0, confirmed);
    }

    boolean hasEnoughConfirmations(long blockHeight) {
        var blockCount = rpcClient.getBlockCount();
        var confirmations = blockCount - blockHeight;
        return confirmations >= minConfirmations;
    }

    int getMinConfirmations() {
        return minConfirmations;
    }
}
