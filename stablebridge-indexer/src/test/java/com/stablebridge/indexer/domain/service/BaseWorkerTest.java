package com.stablebridge.indexer.domain.service;

import com.stablebridge.indexer.domain.event.TransferDetectedEvent;
import com.stablebridge.indexer.domain.model.WorkerType;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.List;

import static com.stablebridge.indexer.domain.model.ChainId.ETHEREUM;
import static com.stablebridge.indexer.domain.model.NetworkType.EVM;
import static com.stablebridge.indexer.domain.model.TransferDirection.INCOMING;
import static com.stablebridge.indexer.domain.model.TransferDirection.OUTGOING;
import static com.stablebridge.indexer.domain.model.WorkerState.PARKED;
import static com.stablebridge.indexer.domain.model.WorkerState.RUNNING;
import static com.stablebridge.indexer.domain.model.WorkerState.STOPPED;
import static com.stablebridge.indexer.domain.model.WorkerType.REGULAR;
import static com.stablebridge.indexer.testutil.TransferFixtures.DEFAULT_BLOCK_NUMBER;
import static com.stablebridge.indexer.testutil.TransferFixtures.DEFAULT_FROM_ADDRESS;
import static com.stablebridge.indexer.testutil.TransferFixtures.DEFAULT_TO_ADDRESS;
import static com.stablebridge.indexer.testutil.TransferFixtures.aBlockResult;
import static com.stablebridge.indexer.testutil.TransferFixtures.aTransfer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("BaseWorker")
class BaseWorkerTest {

    private static final String NON_WATCHED_ADDRESS = "0x9999999999999999999999999999999999999999";
    private static final String SECOND_TO_ADDRESS = "0xdeadbeef1234567890abcdef1234567890abcdef";

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

    @Captor
    private ArgumentCaptor<List<TransferDetectedEvent>> eventsCaptor;

    private MeterRegistry meterRegistry;

