package com.stablebridge.indexer.application.config;

import com.stablebridge.indexer.domain.model.ChainId;
import com.stablebridge.indexer.domain.model.WorkerType;
import com.stablebridge.indexer.domain.port.AddressFilter;
import com.stablebridge.indexer.domain.port.BlockProgressStore;
import com.stablebridge.indexer.domain.port.ChainIndexer;
import com.stablebridge.indexer.domain.port.TransferEventPublisher;
import com.stablebridge.indexer.domain.port.WalletAddressRepository;
import com.stablebridge.indexer.domain.service.BaseWorker;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.health.contributor.Health;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

import static com.stablebridge.indexer.domain.model.ChainId.BASE;
import static com.stablebridge.indexer.domain.model.ChainId.ETHEREUM;
import static com.stablebridge.indexer.domain.model.WorkerType.REGULAR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChainHealthIndicator")
class ChainHealthIndicatorTest {

    @Mock
    private IndexerOrchestrator orchestrator;

    @Mock
    private BlockProgressStore blockProgressStore;

    private ChainHealthIndicator healthIndicator;

    private ChainIndexer ethereumChainIndexer;

    @BeforeEach
    void setUp() {
        ethereumChainIndexer = mock(ChainIndexer.class);
    }

    private void initializeWithEthereumChain() {
        given(ethereumChainIndexer.getChainId()).willReturn(ETHEREUM);
        healthIndicator = new ChainHealthIndicator(
                orchestrator, blockProgressStore, List.of(ethereumChainIndexer));
    }

