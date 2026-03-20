package com.stablebridge.indexer.infrastructure.chain.solana;

import com.stablebridge.indexer.domain.model.BlockResult;
import com.stablebridge.indexer.domain.model.ChainId;
import com.stablebridge.indexer.domain.model.IndexedBlock;
import com.stablebridge.indexer.domain.model.Transfer;
import com.stablebridge.indexer.domain.port.ChainIndexer;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.stream.Stream;

@Slf4j
class SolanaChainIndexer implements ChainIndexer {

    private final SolanaRpcClient rpcClient;
    private final ChainId chainId;
    private final boolean indexNativeTransfers;
    private final SolanaNativeTransferParser nativeTransferParser;
    private final SolanaSpITransferParser splTransferParser;

    SolanaChainIndexer(SolanaRpcClient rpcClient,
                       ChainId chainId,
                       boolean indexNativeTransfers,
                       SolanaNativeTransferParser nativeTransferParser,
                       SolanaSpITransferParser splTransferParser) {
        this.rpcClient = rpcClient;
        this.chainId = chainId;
        this.indexNativeTransfers = indexNativeTransfers;
        this.nativeTransferParser = nativeTransferParser;
        this.splTransferParser = splTransferParser;
    }

    @Override
    public BlockResult indexBlock(long blockNumber) {
        var block = rpcClient.getBlock(blockNumber);

        var splTransfers = splTransferParser.parseSplTransfers(block, blockNumber);
        var nativeTransfers = indexNativeTransfers
                ? nativeTransferParser.parseNativeTransfers(block, blockNumber)
                : List.<Transfer>of();

        var transfers = Stream.concat(splTransfers.stream(), nativeTransfers.stream()).toList();

        var transactionCount = block.transactions() != null ? block.transactions().size() : 0;

        var indexedBlock = IndexedBlock.builder()
                .blockNumber(blockNumber)
                .blockHash(block.blockhash())
                .parentHash(block.previousBlockhash())
                .timestamp(block.blockTimestamp())
                .chainId(chainId)
                .transactionCount(transactionCount)
                .build();

        log.debug("Indexed slot {} on {}: {} transfers", blockNumber, chainId, transfers.size());

        return BlockResult.builder()
                .indexedBlock(indexedBlock)
                .transfers(transfers)
                .build();
    }

    @Override
    public long getLatestFinalizedBlockNumber() {
        return rpcClient.getLatestSlot();
    }

    @Override
    public ChainId getChainId() {
        return chainId;
    }
}
