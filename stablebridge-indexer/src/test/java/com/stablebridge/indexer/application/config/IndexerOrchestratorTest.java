package com.stablebridge.indexer.application.config;

import com.stablebridge.indexer.application.properties.ChainProperties;
import com.stablebridge.indexer.application.properties.ConfirmationStrategy;
import com.stablebridge.indexer.application.properties.IndexerProperties;
import com.stablebridge.indexer.application.properties.RpcProperties;
import com.stablebridge.indexer.application.properties.TokenContractProperties;
import com.stablebridge.indexer.domain.model.NetworkType;
import com.stablebridge.indexer.domain.port.AddressFilter;
import com.stablebridge.indexer.domain.port.BlockProgressStore;
import com.stablebridge.indexer.domain.port.ChainIndexer;
import com.stablebridge.indexer.domain.port.TransferEventPublisher;
import com.stablebridge.indexer.domain.port.WalletAddressRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.stablebridge.indexer.domain.model.ChainId.ETHEREUM;
import static com.stablebridge.indexer.domain.model.NetworkType.BITCOIN;
import static com.stablebridge.indexer.domain.model.NetworkType.EVM;
import static com.stablebridge.indexer.domain.model.NetworkType.SOLANA;
import static com.stablebridge.indexer.domain.model.WorkerState.STOPPED;
import static com.stablebridge.indexer.testutil.WalletAddressFixtures.aWalletAddress;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@DisplayName("IndexerOrchestrator")
class IndexerOrchestratorTest {

    private static final String WALLET_ADDRESS_1 = "0xabcdef1234567890abcdef1234567890abcdef12";
    private static final String WALLET_ADDRESS_2 = "0x1234567890abcdef1234567890abcdef12345678";

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

    private IndexerOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        given(chainIndexer.getChainId()).willReturn(ETHEREUM);
        stubBackgroundWorkerDefaults();
    }

    /**
     * Worker loops run on background virtual threads — their invocation is
     * non-deterministic, so these stubs must be lenient.
     */
    private void stubBackgroundWorkerDefaults() {
        lenient().when(blockProgressStore.getCatchupRanges(ETHEREUM)).thenReturn(Map.of());
        lenient().when(blockProgressStore.getFailedBlocks(ETHEREUM)).thenReturn(Set.of());
    }

    @AfterEach
    void tearDown() {
        if (orchestrator != null && orchestrator.isRunning()) {
            orchestrator.stop();
        }
    }

    @Nested
    @DisplayName("start")
    class Start {

        @Test
        @DisplayName("initializes bloom filter from database on start")
        void initializesBloomFilterFromDatabaseOnStart() {
            // given
            var wallet1 = aWalletAddress().address(WALLET_ADDRESS_1).networkType(EVM).build();
            var wallet2 = aWalletAddress().address(WALLET_ADDRESS_2).networkType(EVM).build();
            given(walletAddressRepository.findAllByNetworkType(EVM))
                    .willReturn(List.of(wallet1, wallet2));
            given(walletAddressRepository.findAllByNetworkType(SOLANA))
                    .willReturn(List.of());
            given(walletAddressRepository.findAllByNetworkType(BITCOIN))
                    .willReturn(List.of());

            orchestrator = createOrchestrator(List.of(chainIndexer));

            // when
            orchestrator.start();

            // then
            then(walletAddressRepository).should().findAllByNetworkType(EVM);
            then(walletAddressRepository).should().findAllByNetworkType(SOLANA);
            then(walletAddressRepository).should().findAllByNetworkType(BITCOIN);
            then(addressFilter).should().add(WALLET_ADDRESS_1, EVM);
            then(addressFilter).should().add(WALLET_ADDRESS_2, EVM);
        }

        @Test
        @DisplayName("creates workers for each enabled chain")
        void createsWorkersForEachEnabledChain() {
            // given
            stubEmptyWalletAddresses();
            orchestrator = createOrchestrator(List.of(chainIndexer));

            // when
            orchestrator.start();

            // then
            assertThat(orchestrator.getWorkers()).hasSize(3);
        }
    }

    @Nested
    @DisplayName("stop")
    class Stop {

        @Test
        @DisplayName("stops all workers on shutdown")
        void stopsAllWorkersOnShutdown() throws InterruptedException {
            // given
            stubEmptyWalletAddresses();
            orchestrator = createOrchestrator(List.of(chainIndexer));
            orchestrator.start();
            Thread.sleep(50); // let worker threads start before stopping

            // when
            orchestrator.stop();
            Thread.sleep(50); // let worker threads finish stopping

            // then
            assertThat(orchestrator.getWorkers())
                    .allMatch(w -> w.getState() == STOPPED);
        }
    }

    @Nested
    @DisplayName("isRunning")
    class IsRunning {

        @Test
        @DisplayName("reports running state correctly")
        void reportsRunningStateCorrectly() {
            // given
            stubEmptyWalletAddresses();
            orchestrator = createOrchestrator(List.of(chainIndexer));

            // when / then — before start
            assertThat(orchestrator.isRunning()).isFalse();

            // when — start
            orchestrator.start();

            // then — after start
            assertThat(orchestrator.isRunning()).isTrue();

            // when — stop
            orchestrator.stop();

            // then — after stop
            assertThat(orchestrator.isRunning()).isFalse();
        }
    }

    private IndexerOrchestrator createOrchestrator(List<ChainIndexer> indexers) {
        var chainProperties = anEvmChainProperties();
        var indexerProperties = new IndexerProperties(
                Map.of("ethereum_mainnet", chainProperties), null, null);

        return new IndexerOrchestrator(
                indexers,
                addressFilter,
                transferEventPublisher,
                blockProgressStore,
                walletAddressRepository,
                new SimpleMeterRegistry(),
                indexerProperties);
    }

    private void stubEmptyWalletAddresses() {
        for (var networkType : NetworkType.values()) {
            given(walletAddressRepository.findAllByNetworkType(networkType))
                    .willReturn(List.of());
        }
    }

    private static ChainProperties anEvmChainProperties() {
        return new ChainProperties(
                true,
                "evm",
                ConfirmationStrategy.FINALIZED,
                0,
                false,
                18,
                0L,
                Duration.ofSeconds(12),
                10,
                new RpcProperties(
                        List.of("https://rpc.example.com"),
                        50, false, Duration.ofSeconds(10), 3, 25, 50, null, null),
                List.of(new TokenContractProperties(
                        "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48", "USDC", 6))
        );
    }
}
