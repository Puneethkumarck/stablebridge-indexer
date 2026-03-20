package com.stablebridge.indexer.infrastructure.chain.bitcoin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("BitcoinConfirmationTracker")
class BitcoinConfirmationTrackerTest {

    @Mock
    private BitcoinRpcClient rpcClient;

    @Nested
    @DisplayName("getLatestConfirmedBlockNumber")
    class GetLatestConfirmedBlockNumber {

        @Test
        @DisplayName("returns blockCount minus 6 confirmations (default)")
        void returnsBlockCountMinusSixConfirmations() {
            // given
            given(rpcClient.getBlockCount()).willReturn(800000L);
            var tracker = new BitcoinConfirmationTracker(rpcClient, 6);

            // when
            var result = tracker.getLatestConfirmedBlockNumber();

            // then
            assertThat(result).isEqualTo(799994L);
        }

        @Test
        @DisplayName("returns blockCount minus 3 confirmations")
        void returnsBlockCountMinusThreeConfirmations() {
            // given
            given(rpcClient.getBlockCount()).willReturn(800000L);
            var tracker = new BitcoinConfirmationTracker(rpcClient, 3);

            // when
            var result = tracker.getLatestConfirmedBlockNumber();

            // then
            assertThat(result).isEqualTo(799997L);
        }

        @Test
        @DisplayName("returns blockCount when 0 confirmations required")
        void returnsBlockCountWhenZeroConfirmations() {
            // given
            given(rpcClient.getBlockCount()).willReturn(800000L);
            var tracker = new BitcoinConfirmationTracker(rpcClient, 0);

            // when
            var result = tracker.getLatestConfirmedBlockNumber();

            // then
            assertThat(result).isEqualTo(800000L);
        }

        @Test
        @DisplayName("returns zero when blockCount is less than minConfirmations")
        void returnsZeroWhenBlockCountLessThanMinConfirmations() {
            // given
            given(rpcClient.getBlockCount()).willReturn(3L);
            var tracker = new BitcoinConfirmationTracker(rpcClient, 6);

            // when
            var result = tracker.getLatestConfirmedBlockNumber();

            // then
            assertThat(result).isEqualTo(0L);
        }

        @Test
        @DisplayName("calls getBlockCount on rpcClient")
        void callsGetBlockCountOnRpcClient() {
            // given
            given(rpcClient.getBlockCount()).willReturn(800000L);
            var tracker = new BitcoinConfirmationTracker(rpcClient, 6);

            // when
            tracker.getLatestConfirmedBlockNumber();

            // then
            then(rpcClient).should().getBlockCount();
        }
    }

    @Nested
    @DisplayName("hasEnoughConfirmations")
    class HasEnoughConfirmations {

        @Test
        @DisplayName("returns true when block has exactly minConfirmations")
        void returnsTrueWhenBlockHasExactlyMinConfirmations() {
            // given
            given(rpcClient.getBlockCount()).willReturn(800000L);
            var tracker = new BitcoinConfirmationTracker(rpcClient, 6);

            // when
            var result = tracker.hasEnoughConfirmations(799994L);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("returns true when block has more than minConfirmations")
        void returnsTrueWhenBlockHasMoreThanMinConfirmations() {
            // given
            given(rpcClient.getBlockCount()).willReturn(800000L);
            var tracker = new BitcoinConfirmationTracker(rpcClient, 6);

            // when
            var result = tracker.hasEnoughConfirmations(799000L);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("returns false when block has fewer than minConfirmations")
        void returnsFalseWhenBlockHasFewerThanMinConfirmations() {
            // given
            given(rpcClient.getBlockCount()).willReturn(800000L);
            var tracker = new BitcoinConfirmationTracker(rpcClient, 6);

            // when
            var result = tracker.hasEnoughConfirmations(799998L);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("returns false when block is the latest block with non-zero confirmations required")
        void returnsFalseWhenBlockIsLatestWithConfirmationsRequired() {
            // given
            given(rpcClient.getBlockCount()).willReturn(800000L);
            var tracker = new BitcoinConfirmationTracker(rpcClient, 6);

            // when
            var result = tracker.hasEnoughConfirmations(800000L);

            // then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("getMinConfirmations")
    class GetMinConfirmations {

        @Test
        @DisplayName("returns configured minConfirmations value")
        void returnsConfiguredMinConfirmationsValue() {
            // given
            var tracker = new BitcoinConfirmationTracker(rpcClient, 6);

            // when
            var result = tracker.getMinConfirmations();

            // then
            assertThat(result).isEqualTo(6);
        }
    }
}
