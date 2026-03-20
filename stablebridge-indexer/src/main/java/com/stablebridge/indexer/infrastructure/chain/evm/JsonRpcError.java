package com.stablebridge.indexer.infrastructure.chain.evm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;

@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
record JsonRpcError(
        int code,
        String message,
        Object data) {}
