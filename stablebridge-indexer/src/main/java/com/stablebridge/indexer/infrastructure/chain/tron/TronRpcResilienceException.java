package com.stablebridge.indexer.infrastructure.chain.tron;

class TronRpcResilienceException extends RuntimeException {

    private TronRpcResilienceException(String message) {
        super(message);
    }

    private TronRpcResilienceException(String message, Throwable cause) {
        super(message, cause);
    }

    static TronRpcResilienceException circuitBreakerOpen(String chainName) {
        return new TronRpcResilienceException(
                "Circuit breaker is OPEN for chain=%s — all TRON endpoints unavailable".formatted(chainName));
    }

    static TronRpcResilienceException rateLimited(String chainName) {
        return new TronRpcResilienceException(
                "Rate limiter rejected call for chain=%s — too many requests".formatted(chainName));
    }

    static TronRpcResilienceException retryExhausted(String chainName, Throwable cause) {
        return new TronRpcResilienceException(
                "All retry attempts exhausted for chain=%s".formatted(chainName), cause);
    }
}
