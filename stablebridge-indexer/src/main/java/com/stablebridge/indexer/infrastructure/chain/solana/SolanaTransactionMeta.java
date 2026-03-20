package com.stablebridge.indexer.infrastructure.chain.solana;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;

import java.util.List;

@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
record SolanaTransactionMeta(
        Object err,
        long fee,
        List<Long> preBalances,
        List<Long> postBalances,
        List<SolanaTokenBalance> preTokenBalances,
        List<SolanaTokenBalance> postTokenBalances) {}
