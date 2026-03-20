package com.stablebridge.indexer.infrastructure.chain.solana;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;

import java.util.List;

@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
record SolanaTransactionBody(
        SolanaTransactionMessage message,
        List<String> signatures) {}
