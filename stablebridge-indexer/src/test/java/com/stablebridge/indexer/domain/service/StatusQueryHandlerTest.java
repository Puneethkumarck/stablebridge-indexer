package com.stablebridge.indexer.domain.service;

import com.stablebridge.indexer.domain.model.BloomStatus;
import com.stablebridge.indexer.domain.model.ChainConfiguration;
import com.stablebridge.indexer.domain.model.ChainId;
import com.stablebridge.indexer.domain.model.ChainStatus;
import com.stablebridge.indexer.domain.port.BlockProgressStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

import static com.stablebridge.indexer.domain.model.NetworkType.BITCOIN;
import static com.stablebridge.indexer.domain.model.NetworkType.EVM;
import static com.stablebridge.indexer.domain.model.NetworkType.SOLANA;
import static com.stablebridge.indexer.domain.model.WorkerState.STOPPED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("StatusQueryHandler")
class StatusQueryHandlerTest {

    private static final String ETHEREUM_CHAIN = "ethereum_mainnet";
    private static final String BASE_CHAIN = "base_mainnet";
    private static final long ETHEREUM_LAST_BLOCK = 19_500_000L;
    private static final long BASE_LAST_BLOCK = 12_000_000L;

    @Mock
    private BlockProgressStore blockProgressStore;

    private StatusQueryHandler queryHandler;

    private Map<String, ChainConfiguration> chainConfigurations;
    private BloomStatus bloomStatus;

    @BeforeEach
    void setUp() {
        chainConfigurations = new LinkedHashMap<>();
        chainConfigurations.put(ETHEREUM_CHAIN, ChainConfiguration.builder()
                .chainName(ETHEREUM_CHAIN)
                .networkType(EVM)
                .enabled(true)
                .build());
        chainConfigurations.put(BASE_CHAIN, ChainConfiguration.builder()
                .chainName(BASE_CHAIN)
                .networkType(EVM)
                .enabled(false)
                .build());

        bloomStatus = BloomStatus.builder()
                .backend("redis")
                .expectedInsertions(1_000_000L)
                .errorRate(0.001)
                .networkTypes(List.of(EVM, SOLANA, BITCOIN))
                .build();

        queryHandler = new StatusQueryHandler(blockProgressStore, chainConfigurations, bloomStatus);
    }

    @Nested
    @DisplayName("getAllChainStatuses")
    class GetAllChainStatuses {

        @Test
        @DisplayName("returns status for all configured chains with progress from block store")
        void returnsStatusForAllConfiguredChains() {
            given(blockProgressStore.getLastProcessedBlock(ChainId.ETHEREUM))
                    .willReturn(OptionalLong.of(ETHEREUM_LAST_BLOCK));
            given(blockProgressStore.getLastProcessedBlock(ChainId.BASE))
                    .willReturn(OptionalLong.of(BASE_LAST_BLOCK));

            var actual = queryHandler.getAllChainStatuses();

            var expected = List.of(
                    ChainStatus.builder()
                            .chainName(ETHEREUM_CHAIN)
                            .networkType(EVM)
                            .workerState(STOPPED)
                            .lastProcessedBlock(ETHEREUM_LAST_BLOCK)
                            .latestFinalizedBlock(null)
                            .blocksBehind(null)
                            .enabled(true)
                            .build(),
                    ChainStatus.builder()
                            .chainName(BASE_CHAIN)
                            .networkType(EVM)
                            .workerState(STOPPED)
                            .lastProcessedBlock(BASE_LAST_BLOCK)
                            .latestFinalizedBlock(null)
                            .blocksBehind(null)
                            .enabled(false)
                            .build()
            );
            assertThat(actual)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
            then(blockProgressStore).should().getLastProcessedBlock(ChainId.ETHEREUM);
            then(blockProgressStore).should().getLastProcessedBlock(ChainId.BASE);
        }

        @Test
        @DisplayName("returns null for lastProcessedBlock when no progress recorded")
        void returnsNullWhenNoProgress() {
            given(blockProgressStore.getLastProcessedBlock(ChainId.ETHEREUM))
                    .willReturn(OptionalLong.empty());
            given(blockProgressStore.getLastProcessedBlock(ChainId.BASE))
                    .willReturn(OptionalLong.empty());

            var actual = queryHandler.getAllChainStatuses();

            var expected = List.of(
                    ChainStatus.builder()
                            .chainName(ETHEREUM_CHAIN)
                            .networkType(EVM)
                            .workerState(STOPPED)
                            .lastProcessedBlock(null)
                            .latestFinalizedBlock(null)
                            .blocksBehind(null)
                            .enabled(true)
                            .build(),
                    ChainStatus.builder()
                            .chainName(BASE_CHAIN)
                            .networkType(EVM)
                            .workerState(STOPPED)
                            .lastProcessedBlock(null)
                            .latestFinalizedBlock(null)
                            .blocksBehind(null)
                            .enabled(false)
                            .build()
            );
            assertThat(actual)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("getChainStatus")
    class GetChainStatus {

        @Test
        @DisplayName("returns status for a configured chain")
        void returnsStatusForConfiguredChain() {
            given(blockProgressStore.getLastProcessedBlock(ChainId.ETHEREUM))
                    .willReturn(OptionalLong.of(ETHEREUM_LAST_BLOCK));

            var actual = queryHandler.getChainStatus(ETHEREUM_CHAIN);

            var expected = ChainStatus.builder()
                    .chainName(ETHEREUM_CHAIN)
                    .networkType(EVM)
                    .workerState(STOPPED)
                    .lastProcessedBlock(ETHEREUM_LAST_BLOCK)
                    .latestFinalizedBlock(null)
                    .blocksBehind(null)
                    .enabled(true)
                    .build();
            assertThat(actual)
                    .isPresent()
                    .hasValueSatisfying(value ->
                            assertThat(value)
                                    .usingRecursiveComparison()
                                    .isEqualTo(expected));
            then(blockProgressStore).should().getLastProcessedBlock(ChainId.ETHEREUM);
        }

        @Test
        @DisplayName("returns empty for unknown chain")
        void returnsEmptyForUnknownChain() {
            var actual = queryHandler.getChainStatus("unknown_chain");

            assertThat(actual).isEmpty();
        }
    }

    @Nested
    @DisplayName("getBloomStatus")
    class GetBloomStatus {

        @Test
        @DisplayName("returns bloom filter configuration status")
        void returnsBloomStatus() {
            var actual = queryHandler.getBloomStatus();

            var expected = BloomStatus.builder()
                    .backend("redis")
                    .expectedInsertions(1_000_000L)
                    .errorRate(0.001)
                    .networkTypes(List.of(EVM, SOLANA, BITCOIN))
                    .build();
            assertThat(actual)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }
    }
}
