package com.stablebridge.indexer.infrastructure.chain.bitcoin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;

import java.time.Instant;
import java.util.List;

@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
record BtcBlock(
        String hash,
        String previousblockhash,
        long height,
        long time,
        int confirmations,
        List<BtcTransaction> tx) {

    Instant blockTimestamp() {
        return Instant.ofEpochSecond(time);
    }
}
