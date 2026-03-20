package com.stablebridge.indexer.infrastructure.chain.bitcoin;

class BitcoinRpcException extends RuntimeException {

    private BitcoinRpcException(String message) {
        super(message);
    }

    private BitcoinRpcException(String message, Throwable cause) {
        super(message, cause);
    }

    static BitcoinRpcException httpError(String method, int statusCode) {
        return new BitcoinRpcException(
                "RPC HTTP error: method=%s, statusCode=%d".formatted(method, statusCode));
    }

    static BitcoinRpcException rpcError(String method, int code, String message) {
        return new BitcoinRpcException(
                "RPC call failed: method=%s, code=%d, error=%s".formatted(method, code, message));
    }

    static BitcoinRpcException networkError(String method, Throwable cause) {
        return new BitcoinRpcException(
                "RPC network error: method=%s, error=%s".formatted(method, cause.getMessage()), cause);
    }

    static BitcoinRpcException parseError(String method, Throwable cause) {
        return new BitcoinRpcException(
                "RPC response parse error: method=%s, error=%s".formatted(method, cause.getMessage()), cause);
    }
}
