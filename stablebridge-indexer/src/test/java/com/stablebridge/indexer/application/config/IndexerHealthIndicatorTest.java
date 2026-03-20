package com.stablebridge.indexer.application.config;

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
import org.springframework.boot.health.contributor.Status;

import java.util.List;

import static com.stablebridge.indexer.domain.model.ChainId.ETHEREUM;
import static com.stablebridge.indexer.domain.model.WorkerType.REGULAR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@DisplayName("IndexerHealthIndicator")
class IndexerHealthIndicatorTest {

    @Mock
    private IndexerOrchestrator orchestrator;

    private IndexerHealthIndicator healthIndicator;

    @BeforeEach
    void setUp() {
        healthIndicator = new IndexerHealthIndicator(orchestrator);
    }

    @Test
    @DisplayName("reports UP when all workers are running")
    void reportsUpWhenAllWorkersRunning() {
        // given
        var worker = createTestWorker();
        worker.start();
        given(orchestrator.getWorkers()).willReturn(List.of(worker));

        // when
        var health = healthIndicator.health();

        // then
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsKey("chains");
    }

    @Test
    @DisplayName("reports DOWN when a worker is stopped and orchestrator is running")
    void reportsDownWhenWorkerStopped() {
        // given
        var worker = createTestWorker();
        given(orchestrator.getWorkers()).willReturn(List.of(worker));
        given(orchestrator.isRunning()).willReturn(true);

        // when
        var health = healthIndicator.health();

        // then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsKey("chains");
    }

    @Test
    @DisplayName("reports DEGRADED when a worker is parked")
    void reportsDegradedWhenWorkerParked() {
        // given
        var worker = createTestWorker();
        worker.start();
        worker.park();
        given(orchestrator.getWorkers()).willReturn(List.of(worker));

        // when
        var health = healthIndicator.health();

        // then
        assertThat(health.getStatus()).isEqualTo(new Status("DEGRADED"));
        assertThat(health.getDetails()).containsKey("chains");
    }

    @Test
    @DisplayName("reports UNKNOWN when no chains are configured")
    void reportsUnknownWhenNoChains() {
        // given
        given(orchestrator.getWorkers()).willReturn(List.of());

        // when
        var health = healthIndicator.health();

        // then
        assertThat(health.getStatus()).isEqualTo(Status.UNKNOWN);
        assertThat(health.getDetails()).containsEntry("reason", "No chains configured");
    }

    private static TestWorker createTestWorker() {
        var chainIndexer = mock(ChainIndexer.class);
        given(chainIndexer.getChainId()).willReturn(ETHEREUM);
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
