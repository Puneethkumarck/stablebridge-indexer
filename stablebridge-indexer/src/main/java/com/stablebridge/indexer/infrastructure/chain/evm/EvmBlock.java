package com.stablebridge.indexer.infrastructure.chain.evm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;

import java.time.Instant;
import java.util.List;

@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
record EvmBlock(
        String number,
        String hash,
        String parentHash,
        String timestamp,
        List<EvmTransaction> transactions) {

    long blockNumber() {
        return HexUtils.hexToLong(number);
    }

    Instant blockTimestamp() {
        return HexUtils.hexToInstant(timestamp);
    }
}
