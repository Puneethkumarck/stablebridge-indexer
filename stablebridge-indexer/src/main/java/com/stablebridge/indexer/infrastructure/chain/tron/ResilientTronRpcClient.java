package com.stablebridge.indexer.infrastructure.chain.tron;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

import static io.github.resilience4j.circuitbreaker.CircuitBreaker.State.OPEN;

@Slf4j
class ResilientTronRpcClient {

    private static final int SLIDING_WINDOW_SIZE = 10;
    private static final int FAILURE_RATE_THRESHOLD = 50;
    private static final Duration WAIT_DURATION_IN_OPEN_STATE = Duration.ofSeconds(30);
    private static final int PERMITTED_CALLS_IN_HALF_OPEN = 3;
    private static final Duration RETRY_WAIT_DURATION = Duration.ofSeconds(1);
    private static final double RETRY_EXPONENTIAL_BACKOFF_MULTIPLIER = 2.0;
    private static final Duration RATE_LIMITER_TIMEOUT = Duration.ofSeconds(5);

    private final TronRpcClient delegate;
    private final String chainName;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final RateLimiter rateLimiter;
    private final MeterRegistry meterRegistry;

    ResilientTronRpcClient(TronRpcClient delegate, String chainName, int maxRetries,
                           int rateLimitRps, int rateLimitBurst, MeterRegistry meterRegistry) {
        this.delegate = delegate;
        this.chainName = chainName;
        this.circuitBreaker = createCircuitBreaker(chainName);
        this.retry = createRetry(chainName, maxRetries);
        this.rateLimiter = createRateLimiter(chainName, rateLimitRps, rateLimitBurst);
        this.meterRegistry = meterRegistry;
    }

    long getLatestSolidifiedBlockNumber() {
        return executeWithResilience(() -> delegate.getLatestSolidifiedBlockNumber(),
                "getLatestSolidifiedBlockNumber");
    }

    TronBlock getBlockByNumber(long blockNumber) {
        return executeWithResilience(() -> delegate.getBlockByNumber(blockNumber), "getBlockByNumber");
    }

    List<TronTransactionInfo> getTransactionInfoByBlockNum(long blockNumber) {
        return executeWithResilience(() -> delegate.getTransactionInfoByBlockNum(blockNumber),
                "getTransactionInfoByBlockNum");
    }

    List<TronBlock> getBlocksByRange(long startNum, long endNum) {
        return executeWithResilience(() -> delegate.getBlocksByRange(startNum, endNum), "getBlocksByRange");
    }

    boolean isCircuitBreakerOpen() {
        return circuitBreaker.getState() == OPEN;
    }

    CircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }

    RateLimiter getRateLimiter() {
        return rateLimiter;
    }

    private <T> T executeWithResilience(Supplier<T> supplier, String methodName) {
        var sample = Timer.start(meterRegistry);
        var decorated = decorateSupplier(supplier);
        try {
            return decorated.get();
        } catch (CallNotPermittedException e) {
            log.warn("Circuit breaker OPEN for chain={}, method={}", chainName, methodName);
            throw TronRpcResilienceException.circuitBreakerOpen(chainName);
        } catch (RequestNotPermitted e) {
            log.warn("Rate limiter rejected call for chain={}, method={}", chainName, methodName);
            throw TronRpcResilienceException.rateLimited(chainName);
        } finally {
            sample.stop(Timer.builder("indexer.rpc.latency")
                    .tag("chain", chainName)
                    .tag("method", methodName)
                    .register(meterRegistry));
        }
    }

    private <T> Supplier<T> decorateSupplier(Supplier<T> supplier) {
        var decorated = CircuitBreaker.decorateSupplier(circuitBreaker, supplier);
        decorated = Retry.decorateSupplier(retry, decorated);
        return RateLimiter.decorateSupplier(rateLimiter, decorated);
    }

    private static CircuitBreaker createCircuitBreaker(String chainName) {
        var config = CircuitBreakerConfig.custom()
                .slidingWindowSize(SLIDING_WINDOW_SIZE)
                .failureRateThreshold(FAILURE_RATE_THRESHOLD)
                .waitDurationInOpenState(WAIT_DURATION_IN_OPEN_STATE)
                .permittedNumberOfCallsInHalfOpenState(PERMITTED_CALLS_IN_HALF_OPEN)
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .recordExceptions(TronRpcException.class)
                .build();
        return CircuitBreaker.of("tron-rpc-%s".formatted(chainName), config);
    }

    private static Retry createRetry(String chainName, int maxRetries) {
        var config = RetryConfig.custom()
                .maxAttempts(maxRetries)
                .intervalFunction(IntervalFunction.ofExponentialBackoff(
                        RETRY_WAIT_DURATION, RETRY_EXPONENTIAL_BACKOFF_MULTIPLIER))
                .retryExceptions(TronRpcException.class)
                .ignoreExceptions(TronRpcResilienceException.class)
                .build();
        return Retry.of("tron-rpc-%s".formatted(chainName), config);
    }

    private static RateLimiter createRateLimiter(String chainName, int rateLimitRps, int rateLimitBurst) {
        var config = RateLimiterConfig.custom()
                .limitForPeriod(rateLimitRps)
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(RATE_LIMITER_TIMEOUT)
                .build();
        return RateLimiter.of("tron-rpc-%s".formatted(chainName), config);
    }
}
