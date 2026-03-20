package com.stablebridge.indexer.infrastructure.chain.bitcoin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;

import java.util.Optional;

@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
record BtcVin(
        String txid,
        int vout,
        BtcScriptSig scriptSig,
        String coinbase,
        BtcVout prevout) {

    boolean isCoinbase() {
        return coinbase != null || txid == null;
    }

    Optional<String> extractFirstAddress() {
        if (prevout == null) {
            return Optional.empty();
        }
        var addresses = prevout.extractAddresses();
        return addresses.isEmpty() ? Optional.empty() : Optional.of(addresses.getFirst());
    }
}
