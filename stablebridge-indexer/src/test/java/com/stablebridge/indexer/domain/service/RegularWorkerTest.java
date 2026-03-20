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

import java.time.Duration;
import java.util.List;
import java.util.OptionalLong;

import static com.stablebridge.indexer.domain.model.ChainId.ETHEREUM;
import static com.stablebridge.indexer.domain.model.WorkerType.REGULAR;
import static com.stablebridge.indexer.testutil.TransferFixtures.aBlockResult;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("RegularWorker")
class RegularWorkerTest {

    private static final Duration POLL_INTERVAL = Duration.ofSeconds(12);
    private static final int BATCH_SIZE = 10;
    private static final long START_BLOCK = 19_500_000L;

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

    private RegularWorker worker;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        given(chainIndexer.getChainId()).willReturn(ETHEREUM);
        worker = new RegularWorker(
                chainIndexer, addressFilter, transferEventPublisher,
                blockProgressStore, walletAddressRepository, meterRegistry,
                POLL_INTERVAL, BATCH_SIZE, START_BLOCK);
    }

    @Nested
    @DisplayName("poll")
    class Poll {

        @Test
        @DisplayName("processes blocks from last processed plus one to latest finalized")
        void processesBlocksFromLastProcessedPlusOneToLatestFinalized() {
            // given
            given(blockProgressStore.getLastProcessedBlock(ETHEREUM)).willReturn(OptionalLong.of(100L));
            given(chainIndexer.getLatestFinalizedBlockNumber()).willReturn(103L);
            given(chainIndexer.indexBlock(101L)).willReturn(aBlockResult().transfers(List.of()).build());
            given(chainIndexer.indexBlock(102L)).willReturn(aBlockResult().transfers(List.of()).build());
            given(chainIndexer.indexBlock(103L)).willReturn(aBlockResult().transfers(List.of()).build());

            // when
            worker.poll();

            // then
            then(chainIndexer).should().indexBlock(101L);
            then(chainIndexer).should().indexBlock(102L);
            then(chainIndexer).should().indexBlock(103L);
        }

        @Test
        @DisplayName("processes blocks from start block when no progress exists")
        void processesBlocksFromStartBlockWhenNoProgressExists() {
            // given
            given(blockProgressStore.getLastProcessedBlock(ETHEREUM)).willReturn(OptionalLong.empty());
            given(chainIndexer.getLatestFinalizedBlockNumber()).willReturn(START_BLOCK + 2);
            given(chainIndexer.indexBlock(START_BLOCK)).willReturn(aBlockResult().transfers(List.of()).build());
            given(chainIndexer.indexBlock(START_BLOCK + 1)).willReturn(aBlockResult().transfers(List.of()).build());
            given(chainIndexer.indexBlock(START_BLOCK + 2)).willReturn(aBlockResult().transfers(List.of()).build());

            // when
            worker.poll();

            // then
            then(chainIndexer).should().indexBlock(START_BLOCK);
            then(chainIndexer).should().indexBlock(START_BLOCK + 1);
            then(chainIndexer).should().indexBlock(START_BLOCK + 2);
        }

        @Test
        @DisplayName("does nothing when no new finalized blocks")
        void doesNothingWhenNoNewFinalizedBlocks() {
            // given
            given(blockProgressStore.getLastProcessedBlock(ETHEREUM)).willReturn(OptionalLong.of(100L));
            given(chainIndexer.getLatestFinalizedBlockNumber()).willReturn(100L);

            // when
            worker.poll();

            // then
            then(chainIndexer).should(never()).indexBlock(101L);
            then(blockProgressStore).should(never()).saveLastProcessedBlock(ETHEREUM, 101L);
        }

        @Test
        @DisplayName("limits processing to batch size")
        void limitsProcessingToBatchSize() {
            // given
            given(blockProgressStore.getLastProcessedBlock(ETHEREUM)).willReturn(OptionalLong.of(100L));
            given(chainIndexer.getLatestFinalizedBlockNumber()).willReturn(200L);
            for (var block = 101L; block <= 110L; block++) {
                given(chainIndexer.indexBlock(block)).willReturn(aBlockResult().transfers(List.of()).build());
            }

            // when
            worker.poll();

            // then
            for (var block = 101L; block <= 110L; block++) {
                then(chainIndexer).should().indexBlock(block);
            }
            then(chainIndexer).should(never()).indexBlock(111L);
        }

        @Test
        @DisplayName("logs warning when lag exceeds threshold")
        void logsWarningWhenLagExceedsThreshold() {
            // given
            given(blockProgressStore.getLastProcessedBlock(ETHEREUM)).willReturn(OptionalLong.of(100L));
            given(chainIndexer.getLatestFinalizedBlockNumber()).willReturn(300L);
            for (var block = 101L; block <= 110L; block++) {
                given(chainIndexer.indexBlock(block)).willReturn(aBlockResult().transfers(List.of()).build());
            }

            // when
            worker.poll();

            // then — still processes batch without exception
            for (var block = 101L; block <= 110L; block++) {
                then(chainIndexer).should().indexBlock(block);
            }
            then(chainIndexer).should(never()).indexBlock(111L);
        }

        @Test
        @DisplayName("updates chain lag gauge after polling")
        void updatesChainLagGaugeAfterPolling() {
            // given
            given(chainIndexer.getLatestFinalizedBlockNumber()).willReturn(150L);
            given(blockProgressStore.getLastProcessedBlock(ETHEREUM)).willReturn(OptionalLong.of(100L));
            for (var block = 101L; block <= 110L; block++) {
                given(chainIndexer.indexBlock(block)).willReturn(aBlockResult().transfers(List.of()).build());
            }

            // when
            worker.poll();

            // then
            var lagGauge = meterRegistry.find("indexer.chain.lag").tag("chain", "ETHEREUM").gauge();
            assertThat(lagGauge).isNotNull();
            assertThat(lagGauge.value()).isEqualTo(50.0);
        }

        @Test
        @DisplayName("processes exactly one block when one block behind")
        void processesExactlyOneBlockWhenOneBlockBehind() {
            // given
            given(blockProgressStore.getLastProcessedBlock(ETHEREUM)).willReturn(OptionalLong.of(100L));
            given(chainIndexer.getLatestFinalizedBlockNumber()).willReturn(101L);
            given(chainIndexer.indexBlock(101L)).willReturn(aBlockResult().transfers(List.of()).build());

            // when
            worker.poll();

            // then
            then(chainIndexer).should().indexBlock(101L);
            then(chainIndexer).should(never()).indexBlock(102L);
        }
    }

    @Nested
    @DisplayName("getWorkerType")
    class GetWorkerType {

        @Test
        @DisplayName("returns REGULAR")
        void returnsRegular() {
            // given — worker created in setUp

            // when
            var workerType = worker.getWorkerType();

            // then
            assertThat(workerType).isEqualTo(REGULAR);
        }
    }

    @Nested
    @DisplayName("configuration")
    class Configuration {

        @Test
        @DisplayName("exposes poll interval")
        void exposesPollInterval() {
            // given — worker created in setUp

            // when
            var interval = worker.getPollInterval();

            // then
            assertThat(interval).isEqualTo(POLL_INTERVAL);
        }

        @Test
        @DisplayName("exposes batch size")
        void exposesBatchSize() {
            // given — worker created in setUp

            // when
            var size = worker.getBatchSize();

            // then
            assertThat(size).isEqualTo(BATCH_SIZE);
        }

        @Test
        @DisplayName("exposes start block")
        void exposesStartBlock() {
            // given — worker created in setUp

            // when
            var block = worker.getStartBlock();

            // then
            assertThat(block).isEqualTo(START_BLOCK);
        }
    }
}
