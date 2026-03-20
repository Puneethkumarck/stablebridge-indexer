package com.stablebridge.indexer.infrastructure.chain.solana;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;

@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
record SolanaTokenBalance(
        int accountIndex,
        String mint,
        String owner,
        SolanaTokenAmount uiTokenAmount) {

    @Builder(toBuilder = true)
    @JsonIgnoreProperties(ignoreUnknown = true)
    record SolanaTokenAmount(
            String amount,
            int decimals,
            String uiAmountString) {}
}