    @Test
    @DisplayName("reports UP with chain details when a worker is running")
    void reportsUpWhenWorkerRunning() {
        // given
        initializeWithEthereumChain();
        var worker = createTestWorker(ETHEREUM);
        worker.start();
        given(orchestrator.getWorkers()).willReturn(List.of(worker));
        given(blockProgressStore.getLastProcessedBlock(ETHEREUM)).willReturn(OptionalLong.of(12_345_678L));
        given(ethereumChainIndexer.getLatestFinalizedBlockNumber()).willReturn(12_345_700L);

        var chainDetail = new LinkedHashMap<String, Object>();
        chainDetail.put("state", "RUNNING");
        chainDetail.put("latestBlock", 12_345_678L);
        chainDetail.put("chainHead", 12_345_700L);
        chainDetail.put("lag", 22L);

        var expected = Health.up()
                .withDetail("chains", Map.of("ETHEREUM", chainDetail))
                .build();

        // when
        var actual = healthIndicator.health();

        // then
        assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    @DisplayName("reports DOWN when all workers are stopped")
    void reportsDownWhenAllWorkersStopped() {
        // given
        initializeWithEthereumChain();
        var worker = createTestWorker(ETHEREUM);
        given(orchestrator.getWorkers()).willReturn(List.of(worker));
        given(blockProgressStore.getLastProcessedBlock(ETHEREUM)).willReturn(OptionalLong.empty());
        given(ethereumChainIndexer.getLatestFinalizedBlockNumber()).willReturn(12_345_700L);

        var chainDetail = new LinkedHashMap<String, Object>();
        chainDetail.put("state", "STOPPED");
        chainDetail.put("chainHead", 12_345_700L);

        var expected = Health.down()
                .withDetail("chains", Map.of("ETHEREUM", chainDetail))
                .build();

        // when
        var actual = healthIndicator.health();

        // then
        assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    @DisplayName("reports DOWN when all workers are parked")
    void reportsDownWhenAllWorkersParked() {
        // given
        initializeWithEthereumChain();
        var worker = createTestWorker(ETHEREUM);
        worker.start();
        worker.park();
        given(orchestrator.getWorkers()).willReturn(List.of(worker));
        given(blockProgressStore.getLastProcessedBlock(ETHEREUM)).willReturn(OptionalLong.of(12_345_000L));
        given(ethereumChainIndexer.getLatestFinalizedBlockNumber()).willReturn(12_345_700L);

        var chainDetail = new LinkedHashMap<String, Object>();
        chainDetail.put("state", "PARKED");
        chainDetail.put("latestBlock", 12_345_000L);
        chainDetail.put("chainHead", 12_345_700L);
        chainDetail.put("lag", 700L);

        var expected = Health.down()
                .withDetail("chains", Map.of("ETHEREUM", chainDetail))
                .build();

        // when
        var actual = healthIndicator.health();

        // then
        assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    @DisplayName("reports UNKNOWN when no chains are configured")
    void reportsUnknownWhenNoChains() {
        // given
        healthIndicator = new ChainHealthIndicator(
                orchestrator, blockProgressStore, List.of());
        given(orchestrator.getWorkers()).willReturn(List.of());

        var expected = Health.unknown()
                .withDetail("reason", "No chains configured")
                .build();

        // when
        var actual = healthIndicator.health();

        // then
        assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    @DisplayName("reports chain head as unavailable when RPC call fails")
    void reportsChainHeadUnavailableWhenRpcFails() {
        // given
        initializeWithEthereumChain();
        var worker = createTestWorker(ETHEREUM);
        worker.start();
        given(orchestrator.getWorkers()).willReturn(List.of(worker));
        given(blockProgressStore.getLastProcessedBlock(ETHEREUM)).willReturn(OptionalLong.of(12_345_678L));
        given(ethereumChainIndexer.getLatestFinalizedBlockNumber())
                .willThrow(new RuntimeException("RPC connection refused"));

        var chainDetail = new LinkedHashMap<String, Object>();
        chainDetail.put("state", "RUNNING");
        chainDetail.put("latestBlock", 12_345_678L);
        chainDetail.put("chainHead", "unavailable");

        var expected = Health.up()
                .withDetail("chains", Map.of("ETHEREUM", chainDetail))
                .build();

        // when
        var actual = healthIndicator.health();

        // then
        assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    @DisplayName("reports UP when at least one worker of multiple chains is running")
    void reportsUpWhenAtLeastOneChainRunning() {
        // given
        given(ethereumChainIndexer.getChainId()).willReturn(ETHEREUM);
        var baseChainIndexer = mock(ChainIndexer.class);
        given(baseChainIndexer.getChainId()).willReturn(BASE);

        healthIndicator = new ChainHealthIndicator(
                orchestrator, blockProgressStore, List.of(ethereumChainIndexer, baseChainIndexer));

        var ethWorker = createTestWorker(ETHEREUM);
        ethWorker.start();
        ethWorker.park();

        var baseWorker = createTestWorker(BASE);
        baseWorker.start();

        given(orchestrator.getWorkers()).willReturn(List.of(ethWorker, baseWorker));
        given(blockProgressStore.getLastProcessedBlock(ETHEREUM)).willReturn(OptionalLong.of(12_345_000L));
        given(blockProgressStore.getLastProcessedBlock(BASE)).willReturn(OptionalLong.of(5_000_000L));
        given(ethereumChainIndexer.getLatestFinalizedBlockNumber()).willReturn(12_345_700L);
        given(baseChainIndexer.getLatestFinalizedBlockNumber()).willReturn(5_000_010L);

        // when
        var actual = healthIndicator.health();

        // then
        assertThat(actual.getStatus().getCode()).isEqualTo("UP");
    }

    @Test
    @DisplayName("omits latestBlock when no progress recorded")
    void omitsLatestBlockWhenNoProgress() {
        // given
        initializeWithEthereumChain();
        var worker = createTestWorker(ETHEREUM);
        worker.start();
        given(orchestrator.getWorkers()).willReturn(List.of(worker));
        given(blockProgressStore.getLastProcessedBlock(ETHEREUM)).willReturn(OptionalLong.empty());
        given(ethereumChainIndexer.getLatestFinalizedBlockNumber()).willReturn(12_345_700L);

        var chainDetail = new LinkedHashMap<String, Object>();
        chainDetail.put("state", "RUNNING");
        chainDetail.put("chainHead", 12_345_700L);

        var expected = Health.up()
                .withDetail("chains", Map.of("ETHEREUM", chainDetail))
                .build();

        // when
        var actual = healthIndicator.health();

        // then
        assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    }

    private static TestWorker createTestWorker(ChainId chainId) {
        var chainIndexer = mock(ChainIndexer.class);
        given(chainIndexer.getChainId()).willReturn(chainId);
        return new TestWorker(
                chainIndexer,
                mock(AddressFilter.class),
                mock(TransferEventPublisher.class),
                mock(BlockProgressStore.class),
                mock(WalletAddressRepository.class),
                new SimpleMeterRegistry());
    }

    private static class TestWorker extends BaseWorker {

        TestWorker(ChainIndexer chainIndexer,
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
}
