package com.stablebridge.indexer.infrastructure.chain.evm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;

import java.util.List;

@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
record EvmLog(
        String address,
        List<String> topics,
        String data,
        String logIndex,
        String transactionIndex,
        String transactionHash,
        String blockNumber,
        String blockHash) {

    int logIdx() {
        return (int) HexUtils.hexToLong(logIndex);
    }

    int txIndex() {
        return (int) HexUtils.hexToLong(transactionIndex);
    }
}
