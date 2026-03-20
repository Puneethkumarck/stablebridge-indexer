package com.stablebridge.indexer.infrastructure.chain.bitcoin;

import lombok.Builder;

import java.util.List;

@Builder(toBuilder = true)
record JsonRpcRequest(
        String jsonrpc,
        String method,
        List<Object> params,
        long id) {

    static JsonRpcRequest of(String method, List<Object> params, long id) {
        return new JsonRpcRequest("1.0", method, params, id);
    }
}
