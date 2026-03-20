package com.stablebridge.indexer.infrastructure.chain.evm;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
@DisplayName("ResilientEvmRpcClient")
class ResilientEvmRpcClientTest {

    private static final String CHAIN_NAME = "ethereum_mainnet";
    private static final int MAX_RETRIES = 3;
    private static final int RATE_LIMIT_RPS = 50;
    private static final int RATE_LIMIT_BURST = 100;
    private static final long BLOCK_NUMBER = 1_000_000L;
    private static final String TX_HASH = "0xtx_hash_1";

    @Mock
    private EvmRpcClient delegate;

    private ResilientEvmRpcClient resilientClient;

    @BeforeEach
    void setUp() {
        resilientClient = new ResilientEvmRpcClient(
                delegate, CHAIN_NAME, MAX_RETRIES, RATE_LIMIT_RPS, RATE_LIMIT_BURST);
    }

    @Nested
    @DisplayName("delegation")
    class Delegation {

        @Test
        @DisplayName("delegates getLatestBlockNumber to underlying client")
        void delegatesGetLatestBlockNumber() {
            // given
            given(delegate.getLatestBlockNumber()).willReturn(BLOCK_NUMBER);

            // when
            var result = resilientClient.getLatestBlockNumber();

            // then
            assertThat(result).isEqualTo(BLOCK_NUMBER);
            then(delegate).should().getLatestBlockNumber();
        }

        @Test
        @DisplayName("delegates getBlockByNumber to underlying client")
        void delegatesGetBlockByNumber() {
            // given
            var expectedBlock = EvmBlock.builder()
                    .number("0xf4240")
                    .hash("0xblock_hash")
                    .parentHash("0xparent_hash")
                    .timestamp("0x65b3e8c0")
                    .transactions(List.of())
                    .build();
            given(delegate.getBlockByNumber(BLOCK_NUMBER)).willReturn(expectedBlock);

            // when
            var result = resilientClient.getBlockByNumber(BLOCK_NUMBER);

            // then
            assertThat(result).usingRecursiveComparison().isEqualTo(expectedBlock);
        }

        @Test
        @DisplayName("delegates getTransactionReceipts to underlying client")
        void delegatesGetTransactionReceipts() {
            // given
            var txHashes = List.of(TX_HASH);
            var expectedReceipts = List.of(EvmReceipt.builder()
                    .transactionHash(TX_HASH)
                    .transactionIndex("0x0")
                    .blockNumber("0xf4240")
                    .blockHash("0xblock_hash")
                    .from("0xsender")
                    .to("0xreceiver")
                    .status("0x1")
                    .logs(List.of())
                    .build());
            given(delegate.getTransactionReceipts(txHashes)).willReturn(expectedReceipts);

            // when
            var result = resilientClient.getTransactionReceipts(txHashes);

            // then
            assertThat(result).usingRecursiveComparison().isEqualTo(expectedReceipts);
        }

        @Test
        @DisplayName("delegates getBlockReceipts to underlying client")
        void delegatesGetBlockReceipts() {
            // given
            var expectedReceipts = List.of(EvmReceipt.builder()
                    .transactionHash(TX_HASH)
                    .transactionIndex("0x0")
                    .blockNumber("0xf4240")
                    .blockHash("0xblock_hash")
                    .from("0xsender")
                    .to("0xreceiver")
                    .status("0x1")
                    .logs(List.of())
                    .build());
            given(delegate.getBlockReceipts(BLOCK_NUMBER)).willReturn(expectedReceipts);

            // when
            var result = resilientClient.getBlockReceipts(BLOCK_NUMBER);

            // then
            assertThat(result).usingRecursiveComparison().isEqualTo(expectedReceipts);
        }

