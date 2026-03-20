package com.stablebridge.indexer.infrastructure.chain.bitcoin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
record BtcVout(
        BigDecimal value,
        int n,
        BtcScriptPubKey scriptPubKey) {

    List<String> extractAddresses() {
        if (scriptPubKey == null) {
            return List.of();
        }
        return Stream.of(
                        Optional.ofNullable(scriptPubKey.address()).stream(),
                        Optional.ofNullable(scriptPubKey.addresses()).stream().flatMap(List::stream))
                .flatMap(s -> s)
                .distinct()
                .toList();
    }
}
