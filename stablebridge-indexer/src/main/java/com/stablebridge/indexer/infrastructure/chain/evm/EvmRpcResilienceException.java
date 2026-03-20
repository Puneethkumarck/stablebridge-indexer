package com.stablebridge.indexer.infrastructure.chain.evm;

class EvmRpcResilienceException extends RuntimeException {

    private EvmRpcResilienceException(String message) {
        super(message);
    }

    private EvmRpcResilienceException(String message, Throwable cause) {
        super(message, cause);
    }

    static EvmRpcResilienceException circuitBreakerOpen(String chainName) {
        return new EvmRpcResilienceException(
                "Circuit breaker is OPEN for chain=%s — all RPC endpoints unavailable".formatted(chainName));
    }

    static EvmRpcResilienceException rateLimited(String chainName) {
        return new EvmRpcResilienceException(
                "Rate limiter rejected call for chain=%s — too many requests".formatted(chainName));
    }

    static EvmRpcResilienceException retryExhausted(String chainName, Throwable cause) {
        return new EvmRpcResilienceException(
                "All retry attempts exhausted for chain=%s".formatted(chainName), cause);
    }
}
