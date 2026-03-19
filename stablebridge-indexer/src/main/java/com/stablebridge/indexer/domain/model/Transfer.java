package com.stablebridge.indexer.domain.model;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;

@Builder
public record Transfer(
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
        ChainId chainId,
        Instant timestamp,
        boolean nativeTransfer) {}
