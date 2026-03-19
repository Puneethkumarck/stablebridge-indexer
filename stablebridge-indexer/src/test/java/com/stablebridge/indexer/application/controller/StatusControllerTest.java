package com.stablebridge.indexer.application.controller;

import com.stablebridge.indexer.domain.model.BloomStatus;
import com.stablebridge.indexer.domain.model.ChainStatus;
import com.stablebridge.indexer.domain.model.NetworkType;
import com.stablebridge.indexer.domain.model.WorkerState;
import com.stablebridge.indexer.domain.service.StatusQueryHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StatusController.class)
@DisplayName("StatusController")
class StatusControllerTest {

    private static final String ETHEREUM_CHAIN = "ethereum_mainnet";
    private static final long ETHEREUM_LAST_BLOCK = 19_500_000L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StatusQueryHandler statusQueryHandler;

    @MockitoBean
    private StatusControllerMapper mapper;

    @Nested
    @DisplayName("GET /api/v1/status")
    class GetAllChainStatuses {

        @Test
        @DisplayName("returns 200 with list of chain statuses")
        void returnsAllChainStatuses() throws Exception {
            List<ChainStatus> domainStatuses = List.of(
                    ChainStatus.builder()
                            .chainName(ETHEREUM_CHAIN)
                            .networkType(NetworkType.EVM)
                            .workerState(WorkerState.STOPPED)
                            .lastProcessedBlock(ETHEREUM_LAST_BLOCK)
                            .latestFinalizedBlock(null)
                            .blocksBehind(null)
                            .enabled(true)
                            .build()
            );

            com.stablebridge.indexer.api.IndexerStatusResponse response =
                    new com.stablebridge.indexer.api.IndexerStatusResponse(
                            ETHEREUM_CHAIN, "EVM", "STOPPED",
                            ETHEREUM_LAST_BLOCK, null, null, true);

            given(statusQueryHandler.getAllChainStatuses()).willReturn(domainStatuses);
            given(mapper.toResponseList(domainStatuses)).willReturn(List.of(response));

            mockMvc.perform(get("/api/v1/status"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].chainId").value(ETHEREUM_CHAIN))
                    .andExpect(jsonPath("$[0].networkType").value("EVM"))
                    .andExpect(jsonPath("$[0].workerState").value("STOPPED"))
                    .andExpect(jsonPath("$[0].lastProcessedBlock").value(ETHEREUM_LAST_BLOCK))
                    .andExpect(jsonPath("$[0].enabled").value(true));

            then(statusQueryHandler).should().getAllChainStatuses();
            then(mapper).should().toResponseList(domainStatuses);
        }
    }

    @Nested
    @DisplayName("GET /api/v1/status/{chainName}")
    class GetChainStatus {

        @Test
        @DisplayName("returns 200 with chain status when chain is configured")
        void returnsChainStatusWhenConfigured() throws Exception {
            ChainStatus domainStatus = ChainStatus.builder()
                    .chainName(ETHEREUM_CHAIN)
                    .networkType(NetworkType.EVM)
                    .workerState(WorkerState.STOPPED)
                    .lastProcessedBlock(ETHEREUM_LAST_BLOCK)
                    .latestFinalizedBlock(null)
                    .blocksBehind(null)
                    .enabled(true)
                    .build();

            com.stablebridge.indexer.api.IndexerStatusResponse response =
                    new com.stablebridge.indexer.api.IndexerStatusResponse(
                            ETHEREUM_CHAIN, "EVM", "STOPPED",
                            ETHEREUM_LAST_BLOCK, null, null, true);

            given(statusQueryHandler.getChainStatus(ETHEREUM_CHAIN))
                    .willReturn(Optional.of(domainStatus));
            given(mapper.toResponse(domainStatus)).willReturn(response);

            mockMvc.perform(get("/api/v1/status/{chainName}", ETHEREUM_CHAIN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.chainId").value(ETHEREUM_CHAIN))
                    .andExpect(jsonPath("$.networkType").value("EVM"))
                    .andExpect(jsonPath("$.workerState").value("STOPPED"))
                    .andExpect(jsonPath("$.lastProcessedBlock").value(ETHEREUM_LAST_BLOCK))
                    .andExpect(jsonPath("$.enabled").value(true));

            then(statusQueryHandler).should().getChainStatus(ETHEREUM_CHAIN);
            then(mapper).should().toResponse(domainStatus);
        }

        @Test
        @DisplayName("returns 404 when chain is not configured")
        void returnsNotFoundWhenChainNotConfigured() throws Exception {
            String unknownChain = "unknown_chain";

            given(statusQueryHandler.getChainStatus(unknownChain))
                    .willReturn(Optional.empty());

            mockMvc.perform(get("/api/v1/status/{chainName}", unknownChain))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.error").value("Not Found"))
                    .andExpect(jsonPath("$.message").value("Chain not configured: " + unknownChain));

            then(statusQueryHandler).should().getChainStatus(unknownChain);
        }
    }

    @Nested
    @DisplayName("GET /api/v1/status/bloom")
    class GetBloomStatus {

        @Test
        @DisplayName("returns 200 with bloom filter status")
        void returnsBloomStatus() throws Exception {
            BloomStatus domainBloomStatus = BloomStatus.builder()
                    .backend("redis")
                    .expectedInsertions(1_000_000L)
                    .errorRate(0.001)
                    .networkTypes(List.of(NetworkType.EVM, NetworkType.SOLANA, NetworkType.BITCOIN))
                    .build();

            com.stablebridge.indexer.api.BloomStatusResponse response =
                    new com.stablebridge.indexer.api.BloomStatusResponse(
                            "redis", 1_000_000L, 0.001,
                            List.of("EVM", "SOLANA", "BITCOIN"));

            given(statusQueryHandler.getBloomStatus()).willReturn(domainBloomStatus);
            given(mapper.toBloomResponse(domainBloomStatus)).willReturn(response);

            mockMvc.perform(get("/api/v1/status/bloom"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.backend").value("redis"))
                    .andExpect(jsonPath("$.expectedInsertions").value(1_000_000))
                    .andExpect(jsonPath("$.errorRate").value(0.001))
                    .andExpect(jsonPath("$.networkTypes[0]").value("EVM"))
                    .andExpect(jsonPath("$.networkTypes[1]").value("SOLANA"))
                    .andExpect(jsonPath("$.networkTypes[2]").value("BITCOIN"));

            then(statusQueryHandler).should().getBloomStatus();
            then(mapper).should().toBloomResponse(domainBloomStatus);
        }
    }
}
