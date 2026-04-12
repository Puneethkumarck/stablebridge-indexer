package com.stablebridge.indexer.infrastructure.chain.tron;

class TronRpcException extends RuntimeException {

    private TronRpcException(String message) {
        super(message);
    }

    private TronRpcException(String message, Throwable cause) {
        super(message, cause);
    }

    static TronRpcException httpError(String endpoint, int statusCode) {
        return new TronRpcException(
                "TRON API HTTP error: endpoint=%s, statusCode=%d".formatted(endpoint, statusCode));
    }

    static TronRpcException rpcError(String endpoint, String message) {
        return new TronRpcException(
                "TRON API error: endpoint=%s, error=%s".formatted(endpoint, message));
    }

    static TronRpcException networkError(String endpoint, Throwable cause) {
        return new TronRpcException(
                "TRON API network error: endpoint=%s, error=%s".formatted(endpoint, cause.getMessage()), cause);
    }

    static TronRpcException parseError(String endpoint, Throwable cause) {
        return new TronRpcException(
                "TRON API response parse error: endpoint=%s, error=%s".formatted(endpoint, cause.getMessage()), cause);
    }
}
