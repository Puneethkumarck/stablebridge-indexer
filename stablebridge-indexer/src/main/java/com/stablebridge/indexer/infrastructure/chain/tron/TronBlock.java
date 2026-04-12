package com.stablebridge.indexer.infrastructure.chain.tron;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;

import java.time.Instant;
import java.util.List;

@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
record TronBlock(
        String blockID,
        BlockHeader block_header,
        List<TronTransaction> transactions) {

    long blockNumber() {
        return block_header != null && block_header.raw_data() != null
                ? block_header.raw_data().number() : 0;
    }

    Instant blockTimestamp() {
        return block_header != null && block_header.raw_data() != null
                ? Instant.ofEpochMilli(block_header.raw_data().timestamp()) : Instant.EPOCH;
    }

    String parentHash() {
        return block_header != null && block_header.raw_data() != null
                ? block_header.raw_data().parentHash() : "";
    }

    @Builder(toBuilder = true)
    @JsonIgnoreProperties(ignoreUnknown = true)
    record BlockHeader(RawData raw_data) {}

    @Builder(toBuilder = true)
    @JsonIgnoreProperties(ignoreUnknown = true)
    record RawData(long number, long timestamp, String parentHash) {}
}
