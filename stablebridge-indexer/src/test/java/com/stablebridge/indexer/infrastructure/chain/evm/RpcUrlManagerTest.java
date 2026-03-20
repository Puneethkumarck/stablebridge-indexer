package com.stablebridge.indexer.infrastructure.chain.evm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.stream.IntStream;

import static com.stablebridge.indexer.infrastructure.chain.evm.RpcUrlManager.HEALTH_PROBE_INTERVAL;
import static com.stablebridge.indexer.infrastructure.chain.evm.RpcUrlManager.MAX_CONSECUTIVE_FAILURES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RpcUrlManager")
class RpcUrlManagerTest {

    private static final String URL_1 = "https://rpc1.example.com";
    private static final String URL_2 = "https://rpc2.example.com";
    private static final String URL_3 = "https://rpc3.example.com";
    private static final List<String> THREE_URLS = List.of(URL_1, URL_2, URL_3);

    @Nested
    @DisplayName("constructor validation")
    class ConstructorValidation {

        @Test
        @DisplayName("throws IllegalArgumentException when urls list is null")
        void throwsWhenUrlsNull() {
            // given / when / then
            assertThatThrownBy(() -> new RpcUrlManager(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("At least one RPC URL must be provided");
        }

        @Test
        @DisplayName("throws IllegalArgumentException when urls list is empty")
        void throwsWhenUrlsEmpty() {
            // given / when / then
            assertThatThrownBy(() -> new RpcUrlManager(List.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("At least one RPC URL must be provided");
        }

        @Test
        @DisplayName("creates manager successfully with single URL")
        void createsWithSingleUrl() {
            // given
            var urls = List.of(URL_1);

            // when
            var manager = new RpcUrlManager(urls);

            // then
            assertThat(manager.totalUrlCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("creates manager successfully with multiple URLs")
        void createsWithMultipleUrls() {
            // given / when
            var manager = new RpcUrlManager(THREE_URLS);

            // then
            assertThat(manager.totalUrlCount()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("round-robin URL selection")
    class RoundRobin {

        @Test
        @DisplayName("rotates through all healthy URLs in order")
        void rotatesThroughAllUrls() {
            // given
            var manager = new RpcUrlManager(THREE_URLS);

            // when
            var first = manager.getNextHealthyUrl();
            var second = manager.getNextHealthyUrl();
            var third = manager.getNextHealthyUrl();
            var fourth = manager.getNextHealthyUrl();

            // then
            assertThat(List.of(first, second, third))
                    .containsExactlyInAnyOrder(URL_1, URL_2, URL_3);
            assertThat(fourth).isEqualTo(first);
        }

        @Test
        @DisplayName("visits all URLs within one full cycle")
        void visitsAllUrlsInCycle() {
            // given
            var manager = new RpcUrlManager(THREE_URLS);

            // when
            var visited = new HashSet<String>();
            IntStream.range(0, 3).forEach(i -> visited.add(manager.getNextHealthyUrl()));

            // then
            assertThat(visited).containsExactlyInAnyOrder(URL_1, URL_2, URL_3);
        }

        @Test
        @DisplayName("returns the only URL for single-URL manager")
        void returnsSingleUrl() {
            // given
            var manager = new RpcUrlManager(List.of(URL_1));

            // when
            var first = manager.getNextHealthyUrl();
            var second = manager.getNextHealthyUrl();

            // then
            assertThat(first).isEqualTo(URL_1);
            assertThat(second).isEqualTo(URL_1);
        }
    }

    @Nested
    @DisplayName("failure tracking")
    class FailureTracking {

        @Test
        @DisplayName("URL remains healthy below max consecutive failures")
        void urlStaysHealthyBelowThreshold() {
            // given
            var manager = new RpcUrlManager(THREE_URLS);

            // when
            IntStream.range(0, MAX_CONSECUTIVE_FAILURES - 1)
                    .forEach(i -> manager.markFailure(URL_1));

            // then
            assertThat(manager.hasHealthyUrls()).isTrue();
            assertThat(manager.healthyUrlCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("URL marked unhealthy after max consecutive failures")
        void urlMarkedUnhealthyAtThreshold() {
            // given
            var manager = new RpcUrlManager(THREE_URLS);

            // when
            IntStream.range(0, MAX_CONSECUTIVE_FAILURES)
                    .forEach(i -> manager.markFailure(URL_1));

            // then
            assertThat(manager.healthyUrlCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("skips unhealthy URL during round-robin")
        void skipsUnhealthyUrl() {
            // given
            var manager = new RpcUrlManager(THREE_URLS);
            IntStream.range(0, MAX_CONSECUTIVE_FAILURES)
                    .forEach(i -> manager.markFailure(URL_1));

            // when
            var visited = new HashSet<String>();
            IntStream.range(0, 6).forEach(i -> visited.add(manager.getNextHealthyUrl()));

            // then
            assertThat(visited).containsExactlyInAnyOrder(URL_2, URL_3);
        }

        @Test
        @DisplayName("markSuccess resets failure count and restores health")
        void markSuccessResetsFailures() {
            // given
            var manager = new RpcUrlManager(THREE_URLS);
            IntStream.range(0, MAX_CONSECUTIVE_FAILURES)
                    .forEach(i -> manager.markFailure(URL_1));
            assertThat(manager.healthyUrlCount()).isEqualTo(2);

            // when
            manager.markSuccess(URL_1);

            // then
            assertThat(manager.healthyUrlCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("markSuccess after partial failures prevents reaching threshold")
        void markSuccessAfterPartialFailures() {
            // given
            var manager = new RpcUrlManager(THREE_URLS);
            IntStream.range(0, MAX_CONSECUTIVE_FAILURES - 1)
                    .forEach(i -> manager.markFailure(URL_1));

            // when
            manager.markSuccess(URL_1);
            manager.markFailure(URL_1);

            // then
            assertThat(manager.healthyUrlCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("markFailure on unknown URL is a no-op")
        void markFailureUnknownUrlNoOp() {
            // given
            var manager = new RpcUrlManager(THREE_URLS);

            // when
            manager.markFailure("https://unknown.example.com");

            // then
            assertThat(manager.healthyUrlCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("markSuccess on unknown URL is a no-op")
        void markSuccessUnknownUrlNoOp() {
            // given
            var manager = new RpcUrlManager(THREE_URLS);

            // when
            manager.markSuccess("https://unknown.example.com");

            // then
            assertThat(manager.healthyUrlCount()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("all URLs unhealthy")
    class AllUrlsUnhealthy {

        @Test
        @DisplayName("throws EvmRpcException when all URLs are unhealthy and not probe-eligible")
        void throwsWhenAllUnhealthy() {
            // given
            var now = Instant.parse("2026-03-20T10:00:00Z");
            var clock = Clock.fixed(now, ZoneId.of("UTC"));
            var manager = new RpcUrlManager(THREE_URLS, clock);

            THREE_URLS.forEach(url ->
                    IntStream.range(0, MAX_CONSECUTIVE_FAILURES)
                            .forEach(i -> manager.markFailure(url)));

            // when / then
            assertThatThrownBy(manager::getNextHealthyUrl)
                    .isInstanceOf(EvmRpcException.class)
                    .hasMessageContaining("All 3 RPC URLs are unhealthy");
        }

        @Test
        @DisplayName("hasHealthyUrls returns false when all URLs are unhealthy")
        void hasHealthyUrlsReturnsFalse() {
            // given
            var manager = new RpcUrlManager(THREE_URLS);
            THREE_URLS.forEach(url ->
                    IntStream.range(0, MAX_CONSECUTIVE_FAILURES)
                            .forEach(i -> manager.markFailure(url)));

            // when
            var result = manager.hasHealthyUrls();

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("healthyUrlCount returns zero when all URLs are unhealthy")
        void healthyUrlCountReturnsZero() {
            // given
            var manager = new RpcUrlManager(THREE_URLS);
            THREE_URLS.forEach(url ->
                    IntStream.range(0, MAX_CONSECUTIVE_FAILURES)
                            .forEach(i -> manager.markFailure(url)));

            // when
            var count = manager.healthyUrlCount();

            // then
            assertThat(count).isZero();
        }
    }

    @Nested
    @DisplayName("health probe with mutable clock")
    class HealthProbeWithMutableClock {

        @Test
        @DisplayName("unhealthy URL is returned after probe interval elapses")
        void unhealthyUrlReturnedAfterProbeInterval() {
            // given
            var failureTime = Instant.parse("2026-03-20T10:00:00Z");
            var mutableClock = new MutableClock(failureTime);
            var manager = new RpcUrlManager(List.of(URL_1, URL_2), mutableClock);

            // Mark URL_1 as unhealthy
            IntStream.range(0, MAX_CONSECUTIVE_FAILURES)
                    .forEach(i -> manager.markFailure(URL_1));

            // URL_2 is still healthy — get it to verify URL_1 is skipped
            var firstCall = manager.getNextHealthyUrl();
            assertThat(firstCall).isEqualTo(URL_2);

            // when — advance clock past probe interval
            mutableClock.setInstant(failureTime.plus(HEALTH_PROBE_INTERVAL).plusSeconds(1));

            // then — URL_1 should now be returned as probe candidate
            var visited = new HashSet<String>();
            IntStream.range(0, 4).forEach(i -> visited.add(manager.getNextHealthyUrl()));
            assertThat(visited).containsExactlyInAnyOrder(URL_1, URL_2);
        }

        @Test
        @DisplayName("all URLs unhealthy — probe-eligible URL returned after interval")
        void allUnhealthyProbeEligibleReturned() {
            // given
            var failureTime = Instant.parse("2026-03-20T10:00:00Z");
            var mutableClock = new MutableClock(failureTime);
            var manager = new RpcUrlManager(THREE_URLS, mutableClock);

            THREE_URLS.forEach(url ->
                    IntStream.range(0, MAX_CONSECUTIVE_FAILURES)
                            .forEach(i -> manager.markFailure(url)));
            assertThat(manager.healthyUrlCount()).isZero();

            // when — advance clock past probe interval
            mutableClock.setInstant(failureTime.plus(HEALTH_PROBE_INTERVAL).plusSeconds(1));

            // then — all URLs should be probe-eligible
            var result = manager.getNextHealthyUrl();
            assertThat(result).isIn(URL_1, URL_2, URL_3);
        }

        @Test
        @DisplayName("unhealthy URL still skipped before probe interval elapses")
        void unhealthyUrlSkippedBeforeProbeInterval() {
            // given
            var failureTime = Instant.parse("2026-03-20T10:00:00Z");
            var mutableClock = new MutableClock(failureTime);
            var manager = new RpcUrlManager(THREE_URLS, mutableClock);

            IntStream.range(0, MAX_CONSECUTIVE_FAILURES)
                    .forEach(i -> manager.markFailure(URL_1));

            // when — advance clock but NOT past probe interval
            mutableClock.setInstant(failureTime.plus(HEALTH_PROBE_INTERVAL.minusSeconds(1)));
            var visited = new HashSet<String>();
            IntStream.range(0, 6).forEach(i -> visited.add(manager.getNextHealthyUrl()));

            // then — URL_1 should still be skipped
            assertThat(visited).containsExactlyInAnyOrder(URL_2, URL_3);
        }

        @Test
        @DisplayName("successful probe restores URL to healthy rotation")
        void successfulProbeRestoresUrl() {
            // given
            var failureTime = Instant.parse("2026-03-20T10:00:00Z");
            var mutableClock = new MutableClock(failureTime);
            var manager = new RpcUrlManager(THREE_URLS, mutableClock);

            IntStream.range(0, MAX_CONSECUTIVE_FAILURES)
                    .forEach(i -> manager.markFailure(URL_1));
            assertThat(manager.healthyUrlCount()).isEqualTo(2);

            // Advance clock past probe interval
            mutableClock.setInstant(failureTime.plus(HEALTH_PROBE_INTERVAL).plusSeconds(1));

            // URL_1 is now probe-eligible — simulate successful probe
            // when
            manager.markSuccess(URL_1);

            // then
            assertThat(manager.healthyUrlCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("failed probe keeps URL unhealthy")
        void failedProbeKeepsUrlUnhealthy() {
            // given
            var failureTime = Instant.parse("2026-03-20T10:00:00Z");
            var mutableClock = new MutableClock(failureTime);
            var manager = new RpcUrlManager(THREE_URLS, mutableClock);

            IntStream.range(0, MAX_CONSECUTIVE_FAILURES)
                    .forEach(i -> manager.markFailure(URL_1));

            // Advance clock past probe interval
            var probeTime = failureTime.plus(HEALTH_PROBE_INTERVAL).plusSeconds(1);
            mutableClock.setInstant(probeTime);

            // when — probe fails (another failure recorded)
            manager.markFailure(URL_1);

            // then — URL_1 is still unhealthy and last failure time is updated
            assertThat(manager.healthyUrlCount()).isEqualTo(2);

            // Advance clock to just before the new probe interval
            mutableClock.setInstant(probeTime.plus(HEALTH_PROBE_INTERVAL.minusSeconds(1)));
            var visited = new HashSet<String>();
            IntStream.range(0, 6).forEach(i -> visited.add(manager.getNextHealthyUrl()));
            assertThat(visited).containsExactlyInAnyOrder(URL_2, URL_3);
        }
    }

    @Nested
    @DisplayName("initial state")
    class InitialState {

        @Test
        @DisplayName("all URLs are healthy initially")
        void allUrlsHealthyInitially() {
            // given / when
            var manager = new RpcUrlManager(THREE_URLS);

            // then
            assertThat(manager.hasHealthyUrls()).isTrue();
            assertThat(manager.healthyUrlCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("totalUrlCount matches provided URLs")
        void totalUrlCountMatchesInput() {
            // given / when
            var manager = new RpcUrlManager(THREE_URLS);

            // then
            assertThat(manager.totalUrlCount()).isEqualTo(3);
        }
    }

    /**
     * A mutable Clock implementation for testing time-dependent behavior.
     */
    private static final class MutableClock extends Clock {

        private volatile Instant instant;
        private final ZoneId zone = ZoneId.of("UTC");

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void setInstant(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

}
