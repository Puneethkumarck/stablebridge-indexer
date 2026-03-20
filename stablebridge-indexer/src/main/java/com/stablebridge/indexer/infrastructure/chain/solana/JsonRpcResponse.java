package com.stablebridge.indexer.infrastructure.chain.solana;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;

@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
record JsonRpcResponse<T>(
        String jsonrpc,
        long id,
        T result,
        JsonRpcError error) {}
