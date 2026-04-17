package com.stablebridge.indexer.infrastructure.chain.tron;

import com.stablebridge.indexer.domain.model.ChainId;
import com.stablebridge.indexer.domain.model.Transfer;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

@Slf4j
class TronNativeTransferParser {

    private static final String NATIVE_SYMBOL = "TRX";
    private static final int TRX_DECIMALS = 6;
    private static final int NATIVE_TRANSFER_LOG_INDEX = -1;
    private static final BigDecimal SUN_PER_TRX = BigDecimal.TEN.pow(TRX_DECIMALS);

    private final ChainId chainId;

    TronNativeTransferParser(ChainId chainId) {
        this.chainId = chainId;
    }

    List<Transfer> parseNativeTransfers(TronBlock block) {
        if (block.transactions() == null || block.transactions().isEmpty()) {
            return List.of();
        }

        var txs = block.transactions();
        var blockNumber = block.blockNumber();
        var blockHash = block.blockID();
        var blockTimestamp = block.blockTimestamp();

        return IntStream.range(0, txs.size())
                .filter(i -> txs.get(i).isNativeTransfer())
                .filter(i -> txs.get(i).ownerAddress() != null)
                .filter(i -> txs.get(i).toAddress() != null)
                .filter(i -> txs.get(i).amount() != 0)
                .mapToObj(i -> buildTransfer(txs.get(i), i, blockNumber, blockHash, blockTimestamp))
                .toList();
    }

    private Transfer buildTransfer(
            TronTransaction tx,
            int txIndex,
            long blockNumber,
            String blockHash,
            Instant blockTimestamp) {
        var amountSun = tx.amount();
        var rawAmount = String.valueOf(amountSun);
        var amount = BigDecimal.valueOf(amountSun).divide(SUN_PER_TRX, TRX_DECIMALS, RoundingMode.HALF_UP);

        return Transfer.builder()
                .txHash(tx.txID())
                .fromAddress(TronAddressConverter.base58ToHex(tx.ownerAddress()))
                .toAddress(TronAddressConverter.base58ToHex(tx.toAddress()))
                .rawAmount(rawAmount)
                .amount(amount)
                .decimals(TRX_DECIMALS)
                .tokenSymbol(NATIVE_SYMBOL)
                .tokenContractAddress(null)
                .blockNumber(blockNumber)
                .blockHash(blockHash)
                .transactionIndex(txIndex)
                .logIndex(NATIVE_TRANSFER_LOG_INDEX)
                .chainId(chainId)
                .timestamp(blockTimestamp)
                .nativeTransfer(true)
                .build();
    }
}