        @Test
        @DisplayName("delegates supportsBlockReceipts to underlying client without resilience wrapping")
        void delegatesSupportsBlockReceipts() {
            // given
            given(delegate.supportsBlockReceipts()).willReturn(true);

            // when
            var result = resilientClient.supportsBlockReceipts();

            // then
            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("circuit breaker")
    class CircuitBreakerTests {

        @Test
        @DisplayName("circuit breaker is CLOSED initially")
        void circuitBreakerIsClosedInitially() {
            // given — fresh client

            // when
            var isOpen = resilientClient.isCircuitBreakerOpen();

            // then
            assertThat(isOpen).isFalse();
        }

        @Test
        @DisplayName("circuit breaker opens after failure threshold is reached")
        void circuitBreakerOpensAfterFailureThreshold() {
            // given
            given(delegate.getLatestBlockNumber())
                    .willThrow(EvmRpcException.networkError("eth_blockNumber",
                            new RuntimeException("connection refused")));

            // when — exhaust sliding window (10 calls) to trigger the 50% failure threshold
            IntStream.range(0, 10).forEach(i -> {
                try {
                    resilientClient.getLatestBlockNumber();
                } catch (Exception ignored) {
                    // expected failures
                }
            });

            // then
            assertThat(resilientClient.isCircuitBreakerOpen()).isTrue();
        }

        @Test
        @DisplayName("throws EvmRpcResilienceException when circuit breaker is OPEN")
        void throwsResilienceExceptionWhenCircuitBreakerOpen() {
            // given — force circuit breaker to OPEN state
            resilientClient.getCircuitBreaker().transitionToOpenState();

            // when / then
            assertThatThrownBy(() -> resilientClient.getLatestBlockNumber())
                    .isInstanceOf(EvmRpcResilienceException.class)
                    .hasMessageContaining("Circuit breaker is OPEN")
                    .hasMessageContaining(CHAIN_NAME);
        }

        @Test
        @DisplayName("circuit breaker records successful calls and stays CLOSED")
        void circuitBreakerStaysClosedOnSuccess() {
            // given
            given(delegate.getLatestBlockNumber()).willReturn(BLOCK_NUMBER);

            // when
            IntStream.range(0, 10).forEach(i -> resilientClient.getLatestBlockNumber());

            // then
            assertThat(resilientClient.isCircuitBreakerOpen()).isFalse();
        }

        @Test
        @DisplayName("circuit breaker state transitions from OPEN to HALF_OPEN are exposed")
        void circuitBreakerStateIsAccessible() {
            // given
            resilientClient.getCircuitBreaker().transitionToOpenState();

            // when
            resilientClient.getCircuitBreaker().transitionToHalfOpenState();

            // then
            assertThat(resilientClient.getCircuitBreaker().getState())
                    .isEqualTo(CircuitBreaker.State.HALF_OPEN);
        }
    }

    @Nested
    @DisplayName("retry")
    class RetryTests {

        @Test
        @DisplayName("retries on EvmRpcException and succeeds on later attempt")
        void retriesAndSucceeds() {
            // given
            given(delegate.getLatestBlockNumber())
                    .willThrow(EvmRpcException.networkError("eth_blockNumber",
                            new RuntimeException("timeout")))
                    .willReturn(BLOCK_NUMBER);

            // when
            var result = resilientClient.getLatestBlockNumber();

            // then
            assertThat(result).isEqualTo(BLOCK_NUMBER);
            then(delegate).should(times(2)).getLatestBlockNumber();
        }

        @Test
        @DisplayName("throws after all retry attempts are exhausted")
        void throwsAfterRetryExhaustion() {
            // given
            given(delegate.getLatestBlockNumber())
                    .willThrow(EvmRpcException.networkError("eth_blockNumber",
                            new RuntimeException("connection refused")));

            // when / then
            assertThatThrownBy(() -> resilientClient.getLatestBlockNumber())
                    .isInstanceOf(EvmRpcException.class)
                    .hasMessageContaining("connection refused");
            then(delegate).should(times(MAX_RETRIES)).getLatestBlockNumber();
        }
    }

    @Nested
    @DisplayName("rate limiter")
    class RateLimiterTests {

        @Test
        @DisplayName("allows calls within rate limit")
        void allowsCallsWithinRateLimit() {
            // given
            given(delegate.getLatestBlockNumber()).willReturn(BLOCK_NUMBER);

            // when
            var result = resilientClient.getLatestBlockNumber();

            // then
            assertThat(result).isEqualTo(BLOCK_NUMBER);
        }

        @Test
        @DisplayName("throws EvmRpcResilienceException when rate limit is exhausted")
        void throwsResilienceExceptionWhenRateLimited() {
            // given — drain all permits and set timeout to zero to force immediate rejection
            resilientClient.getRateLimiter().drainPermissions();
            resilientClient.getRateLimiter().changeTimeoutDuration(Duration.ZERO);

            // when / then
            assertThatThrownBy(() -> resilientClient.getLatestBlockNumber())
                    .isInstanceOf(EvmRpcResilienceException.class)
                    .hasMessageContaining("Rate limiter rejected call")
                    .hasMessageContaining(CHAIN_NAME);
        }
    }

    @Nested
    @DisplayName("combined resilience")
    class CombinedResilienceTests {

        @Test
        @DisplayName("retry works with circuit breaker — retries before circuit opens")
        void retryWorksWithCircuitBreaker() {
            // given
            given(delegate.getLatestBlockNumber())
                    .willThrow(EvmRpcException.networkError("eth_blockNumber",
                            new RuntimeException("transient error")))
                    .willThrow(EvmRpcException.networkError("eth_blockNumber",
                            new RuntimeException("transient error")))
                    .willReturn(BLOCK_NUMBER);

            // when
            var result = resilientClient.getLatestBlockNumber();

            // then
            assertThat(result).isEqualTo(BLOCK_NUMBER);
            then(delegate).should(times(3)).getLatestBlockNumber();
            assertThat(resilientClient.isCircuitBreakerOpen()).isFalse();
        }

        @Test
        @DisplayName("does not retry EvmRpcResilienceException")
        void doesNotRetryResilienceException() {
            // given — force circuit breaker to OPEN
            resilientClient.getCircuitBreaker().transitionToOpenState();

            // when / then — should throw immediately without retries
            assertThatThrownBy(() -> resilientClient.getLatestBlockNumber())
                    .isInstanceOf(EvmRpcResilienceException.class)
                    .hasMessageContaining("Circuit breaker is OPEN");
            then(delegate).shouldHaveNoInteractions();
        }
    }
}
