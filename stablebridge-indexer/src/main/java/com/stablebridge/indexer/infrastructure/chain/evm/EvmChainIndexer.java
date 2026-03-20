package com.stablebridge.indexer.infrastructure.chain.evm;

import com.stablebridge.indexer.domain.model.BlockResult;
import com.stablebridge.indexer.domain.model.ChainId;
import com.stablebridge.indexer.domain.model.IndexedBlock;
import com.stablebridge.indexer.domain.model.Transfer;
import com.stablebridge.indexer.domain.port.ChainIndexer;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
class EvmChainIndexer implements ChainIndexer {

    private final ResilientEvmRpcClient rpcClient;
    private final ChainId chainId;
    private final boolean useFinalizedTag;
    private final int minConfirmations;
    private final boolean indexNativeTransfers;
    private final EvmNativeTransferParser nativeTransferParser;
    private final EvmErc20TransferParser erc20TransferParser;

    EvmChainIndexer(ResilientEvmRpcClient rpcClient,
                    ChainId chainId,
                    boolean useFinalizedTag,
                    int minConfirmations,
                    boolean indexNativeTransfers,
                    EvmNativeTransferParser nativeTransferParser,
                    EvmErc20TransferParser erc20TransferParser) {
        this.rpcClient = rpcClient;
        this.chainId = chainId;
        this.useFinalizedTag = useFinalizedTag;
        this.minConfirmations = minConfirmations;
        this.indexNativeTransfers = indexNativeTransfers;
        this.nativeTransferParser = nativeTransferParser;
        this.erc20TransferParser = erc20TransferParser;
    }

    @Override
    public BlockResult indexBlock(long blockNumber) {
        var block = rpcClient.getBlockByNumber(blockNumber);
        var receipts = fetchReceipts(block, blockNumber);

        var erc20Transfers = erc20TransferParser.parseErc20Transfers(receipts, block);
        var nativeTransfers = indexNativeTransfers
                ? filterBySuccessfulReceipts(nativeTransferParser.parseNativeTransfers(block), receipts)
                : List.<Transfer>of();

        var transfers = Stream.concat(erc20Transfers.stream(), nativeTransfers.stream()).toList();

        var indexedBlock = IndexedBlock.builder()
                .blockNumber(block.blockNumber())
                .blockHash(block.hash())
                .parentHash(block.parentHash())
                .timestamp(block.blockTimestamp())
                .chainId(chainId)
                .transactionCount(block.transactions() != null ? block.transactions().size() : 0)
                .build();

        log.debug("Indexed block {} on {}: {} transfers", blockNumber, chainId, transfers.size());

        return BlockResult.builder()
                .indexedBlock(indexedBlock)
                .transfers(transfers)
                .build();
    }

    @Override
    public long getLatestFinalizedBlockNumber() {
        var latestBlockNumber = rpcClient.getLatestBlockNumber();

        if (useFinalizedTag) {
            return latestBlockNumber;
        }

        return latestBlockNumber - minConfirmations;
    }

    @Override
    public ChainId getChainId() {
        return chainId;
    }

    private List<EvmReceipt> fetchReceipts(EvmBlock block, long blockNumber) {
        if (rpcClient.supportsBlockReceipts()) {
            return rpcClient.getBlockReceipts(blockNumber);
        }

        var txHashes = block.transactions() != null
                ? block.transactions().stream().map(EvmTransaction::hash).toList()
                : List.<String>of();

        return rpcClient.getTransactionReceipts(txHashes);
    }

    private List<Transfer> filterBySuccessfulReceipts(List<Transfer> nativeTransfers,
                                                      List<EvmReceipt> receipts) {
        var successfulTxHashes = receipts.stream()
                .filter(EvmReceipt::isSuccessful)
                .map(EvmReceipt::transactionHash)
                .collect(Collectors.toSet());

        return nativeTransfers.stream()
                .filter(transfer -> successfulTxHashes.contains(transfer.txHash()))
                .toList();
    }
}
