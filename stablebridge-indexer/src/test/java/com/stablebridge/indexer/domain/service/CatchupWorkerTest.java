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
import java.util.Map;

import static com.stablebridge.indexer.domain.model.ChainId.ETHEREUM;
import static com.stablebridge.indexer.domain.model.WorkerType.CATCHUP;
import static com.stablebridge.indexer.testutil.TransferFixtures.aBlockResult;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("CatchupWorker")
class CatchupWorkerTest {

    private static final int CHUNK_SIZE = 5;

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

    private CatchupWorker worker;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        given(chainIndexer.getChainId()).willReturn(ETHEREUM);
        worker = new CatchupWorker(
                chainIndexer, addressFilter, transferEventPublisher,
                blockProgressStore, walletAddressRepository, meterRegistry,
                CHUNK_SIZE);
    }

    @Nested
    @DisplayName("processRanges")
    class ProcessRanges {

        @Test
        @DisplayName("processes all blocks in a single range")
        void processesAllBlocksInSingleRange() {
            // given
            given(blockProgressStore.getCatchupRanges(ETHEREUM))
                    .willReturn(Map.of(100L, 104L));
            var emptyBlockResult = aBlockResult().transfers(List.of()).build();
            given(chainIndexer.indexBlock(100L)).willReturn(emptyBlockResult);
            given(chainIndexer.indexBlock(101L)).willReturn(emptyBlockResult);
            given(chainIndexer.indexBlock(102L)).willReturn(emptyBlockResult);
            given(chainIndexer.indexBlock(103L)).willReturn(emptyBlockResult);
            given(chainIndexer.indexBlock(104L)).willReturn(emptyBlockResult);

            // when
            worker.processRanges();

            // then
            then(chainIndexer).should().indexBlock(100L);
            then(chainIndexer).should().indexBlock(101L);
            then(chainIndexer).should().indexBlock(102L);
            then(chainIndexer).should().indexBlock(103L);
            then(chainIndexer).should().indexBlock(104L);
        }

        @Test
        @DisplayName("processes multiple ranges")
        void processesMultipleRanges() {
            // given
            given(blockProgressStore.getCatchupRanges(ETHEREUM))
                    .willReturn(Map.of(100L, 102L, 200L, 201L));
            var emptyBlockResult = aBlockResult().transfers(List.of()).build();
            given(chainIndexer.indexBlock(100L)).willReturn(emptyBlockResult);
            given(chainIndexer.indexBlock(101L)).willReturn(emptyBlockResult);
            given(chainIndexer.indexBlock(102L)).willReturn(emptyBlockResult);
            given(chainIndexer.indexBlock(200L)).willReturn(emptyBlockResult);
            given(chainIndexer.indexBlock(201L)).willReturn(emptyBlockResult);

            // when
            worker.processRanges();

            // then
            then(chainIndexer).should().indexBlock(100L);
            then(chainIndexer).should().indexBlock(101L);
            then(chainIndexer).should().indexBlock(102L);
            then(chainIndexer).should().indexBlock(200L);
            then(chainIndexer).should().indexBlock(201L);
        }

        @Test
        @DisplayName("does nothing when no ranges exist")
        void doesNothingWhenNoRangesExist() {
            // given
            given(blockProgressStore.getCatchupRanges(ETHEREUM))
                    .willReturn(Map.of());

            // when
            worker.processRanges();

            // then
            then(chainIndexer).should(never()).indexBlock(100L);
        }

        @Test
        @DisplayName("removes range after processing")
        void removesRangeAfterProcessing() {
            // given
            given(blockProgressStore.getCatchupRanges(ETHEREUM))
                    .willReturn(Map.of(100L, 102L));
            var emptyBlockResult = aBlockResult().transfers(List.of()).build();
            given(chainIndexer.indexBlock(100L)).willReturn(emptyBlockResult);
            given(chainIndexer.indexBlock(101L)).willReturn(emptyBlockResult);
            given(chainIndexer.indexBlock(102L)).willReturn(emptyBlockResult);

            // when
            worker.processRanges();

            // then
            then(blockProgressStore).should().removeCatchupRange(ETHEREUM, 100L);
        }

        @Test
        @DisplayName("adds failed block to failed set on error")
        void addsFailedBlockToFailedSetOnError() {
            // given
            given(blockProgressStore.getCatchupRanges(ETHEREUM))
                    .willReturn(Map.of(100L, 104L));
            var emptyBlockResult = aBlockResult().transfers(List.of()).build();
            given(chainIndexer.indexBlock(100L)).willReturn(emptyBlockResult);
            given(chainIndexer.indexBlock(101L)).willReturn(emptyBlockResult);
            given(chainIndexer.indexBlock(102L)).willThrow(new RuntimeException("RPC error"));
            given(chainIndexer.indexBlock(103L)).willReturn(emptyBlockResult);
            given(chainIndexer.indexBlock(104L)).willReturn(emptyBlockResult);

            // when
            worker.processRanges();

            // then
            then(blockProgressStore).should().addFailedBlock(ETHEREUM, 102L);
        }
    }

    @Nested
    @DisplayName("getWorkerType")
    class GetWorkerType {

        @Test
        @DisplayName("returns CATCHUP")
        void returnsCatchup() {
            // given — worker created in setUp

            // when
            var workerType = worker.getWorkerType();

            // then
            assertThat(workerType).isEqualTo(CATCHUP);
        }
    }

    @Nested
    @DisplayName("configuration")
    class Configuration {

        @Test
        @DisplayName("exposes chunk size")
        void exposesChunkSize() {
            // given — worker created in setUp

            // when
            var result = worker.getChunkSize();

            // then
            assertThat(result).isEqualTo(CHUNK_SIZE);
        }
    }
}
