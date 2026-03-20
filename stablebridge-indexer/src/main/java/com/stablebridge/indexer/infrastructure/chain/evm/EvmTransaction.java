package com.stablebridge.indexer.infrastructure.chain.evm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;

@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
record EvmTransaction(
        String hash,
        String from,
        String to,
        String value,
        String input,
        String blockNumber,
        String transactionIndex,
        String blockHash) {

    int txIndex() {
        return (int) HexUtils.hexToLong(transactionIndex);
    }
}
