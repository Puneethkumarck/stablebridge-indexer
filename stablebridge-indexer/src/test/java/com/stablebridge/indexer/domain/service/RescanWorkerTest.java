package com.stablebridge.indexer.domain.service;

import com.stablebridge.indexer.domain.port.AddressFilter;
import com.stablebridge.indexer.domain.port.BlockProgressStore;
import com.stablebridge.indexer.domain.port.ChainIndexer;
import com.stablebridge.indexer.domain.port.TransferEventPublisher;
import com.stablebridge.indexer.domain.port.WalletAddressRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static com.stablebridge.indexer.domain.model.ChainId.ETHEREUM;
import static com.stablebridge.indexer.domain.model.WorkerType.RESCAN;
import static com.stablebridge.indexer.domain.service.RescanWorker.MAX_ATTEMPTS;
import static com.stablebridge.indexer.testutil.TransferFixtures.aBlockResult;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
@DisplayName("RescanWorker")
class RescanWorkerTest {

    @Mock
    private ChainIndexer chainIndexer;

    @Mock
    private AddressFilter addressFilter;

    @Mock
    private TransferEventPublisher transferEventPublisher;

    @Mock
    private BlockProgressStore blockProgressStore;

    @Mock
    private WalletAddressRepository walletAddressRepository;

    private MeterRegistry meterRegistry;

    private RescanWorker worker;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        given(chainIndexer.getChainId()).willReturn(ETHEREUM);
        worker = new RescanWorker(
                chainIndexer, addressFilter, transferEventPublisher,
                blockProgressStore, walletAddressRepository, meterRegistry);
    }

    @Nested
    @DisplayName("rescan")
    class Rescan {

        @Test
        @DisplayName("removes block from failed set on success")
        void removesBlockFromFailedSetOnSuccess() {
            // given
            given(blockProgressStore.getFailedBlocks(ETHEREUM)).willReturn(Set.of(100L));
            var blockResult = aBlockResult().transfers(List.of()).build();
            given(chainIndexer.indexBlock(100L)).willReturn(blockResult);

            // when
            worker.rescan();

            // then
            then(blockProgressStore).should().removeFailedBlock(ETHEREUM, 100L);
        }

        @Test
        @DisplayName("does nothing when no failed blocks")
        void doesNothingWhenNoFailedBlocks() {
            // given
            given(blockProgressStore.getFailedBlocks(ETHEREUM)).willReturn(Set.of());

            // when
            worker.rescan();

            // then
            then(chainIndexer).should(never()).indexBlock(100L);
        }

        @Test
        @DisplayName("increments attempt count on failure")
        void incrementsAttemptCountOnFailure() {
            // given
            given(blockProgressStore.getFailedBlocks(ETHEREUM)).willReturn(Set.of(100L));
            given(chainIndexer.indexBlock(100L)).willThrow(new RuntimeException("RPC error"));

            // when
            worker.rescan();
            worker.rescan();

            // then
            then(chainIndexer).should(times(2)).indexBlock(100L);
        }

        @Test
        @DisplayName("gives up after max attempts")
        void givesUpAfterMaxAttempts() {
            // given
            given(blockProgressStore.getFailedBlocks(ETHEREUM)).willReturn(Set.of(100L));
            given(chainIndexer.indexBlock(100L)).willThrow(new RuntimeException("RPC error"));

            // when — call rescan MAX_ATTEMPTS + 1 times
            for (var i = 0; i < MAX_ATTEMPTS + 1; i++) {
                worker.rescan();
            }

            // then — indexBlock called MAX_ATTEMPTS times (not MAX_ATTEMPTS + 1)
            then(chainIndexer).should(times(MAX_ATTEMPTS)).indexBlock(100L);
        }

        @Test
        @DisplayName("processes multiple failed blocks in order")
        void processesMultipleFailedBlocksInOrder() {
            // given
            given(blockProgressStore.getFailedBlocks(ETHEREUM)).willReturn(Set.of(300L, 100L, 200L));
            var blockResult = aBlockResult().transfers(List.of()).build();
            given(chainIndexer.indexBlock(100L)).willReturn(blockResult);
            given(chainIndexer.indexBlock(200L)).willReturn(blockResult);
            given(chainIndexer.indexBlock(300L)).willReturn(blockResult);

            // when
            worker.rescan();

            // then
            var inOrder = inOrder(chainIndexer);
            then(chainIndexer).should(inOrder).indexBlock(100L);
            then(chainIndexer).should(inOrder).indexBlock(200L);
            then(chainIndexer).should(inOrder).indexBlock(300L);
        }

        @Test
        @DisplayName("keeps block in failed set after max attempts")
        void keepsBlockInFailedSetAfterMaxAttempts() {
            // given
            given(blockProgressStore.getFailedBlocks(ETHEREUM)).willReturn(Set.of(100L));
            given(chainIndexer.indexBlock(100L)).willThrow(new RuntimeException("RPC error"));

            // when
            for (var i = 0; i < MAX_ATTEMPTS + 1; i++) {
                worker.rescan();
            }

            // then
            then(blockProgressStore).should(never()).removeFailedBlock(ETHEREUM, 100L);
        }
    }

    @Nested
    @DisplayName("calculateBackoff")
    class CalculateBackoff {

        @Test
        @DisplayName("returns exponential backoff")
        void returnsExponentialBackoff() {
            // given — worker created in setUp

            // when / then
            assertThat(worker.calculateBackoff(1)).isEqualTo(1000L);
            assertThat(worker.calculateBackoff(2)).isEqualTo(2000L);
            assertThat(worker.calculateBackoff(3)).isEqualTo(4000L);
            assertThat(worker.calculateBackoff(4)).isEqualTo(8000L);
            assertThat(worker.calculateBackoff(5)).isEqualTo(16000L);
        }

        @Test
        @DisplayName("caps at 30 seconds")
        void capsAt30Seconds() {
            // given — worker created in setUp

            // when / then
            assertThat(worker.calculateBackoff(6)).isEqualTo(30000L);
            assertThat(worker.calculateBackoff(10)).isEqualTo(30000L);
        }
    }

    @Nested
    @DisplayName("getWorkerType")
    class GetWorkerType {

        @Test
        @DisplayName("returns RESCAN")
        void returnsRescan() {
            // given — worker created in setUp

            // when
            var workerType = worker.getWorkerType();

            // then
            assertThat(workerType).isEqualTo(RESCAN);
        }
    }
}
