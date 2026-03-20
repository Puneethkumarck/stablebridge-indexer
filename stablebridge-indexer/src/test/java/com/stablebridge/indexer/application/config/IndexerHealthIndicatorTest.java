package com.stablebridge.indexer.application.config;

import com.stablebridge.indexer.domain.event.TransferDetectedEvent;
import com.stablebridge.indexer.domain.model.BlockResult;
import com.stablebridge.indexer.domain.model.ChainId;
import com.stablebridge.indexer.domain.model.NetworkType;
import com.stablebridge.indexer.domain.model.WalletAddress;
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
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

import static com.stablebridge.indexer.domain.model.ChainId.ETHEREUM;
import static com.stablebridge.indexer.domain.model.WorkerType.REGULAR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

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
        var meterRegistry = new SimpleMeterRegistry();
        return new TestWorker(meterRegistry);
    }

    private static class TestWorker extends BaseWorker {

        TestWorker(MeterRegistry meterRegistry) {
            super(
                    new StubChainIndexer(),
                    new StubAddressFilter(),
                    new StubTransferEventPublisher(),
                    new StubBlockProgressStore(),
                    new StubWalletAddressRepository(),
                    meterRegistry);
        }

        @Override
        public WorkerType getWorkerType() {
            return REGULAR;
        }
    }

    private static class StubChainIndexer implements ChainIndexer {
        @Override
        public BlockResult indexBlock(long blockNumber) {
            return null;
        }

        @Override
        public long getLatestFinalizedBlockNumber() {
            return 0;
        }

        @Override
        public ChainId getChainId() {
            return ETHEREUM;
        }
    }

    private static class StubAddressFilter implements AddressFilter {
        @Override
        public boolean mightContain(String address, NetworkType networkType) {
            return false;
        }

        @Override
        public boolean contains(String address, NetworkType networkType) {
            return false;
        }

        @Override
        public void add(String address, NetworkType networkType) {
        }

        @Override
        public void remove(String address, NetworkType networkType) {
        }
    }

    private static class StubTransferEventPublisher implements TransferEventPublisher {
        @Override
        public void publish(TransferDetectedEvent event) {
        }

        @Override
        public void publishAll(List<TransferDetectedEvent> events) {
        }
    }

    private static class StubBlockProgressStore implements BlockProgressStore {
        @Override
        public OptionalLong getLastProcessedBlock(ChainId chainId) {
            return OptionalLong.empty();
        }

        @Override
        public void saveLastProcessedBlock(ChainId chainId, long blockNumber) {
        }

        @Override
        public void addFailedBlock(ChainId chainId, long blockNumber) {
        }

        @Override
        public Set<Long> getFailedBlocks(ChainId chainId) {
            return Set.of();
        }

        @Override
        public void removeFailedBlock(ChainId chainId, long blockNumber) {
        }

        @Override
        public void saveCatchupRange(ChainId chainId, long fromBlock, long toBlock) {
        }

        @Override
        public Map<Long, Long> getCatchupRanges(ChainId chainId) {
            return Map.of();
        }

        @Override
        public void removeCatchupRange(ChainId chainId, long fromBlock) {
        }
    }

    private static class StubWalletAddressRepository implements WalletAddressRepository {
        @Override
        public WalletAddress save(WalletAddress walletAddress) {
            return walletAddress;
        }

        @Override
        public Optional<WalletAddress> findByAddressAndNetworkType(String address, NetworkType networkType) {
            return Optional.empty();
        }

        @Override
        public List<WalletAddress> findAllByNetworkType(NetworkType networkType) {
            return List.of();
        }

        @Override
        public boolean existsByAddressAndNetworkType(String address, NetworkType networkType) {
            return false;
        }

        @Override
        public void deleteByAddressAndNetworkType(String address, NetworkType networkType) {
        }
    }
}
