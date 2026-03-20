package com.stablebridge.indexer.infrastructure.chain.bitcoin;

import com.stablebridge.indexer.domain.model.BlockResult;
import com.stablebridge.indexer.domain.model.ChainId;
import com.stablebridge.indexer.domain.model.IndexedBlock;
import com.stablebridge.indexer.domain.port.ChainIndexer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class BitcoinChainIndexer implements ChainIndexer {

    private final BitcoinRpcClient rpcClient;
    private final ChainId chainId;
    private final BitcoinTransferParser transferParser;
    private final BitcoinConfirmationTracker confirmationTracker;

    BitcoinChainIndexer(BitcoinRpcClient rpcClient,
                        ChainId chainId,
                        BitcoinTransferParser transferParser,
                        BitcoinConfirmationTracker confirmationTracker) {
        this.rpcClient = rpcClient;
        this.chainId = chainId;
        this.transferParser = transferParser;
        this.confirmationTracker = confirmationTracker;
    }

    @Override
    public BlockResult indexBlock(long blockNumber) {
        var hash = rpcClient.getBlockHash(blockNumber);
        var block = rpcClient.getBlock(hash);
        var transfers = transferParser.parseTransfers(block);

        var indexedBlock = IndexedBlock.builder()
                .blockNumber(block.height())
                .blockHash(block.hash())
                .parentHash(block.previousblockhash())
                .timestamp(block.blockTimestamp())
                .chainId(chainId)
                .transactionCount(block.tx() != null ? block.tx().size() : 0)
                .build();

        log.debug("Indexed block {} on {}: {} transfers", blockNumber, chainId, transfers.size());

        return BlockResult.builder()
                .indexedBlock(indexedBlock)
                .transfers(transfers)
                .build();
    }

    @Override
    public long getLatestFinalizedBlockNumber() {
        return confirmationTracker.getLatestConfirmedBlockNumber();
    }

    @Override
    public ChainId getChainId() {
        return chainId;
    }
}
