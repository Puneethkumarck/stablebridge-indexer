package com.stablebridge.indexer.infrastructure.chain.tron;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;

import java.time.Instant;
import java.util.List;

@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
record TronTransactionInfo(
        String id,
        long blockNumber,
        long blockTimeStamp,
        Receipt receipt,
        List<TronEventLog> log) {

    boolean isSuccessful() {
        return receipt != null && "SUCCESS".equals(receipt.result());
    }

    Instant blockTimestamp() {
        return Instant.ofEpochMilli(blockTimeStamp);
    }

    @Builder(toBuilder = true)
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Receipt(String result) {}
}
