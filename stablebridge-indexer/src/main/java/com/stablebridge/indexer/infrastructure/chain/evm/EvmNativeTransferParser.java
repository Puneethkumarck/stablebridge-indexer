package com.stablebridge.indexer.infrastructure.chain.evm;

import com.stablebridge.indexer.domain.model.ChainId;
import com.stablebridge.indexer.domain.model.Transfer;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

@Slf4j
class EvmNativeTransferParser {

    private static final Map<ChainId, String> NATIVE_SYMBOLS = Map.of(
            ChainId.ETHEREUM, "ETH",
            ChainId.POLYGON, "MATIC",
            ChainId.ARBITRUM, "ETH",
            ChainId.OPTIMISM, "ETH",
            ChainId.BASE, "ETH",
            ChainId.AVALANCHE, "AVAX",
            ChainId.BSC, "BNB"
    );

    private static final int NATIVE_TRANSFER_LOG_INDEX = -1;

    private final ChainId chainId;
    private final int nativeDecimals;
    private final String nativeSymbol;

    EvmNativeTransferParser(ChainId chainId, int nativeDecimals) {
        this.chainId = chainId;
        this.nativeDecimals = nativeDecimals;
        this.nativeSymbol = NATIVE_SYMBOLS.getOrDefault(chainId, "UNKNOWN");
    }

    List<Transfer> parseNativeTransfers(EvmBlock block) {
        if (block.transactions() == null || block.transactions().isEmpty()) {
            return List.of();
        }

        var blockNumber = block.blockNumber();
        var blockHash = block.hash();
        var blockTimestamp = block.blockTimestamp();

        return block.transactions().stream()
                .filter(this::hasNonZeroValue)
                .filter(tx -> tx.to() != null)
                .map(tx -> {
                    var rawBigInt = hexToBigInteger(tx.value());
                    var rawAmount = rawBigInt.toString();
                    var amount = new BigDecimal(rawBigInt)
                            .divide(BigDecimal.TEN.pow(nativeDecimals), nativeDecimals, RoundingMode.HALF_UP);

                    return Transfer.builder()
                            .txHash(tx.hash())
                            .fromAddress(tx.from())
                            .toAddress(tx.to())
                            .rawAmount(rawAmount)
                            .amount(amount)
                            .decimals(nativeDecimals)
                            .tokenSymbol(nativeSymbol)
                            .tokenContractAddress(null)
                            .blockNumber(blockNumber)
                            .blockHash(blockHash)
                            .transactionIndex(tx.txIndex())
                            .logIndex(NATIVE_TRANSFER_LOG_INDEX)
                            .chainId(chainId)
                            .timestamp(blockTimestamp)
                            .nativeTransfer(true)
                            .build();
                })
                .toList();
    }

    private boolean hasNonZeroValue(EvmTransaction tx) {
        var value = tx.value();
        return value != null
                && !"0x0".equals(value)
                && !"0x".equals(value)
                && !"0x00".equals(value);
    }

    private static BigInteger hexToBigInteger(String hex) {
        var stripped = hex.startsWith("0x") || hex.startsWith("0X")
                ? hex.substring(2)
                : hex;
        return new BigInteger(stripped, 16);
    }
}
