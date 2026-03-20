package com.stablebridge.indexer.infrastructure.chain.evm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;

import java.util.List;

@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
record EvmReceipt(
        String transactionHash,
        String transactionIndex,
        String blockNumber,
        String blockHash,
        String from,
        String to,
        String status,
        List<EvmLog> logs) {

    int txIndex() {
        return (int) HexUtils.hexToLong(transactionIndex);
    }

    boolean isSuccessful() {
        return "0x1".equals(status);
    }
}