    private TestWorker worker;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        given(chainIndexer.getChainId()).willReturn(ETHEREUM);
        worker = new TestWorker(
                chainIndexer, addressFilter, transferEventPublisher,
                blockProgressStore, walletAddressRepository, meterRegistry);
    }

    @Nested
    @DisplayName("processBlock")
    class ProcessBlock {

        @Test
        @DisplayName("publishes matched events to Kafka then saves progress to Redis")
        void publishesMatchedEventsToKafkaThenSavesProgressToRedis() {
            // given
            var transfer = aTransfer().build();
            var blockResult = aBlockResult().transfers(List.of(transfer)).build();
            given(chainIndexer.indexBlock(DEFAULT_BLOCK_NUMBER)).willReturn(blockResult);
            given(addressFilter.mightContain(DEFAULT_TO_ADDRESS, EVM)).willReturn(true);
            given(walletAddressRepository.existsByAddressAndNetworkType(DEFAULT_TO_ADDRESS, EVM))
                    .willReturn(true);

            // when
            worker.processBlock(DEFAULT_BLOCK_NUMBER);

            // then
            var order = inOrder(transferEventPublisher, blockProgressStore);
            then(transferEventPublisher).should(order).publishAll(eventsCaptor.capture());
            then(blockProgressStore).should(order)
                    .saveLastProcessedBlock(ETHEREUM, DEFAULT_BLOCK_NUMBER);

            var expectedEvent = TransferDetectedEvent.builder()
                    .transfer(transfer)
                    .direction(INCOMING)
                    .detectedAt(Instant.now())
                    .build();
            assertThat(eventsCaptor.getValue())
                    .usingRecursiveComparison()
                    .ignoringFields("detectedAt")
                    .isEqualTo(List.of(expectedEvent));
        }

        @Test
        @DisplayName("saves progress even when no transfers match")
        void savesProgressEvenWhenNoTransfersMatch() {
            // given
            var blockResult = aBlockResult()
                    .transfers(List.of(aTransfer().toAddress(NON_WATCHED_ADDRESS).build()))
                    .build();
            given(chainIndexer.indexBlock(DEFAULT_BLOCK_NUMBER)).willReturn(blockResult);
            given(addressFilter.mightContain(NON_WATCHED_ADDRESS, EVM)).willReturn(false);

            // when
            worker.processBlock(DEFAULT_BLOCK_NUMBER);

            // then
            then(transferEventPublisher).shouldHaveNoInteractions();
            then(blockProgressStore).should().saveLastProcessedBlock(ETHEREUM, DEFAULT_BLOCK_NUMBER);
        }

        @Test
        @DisplayName("does not publish when bloom hits but DB misses (false positive)")
        void doesNotPublishWhenBloomHitsButDbMisses() {
            // given
            var blockResult = aBlockResult().build();
            given(chainIndexer.indexBlock(DEFAULT_BLOCK_NUMBER)).willReturn(blockResult);
            given(addressFilter.mightContain(DEFAULT_TO_ADDRESS, EVM)).willReturn(true);
            given(walletAddressRepository.existsByAddressAndNetworkType(DEFAULT_TO_ADDRESS, EVM))
                    .willReturn(false);

            // when
            worker.processBlock(DEFAULT_BLOCK_NUMBER);

            // then
            then(transferEventPublisher).shouldHaveNoInteractions();
            then(blockProgressStore).should().saveLastProcessedBlock(ETHEREUM, DEFAULT_BLOCK_NUMBER);
        }

        @Test
        @DisplayName("publishes only confirmed transfers when some match and some do not")
        void publishesOnlyConfirmedTransfersWhenSomeMatchAndSomeDoNot() {
            // given
            var matchedTransfer = aTransfer().toAddress(DEFAULT_TO_ADDRESS).build();
            var unmatchedTransfer = aTransfer().toAddress(NON_WATCHED_ADDRESS).logIndex(8).build();
            var blockResult = aBlockResult()
                    .transfers(List.of(matchedTransfer, unmatchedTransfer))
                    .build();
            given(chainIndexer.indexBlock(DEFAULT_BLOCK_NUMBER)).willReturn(blockResult);
            given(addressFilter.mightContain(DEFAULT_TO_ADDRESS, EVM)).willReturn(true);
            given(addressFilter.mightContain(NON_WATCHED_ADDRESS, EVM)).willReturn(false);
            given(walletAddressRepository.existsByAddressAndNetworkType(DEFAULT_TO_ADDRESS, EVM))
                    .willReturn(true);

            // when
            worker.processBlock(DEFAULT_BLOCK_NUMBER);

            // then
            then(transferEventPublisher).should().publishAll(eventsCaptor.capture());

            var expectedEvent = TransferDetectedEvent.builder()
                    .transfer(matchedTransfer)
                    .direction(INCOMING)
                    .detectedAt(Instant.now())
                    .build();
            assertThat(eventsCaptor.getValue())
                    .usingRecursiveComparison()
                    .ignoringFields("detectedAt")
                    .isEqualTo(List.of(expectedEvent));
        }

        @Test
        @DisplayName("saves progress when block has no transfers")
        void savesProgressWhenBlockHasNoTransfers() {
            // given
            var blockResult = aBlockResult().transfers(List.of()).build();
            given(chainIndexer.indexBlock(DEFAULT_BLOCK_NUMBER)).willReturn(blockResult);

            // when
            worker.processBlock(DEFAULT_BLOCK_NUMBER);

            // then
            then(transferEventPublisher).shouldHaveNoInteractions();
            then(blockProgressStore).should().saveLastProcessedBlock(ETHEREUM, DEFAULT_BLOCK_NUMBER);
        }
    }

    @Nested
    @DisplayName("matchTransfers")
    class MatchTransfers {

        @Test
        @DisplayName("returns only transfers that pass both bloom and DB check")
        void returnsOnlyTransfersThatPassBothBloomAndDbCheck() {
            // given
            var confirmedTransfer = aTransfer().toAddress(DEFAULT_TO_ADDRESS).build();
            var bloomMissTransfer = aTransfer().toAddress(NON_WATCHED_ADDRESS).logIndex(8).build();
            var falsePositiveTransfer = aTransfer().toAddress(SECOND_TO_ADDRESS).logIndex(9).build();

            var blockResult = aBlockResult()
                    .transfers(List.of(confirmedTransfer, bloomMissTransfer, falsePositiveTransfer))
                    .build();

            given(addressFilter.mightContain(DEFAULT_TO_ADDRESS, EVM)).willReturn(true);
            given(addressFilter.mightContain(NON_WATCHED_ADDRESS, EVM)).willReturn(false);
            given(addressFilter.mightContain(SECOND_TO_ADDRESS, EVM)).willReturn(true);
            given(walletAddressRepository.existsByAddressAndNetworkType(DEFAULT_TO_ADDRESS, EVM))
                    .willReturn(true);
            given(walletAddressRepository.existsByAddressAndNetworkType(SECOND_TO_ADDRESS, EVM))
                    .willReturn(false);

            // when
            var result = worker.matchTransfers(blockResult);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().transfer().toAddress()).isEqualTo(DEFAULT_TO_ADDRESS);
        }

        @Test
        @DisplayName("returns empty list when no transfers match bloom filter")
        void returnsEmptyListWhenNoTransfersMatchBloomFilter() {
            // given
            var blockResult = aBlockResult()
                    .transfers(List.of(aTransfer().toAddress(NON_WATCHED_ADDRESS).build()))
                    .build();
            given(addressFilter.mightContain(NON_WATCHED_ADDRESS, EVM)).willReturn(false);

            // when
            var result = worker.matchTransfers(blockResult);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("does not call DB when bloom filter returns false")
        void doesNotCallDbWhenBloomFilterReturnsFalse() {
            // given
            var blockResult = aBlockResult()
                    .transfers(List.of(aTransfer().toAddress(NON_WATCHED_ADDRESS).build()))
                    .build();
            given(addressFilter.mightContain(NON_WATCHED_ADDRESS, EVM)).willReturn(false);

            // when
            worker.matchTransfers(blockResult);

            // then
            then(walletAddressRepository).should(never())
                    .existsByAddressAndNetworkType(NON_WATCHED_ADDRESS, EVM);
        }
    }

    @Nested
    @DisplayName("state transitions")
    class StateTransitions {

        @Test
        @DisplayName("starts in STOPPED state")
        void startsInStoppedState() {
            // given — worker created in setUp

            // when
            var state = worker.getState();

            // then
            assertThat(state).isEqualTo(STOPPED);
        }

        @Test
        @DisplayName("transitions to RUNNING when started")
        void transitionsToRunningWhenStarted() {
            // given — worker in STOPPED state

            // when
            worker.start();

            // then
            assertThat(worker.getState()).isEqualTo(RUNNING);
        }

        @Test
        @DisplayName("transitions to PARKED when parked")
        void transitionsToParkedWhenParked() {
            // given
            worker.start();

            // when
            worker.park();

            // then
            assertThat(worker.getState()).isEqualTo(PARKED);
        }

        @Test
        @DisplayName("transitions to STOPPED when stopped")
        void transitionsToStoppedWhenStopped() {
            // given
            worker.start();

            // when
            worker.stop();

            // then
            assertThat(worker.getState()).isEqualTo(STOPPED);
        }

        @Test
        @DisplayName("resumes from PARKED to RUNNING when health probe succeeds")
        void resumesFromParkedToRunningWhenHealthProbeSucceeds() {
            // given
            worker.start();
            worker.park();
            given(chainIndexer.getLatestFinalizedBlockNumber()).willReturn(19_500_000L);

            // when
            var resumed = worker.tryResume();

            // then
            assertThat(resumed).isTrue();
            assertThat(worker.getState()).isEqualTo(RUNNING);
        }

        @Test
        @DisplayName("stays PARKED when health probe fails")
        void staysParkedWhenHealthProbeFails() {
            // given
            worker.start();
            worker.park();
            given(chainIndexer.getLatestFinalizedBlockNumber())
                    .willThrow(new RuntimeException("RPC unavailable"));

            // when
            var resumed = worker.tryResume();

            // then
            assertThat(resumed).isFalse();
            assertThat(worker.getState()).isEqualTo(PARKED);
        }
    }

    @Nested
    @DisplayName("MDC context")
    class MdcContext {

        @Test
        @DisplayName("clears MDC after block processing completes")
        void clearsMdcAfterBlockProcessingCompletes() {
            // given
            var blockResult = aBlockResult().transfers(List.of()).build();
            given(chainIndexer.indexBlock(DEFAULT_BLOCK_NUMBER)).willReturn(blockResult);

            // when
            worker.processBlock(DEFAULT_BLOCK_NUMBER);

            // then
            assertThat(MDC.get("chain")).isNull();
            assertThat(MDC.get("blockNumber")).isNull();
            assertThat(MDC.get("workerType")).isNull();
            assertThat(MDC.get("traceId")).isNull();
        }

        @Test
        @DisplayName("clears MDC even when processing fails")
        void clearsMdcEvenWhenProcessingFails() {
            // given
            given(chainIndexer.indexBlock(DEFAULT_BLOCK_NUMBER))
                    .willThrow(new RuntimeException("RPC error"));

            // when
            try {
                worker.processBlock(DEFAULT_BLOCK_NUMBER);
            } catch (RuntimeException ignored) {
                // expected
            }

            // then
            assertThat(MDC.get("chain")).isNull();
            assertThat(MDC.get("blockNumber")).isNull();
            assertThat(MDC.get("workerType")).isNull();
            assertThat(MDC.get("traceId")).isNull();
        }
    }

    @Nested
    @DisplayName("Micrometer counters")
    class MicrometerCounters {

        @Test
        @DisplayName("increments blocks processed counter after successful processing")
        void incrementsBlocksProcessedCounterAfterSuccessfulProcessing() {
            // given
            var blockResult = aBlockResult().transfers(List.of()).build();
            given(chainIndexer.indexBlock(DEFAULT_BLOCK_NUMBER)).willReturn(blockResult);

            // when
            worker.processBlock(DEFAULT_BLOCK_NUMBER);

            // then
            var counter = meterRegistry.find("indexer.blocks.processed")
                    .tag("chain", "ETHEREUM")
                    .counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("increments transfers matched counter for each confirmed transfer")
        void incrementsTransfersMatchedCounterForEachConfirmedTransfer() {
            // given
            var transfer1 = aTransfer().toAddress(DEFAULT_TO_ADDRESS).build();
            var transfer2 = aTransfer().toAddress(SECOND_TO_ADDRESS).logIndex(8).build();
            var blockResult = aBlockResult()
                    .transfers(List.of(transfer1, transfer2))
                    .build();
            given(chainIndexer.indexBlock(DEFAULT_BLOCK_NUMBER)).willReturn(blockResult);
            given(addressFilter.mightContain(DEFAULT_TO_ADDRESS, EVM)).willReturn(true);
            given(addressFilter.mightContain(SECOND_TO_ADDRESS, EVM)).willReturn(true);
            given(walletAddressRepository.existsByAddressAndNetworkType(DEFAULT_TO_ADDRESS, EVM))
                    .willReturn(true);
            given(walletAddressRepository.existsByAddressAndNetworkType(SECOND_TO_ADDRESS, EVM))
                    .willReturn(true);

            // when
            worker.processBlock(DEFAULT_BLOCK_NUMBER);

            // then
            var counter = meterRegistry.find("indexer.transfers.matched")
                    .tag("chain", "ETHEREUM")
                    .counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(2.0);
        }

        @Test
        @DisplayName("does not increment transfers matched counter when no transfers match")
        void doesNotIncrementTransfersMatchedCounterWhenNoTransfersMatch() {
            // given
            var blockResult = aBlockResult().transfers(List.of()).build();
            given(chainIndexer.indexBlock(DEFAULT_BLOCK_NUMBER)).willReturn(blockResult);

            // when
            worker.processBlock(DEFAULT_BLOCK_NUMBER);

            // then
            var counter = meterRegistry.find("indexer.transfers.matched")
                    .tag("chain", "ETHEREUM")
                    .counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(0.0);
        }
    }

    @Nested
    @DisplayName("getChainId")
    class GetChainId {

        @Test
        @DisplayName("delegates to chain indexer")
        void delegatesToChainIndexer() {
            // given — chainIndexer stubbed in setUp

            // when
            var chainId = worker.getChainId();

            // then
            assertThat(chainId).isEqualTo(ETHEREUM);
        }
    }

    @Nested
    @DisplayName("two-way indexing")
    class TwoWayIndexing {

        private TwoWayTestWorker twoWayWorker;

        @BeforeEach
        void setUp() {
            twoWayWorker = new TwoWayTestWorker(
                    chainIndexer, addressFilter, transferEventPublisher,
                    blockProgressStore, walletAddressRepository, meterRegistry);
        }

        @Test
        @DisplayName("emits INCOMING event when only toAddress matches")
        void emitsIncomingEventWhenOnlyToAddressMatches() {
            // given
            var transfer = aTransfer().build();
            var blockResult = aBlockResult().transfers(List.of(transfer)).build();
            given(chainIndexer.indexBlock(DEFAULT_BLOCK_NUMBER)).willReturn(blockResult);
            given(addressFilter.mightContain(DEFAULT_TO_ADDRESS, EVM)).willReturn(true);
            given(walletAddressRepository.existsByAddressAndNetworkType(DEFAULT_TO_ADDRESS, EVM))
                    .willReturn(true);
            given(addressFilter.mightContain(DEFAULT_FROM_ADDRESS, EVM)).willReturn(false);

            // when
            twoWayWorker.processBlock(DEFAULT_BLOCK_NUMBER);

            // then
            then(transferEventPublisher).should().publishAll(eventsCaptor.capture());
            assertThat(eventsCaptor.getValue()).hasSize(1);
            assertThat(eventsCaptor.getValue().getFirst().direction()).isEqualTo(INCOMING);
        }

        @Test
        @DisplayName("emits OUTGOING event when only fromAddress matches")
        void emitsOutgoingEventWhenOnlyFromAddressMatches() {
            // given
            var transfer = aTransfer().build();
            var blockResult = aBlockResult().transfers(List.of(transfer)).build();
            given(chainIndexer.indexBlock(DEFAULT_BLOCK_NUMBER)).willReturn(blockResult);
            given(addressFilter.mightContain(DEFAULT_TO_ADDRESS, EVM)).willReturn(false);
            given(addressFilter.mightContain(DEFAULT_FROM_ADDRESS, EVM)).willReturn(true);
            given(walletAddressRepository.existsByAddressAndNetworkType(DEFAULT_FROM_ADDRESS, EVM))
                    .willReturn(true);

            // when
            twoWayWorker.processBlock(DEFAULT_BLOCK_NUMBER);

            // then
            then(transferEventPublisher).should().publishAll(eventsCaptor.capture());
            assertThat(eventsCaptor.getValue()).hasSize(1);
            assertThat(eventsCaptor.getValue().getFirst().direction()).isEqualTo(OUTGOING);
        }

        @Test
        @DisplayName("emits both INCOMING and OUTGOING events when both addresses match")
        void emitsBothEventsWhenBothAddressesMatch() {
            // given
            var transfer = aTransfer().build();
            var blockResult = aBlockResult().transfers(List.of(transfer)).build();
            given(chainIndexer.indexBlock(DEFAULT_BLOCK_NUMBER)).willReturn(blockResult);
            given(addressFilter.mightContain(DEFAULT_TO_ADDRESS, EVM)).willReturn(true);
            given(walletAddressRepository.existsByAddressAndNetworkType(DEFAULT_TO_ADDRESS, EVM))
                    .willReturn(true);
            given(addressFilter.mightContain(DEFAULT_FROM_ADDRESS, EVM)).willReturn(true);
            given(walletAddressRepository.existsByAddressAndNetworkType(DEFAULT_FROM_ADDRESS, EVM))
                    .willReturn(true);

            // when
            twoWayWorker.processBlock(DEFAULT_BLOCK_NUMBER);

            // then
            then(transferEventPublisher).should().publishAll(eventsCaptor.capture());
            assertThat(eventsCaptor.getValue()).hasSize(2);
            assertThat(eventsCaptor.getValue().get(0).direction()).isEqualTo(INCOMING);
            assertThat(eventsCaptor.getValue().get(1).direction()).isEqualTo(OUTGOING);
        }

        @Test
        @DisplayName("does not emit OUTGOING when two-way indexing is disabled")
        void doesNotEmitOutgoingWhenTwoWayDisabled() {
            // given
            var transfer = aTransfer().build();
            var blockResult = aBlockResult().transfers(List.of(transfer)).build();
            given(chainIndexer.indexBlock(DEFAULT_BLOCK_NUMBER)).willReturn(blockResult);
            given(addressFilter.mightContain(DEFAULT_TO_ADDRESS, EVM)).willReturn(true);
            given(walletAddressRepository.existsByAddressAndNetworkType(DEFAULT_TO_ADDRESS, EVM))
                    .willReturn(true);
            lenient().when(addressFilter.mightContain(DEFAULT_FROM_ADDRESS, EVM)).thenReturn(true);
            lenient().when(walletAddressRepository.existsByAddressAndNetworkType(DEFAULT_FROM_ADDRESS, EVM))
                    .thenReturn(true);

            // when
            worker.processBlock(DEFAULT_BLOCK_NUMBER);

            // then
            then(transferEventPublisher).should().publishAll(eventsCaptor.capture());
            assertThat(eventsCaptor.getValue()).hasSize(1);
            assertThat(eventsCaptor.getValue().getFirst().direction()).isEqualTo(INCOMING);
        }

        @Test
        @DisplayName("emits no events when neither address matches")
        void emitsNoEventsWhenNeitherAddressMatches() {
            // given
            var transfer = aTransfer().build();
            var blockResult = aBlockResult().transfers(List.of(transfer)).build();
            given(chainIndexer.indexBlock(DEFAULT_BLOCK_NUMBER)).willReturn(blockResult);
            given(addressFilter.mightContain(DEFAULT_TO_ADDRESS, EVM)).willReturn(false);
            given(addressFilter.mightContain(DEFAULT_FROM_ADDRESS, EVM)).willReturn(false);

            // when
            twoWayWorker.processBlock(DEFAULT_BLOCK_NUMBER);

            // then
            then(transferEventPublisher).shouldHaveNoInteractions();
        }
    }

    private static class TestWorker extends BaseWorker {

        TestWorker(
                ChainIndexer chainIndexer,
                AddressFilter addressFilter,
                TransferEventPublisher transferEventPublisher,
                BlockProgressStore blockProgressStore,
                WalletAddressRepository walletAddressRepository,
                MeterRegistry meterRegistry) {
            super(chainIndexer, addressFilter, transferEventPublisher,
                    blockProgressStore, walletAddressRepository, meterRegistry);
        }

        @Override
        public WorkerType getWorkerType() {
            return REGULAR;
        }
    }

    private static class TwoWayTestWorker extends BaseWorker {

        TwoWayTestWorker(
                ChainIndexer chainIndexer,
                AddressFilter addressFilter,
                TransferEventPublisher transferEventPublisher,
                BlockProgressStore blockProgressStore,
                WalletAddressRepository walletAddressRepository,
                MeterRegistry meterRegistry) {
            super(chainIndexer, addressFilter, transferEventPublisher,
                    blockProgressStore, walletAddressRepository, meterRegistry);
        }

        @Override
        public WorkerType getWorkerType() {
            return REGULAR;
        }

        @Override
        protected boolean isTwoWayIndexingEnabled() {
            return true;
        }
    }
}
