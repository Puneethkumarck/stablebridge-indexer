package com.stablebridge.indexer.infrastructure.chain.evm;

import lombok.Builder;

import java.util.List;

@Builder(toBuilder = true)
record JsonRpcRequest(
        String jsonrpc,
        String method,
        List<Object> params,
        long id) {

    static JsonRpcRequest of(String method, List<Object> params, long id) {
        return new JsonRpcRequest("2.0", method, params, id);
    }
}
