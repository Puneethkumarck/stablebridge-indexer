package com.stablebridge.indexer.application.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.stablebridge.indexer.api.BloomStatusResponse;
import com.stablebridge.indexer.api.ErrorResponse;
import com.stablebridge.indexer.api.IndexerStatusResponse;
import com.stablebridge.indexer.domain.model.BloomStatus;
import com.stablebridge.indexer.domain.model.ChainStatus;
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

import static com.stablebridge.indexer.domain.model.NetworkType.BITCOIN;
import static com.stablebridge.indexer.domain.model.NetworkType.EVM;
import static com.stablebridge.indexer.domain.model.NetworkType.SOLANA;
import static com.stablebridge.indexer.domain.model.WorkerState.STOPPED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StatusController.class)
@DisplayName("StatusController")
class StatusControllerTest {

    private static final String ETHEREUM_CHAIN = "ethereum_mainnet";
    private static final long ETHEREUM_LAST_BLOCK = 19_500_000L;

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

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
            var domainStatuses = List.of(
                    ChainStatus.builder()
                            .chainName(ETHEREUM_CHAIN)
                            .networkType(EVM)
                            .workerState(STOPPED)
                            .lastProcessedBlock(ETHEREUM_LAST_BLOCK)
                            .latestFinalizedBlock(null)
                            .blocksBehind(null)
                            .enabled(true)
                            .build()
            );

            var response = new IndexerStatusResponse(
                    ETHEREUM_CHAIN, "EVM", "STOPPED",
                    ETHEREUM_LAST_BLOCK, null, null, true);

            given(statusQueryHandler.getAllChainStatuses()).willReturn(domainStatuses);
            given(mapper.toResponseList(domainStatuses)).willReturn(List.of(response));

            var result = mockMvc.perform(get("/api/v1/status"))
                    .andExpect(status().isOk())
                    .andReturn();

            var actual = objectMapper.readValue(
                    result.getResponse().getContentAsString(),
                    new TypeReference<List<IndexerStatusResponse>>() {});
            var expected = List.of(
                    new IndexerStatusResponse(
                            ETHEREUM_CHAIN, "EVM", "STOPPED",
                            ETHEREUM_LAST_BLOCK, null, null, true));
            assertThat(actual)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);

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
            var domainStatus = ChainStatus.builder()
                    .chainName(ETHEREUM_CHAIN)
                    .networkType(EVM)
                    .workerState(STOPPED)
                    .lastProcessedBlock(ETHEREUM_LAST_BLOCK)
                    .latestFinalizedBlock(null)
                    .blocksBehind(null)
                    .enabled(true)
                    .build();

            var response = new IndexerStatusResponse(
                    ETHEREUM_CHAIN, "EVM", "STOPPED",
                    ETHEREUM_LAST_BLOCK, null, null, true);

            given(statusQueryHandler.getChainStatus(ETHEREUM_CHAIN))
                    .willReturn(Optional.of(domainStatus));
            given(mapper.toResponse(domainStatus)).willReturn(response);

            var result = mockMvc.perform(get("/api/v1/status/{chainName}", ETHEREUM_CHAIN))
                    .andExpect(status().isOk())
                    .andReturn();

            var actual = objectMapper.readValue(
                    result.getResponse().getContentAsString(),
                    IndexerStatusResponse.class);
            var expected = new IndexerStatusResponse(
                    ETHEREUM_CHAIN, "EVM", "STOPPED",
                    ETHEREUM_LAST_BLOCK, null, null, true);
            assertThat(actual)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);

            then(statusQueryHandler).should().getChainStatus(ETHEREUM_CHAIN);
            then(mapper).should().toResponse(domainStatus);
        }

        @Test
        @DisplayName("returns 404 when chain is not configured")
        void returnsNotFoundWhenChainNotConfigured() throws Exception {
            var unknownChain = "unknown_chain";

            given(statusQueryHandler.getChainStatus(unknownChain))
                    .willReturn(Optional.empty());

            var result = mockMvc.perform(get("/api/v1/status/{chainName}", unknownChain))
                    .andExpect(status().isNotFound())
                    .andReturn();

            var actual = objectMapper.readValue(
                    result.getResponse().getContentAsString(),
                    ErrorResponse.class);
            var expected = new ErrorResponse(
                    404, "Not Found",
                    "Chain not configured: " + unknownChain, null);
            assertThat(actual)
                    .usingRecursiveComparison()
                    .ignoringFields("timestamp")
                    .isEqualTo(expected);

            then(statusQueryHandler).should().getChainStatus(unknownChain);
        }
    }

    @Nested
    @DisplayName("GET /api/v1/status/bloom")
    class GetBloomStatus {

        @Test
        @DisplayName("returns 200 with bloom filter status")
        void returnsBloomStatus() throws Exception {
            var domainBloomStatus = BloomStatus.builder()
                    .backend("redis")
                    .expectedInsertions(1_000_000L)
                    .errorRate(0.001)
                    .networkTypes(List.of(EVM, SOLANA, BITCOIN))
                    .build();

            var response = new BloomStatusResponse(
                    "redis", 1_000_000L, 0.001,
                    List.of("EVM", "SOLANA", "BITCOIN"));

            given(statusQueryHandler.getBloomStatus()).willReturn(domainBloomStatus);
            given(mapper.toBloomResponse(domainBloomStatus)).willReturn(response);

            var result = mockMvc.perform(get("/api/v1/status/bloom"))
                    .andExpect(status().isOk())
                    .andReturn();

            var actual = objectMapper.readValue(
                    result.getResponse().getContentAsString(),
                    BloomStatusResponse.class);
            var expected = new BloomStatusResponse(
                    "redis", 1_000_000L, 0.001,
                    List.of("EVM", "SOLANA", "BITCOIN"));
            assertThat(actual)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);

            then(statusQueryHandler).should().getBloomStatus();
            then(mapper).should().toBloomResponse(domainBloomStatus);
        }
    }
}
