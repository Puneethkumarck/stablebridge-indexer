package com.stablebridge.indexer.infrastructure.chain.solana;

class SolanaRpcException extends RuntimeException {

    private SolanaRpcException(String message) {
        super(message);
    }

    private SolanaRpcException(String message, Throwable cause) {
        super(message, cause);
    }

    static SolanaRpcException httpError(String method, int statusCode) {
        return new SolanaRpcException(
                "RPC HTTP error: method=%s, statusCode=%d".formatted(method, statusCode));
    }

    static SolanaRpcException rpcError(String method, int code, String message) {
        return new SolanaRpcException(
                "RPC call failed: method=%s, code=%d, error=%s".formatted(method, code, message));
    }

    static SolanaRpcException networkError(String method, Throwable cause) {
        return new SolanaRpcException(
                "RPC network error: method=%s, error=%s".formatted(method, cause.getMessage()), cause);
    }

    static SolanaRpcException parseError(String method, Throwable cause) {
        return new SolanaRpcException(
                "RPC response parse error: method=%s, error=%s".formatted(method, cause.getMessage()), cause);
    }
}
