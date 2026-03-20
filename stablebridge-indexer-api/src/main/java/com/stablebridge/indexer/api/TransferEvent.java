package com.stablebridge.indexer.api;

import java.math.BigDecimal;
import java.time.Instant;

public record TransferEvent(
        String txHash,
        String fromAddress,
        String toAddress,
        String rawAmount,
        BigDecimal amount,
        int decimals,
        String tokenSymbol,
        String tokenContractAddress,
        long blockNumber,
        String blockHash,
        int transactionIndex,
        int logIndex,
        String chainId,
        String networkType,
        Instant timestamp,
        boolean nativeTransfer,
        Instant detectedAt) {}
