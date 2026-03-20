package com.stablebridge.indexer.infrastructure.chain.evm;

class EvmRpcException extends RuntimeException {

    private EvmRpcException(String message) {
        super(message);
    }

    private EvmRpcException(String message, Throwable cause) {
        super(message, cause);
    }

    static EvmRpcException httpError(String method, int statusCode) {
        return new EvmRpcException(
                "RPC HTTP error: method=%s, statusCode=%d".formatted(method, statusCode));
    }

    static EvmRpcException rpcError(String method, int code, String message) {
        return new EvmRpcException(
                "RPC call failed: method=%s, code=%d, error=%s".formatted(method, code, message));
    }

    static EvmRpcException networkError(String method, Throwable cause) {
        return new EvmRpcException(
                "RPC network error: method=%s, error=%s".formatted(method, cause.getMessage()), cause);
    }

    static EvmRpcException parseError(String method, Throwable cause) {
        return new EvmRpcException(
                "RPC response parse error: method=%s, error=%s".formatted(method, cause.getMessage()), cause);
    }

    static EvmRpcException allUrlsUnhealthy(int totalUrls) {
        return new EvmRpcException(
                "All %d RPC URLs are unhealthy and not eligible for health probe".formatted(totalUrls));
    }
}
