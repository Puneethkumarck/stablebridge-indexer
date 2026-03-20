package com.stablebridge.indexer.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stablebridge.indexer.api.WalletAddressRequest;
import com.stablebridge.indexer.application.config.SecurityAutoConfiguration;
import com.stablebridge.indexer.application.properties.IndexerProperties;
import com.stablebridge.indexer.domain.model.NetworkType;
import com.stablebridge.indexer.domain.model.WalletAddress;
import com.stablebridge.indexer.domain.service.WalletCommandHandler;
import com.stablebridge.indexer.domain.service.WalletCommandHandler.WalletAddressTuple;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static com.stablebridge.indexer.testutil.WalletAddressFixtures.DEFAULT_ADDRESS;
import static com.stablebridge.indexer.testutil.WalletAddressFixtures.DEFAULT_LABEL;
import static com.stablebridge.indexer.testutil.WalletAddressFixtures.aWalletAddress;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WalletController.class)
@Import(SecurityAutoConfiguration.class)
@EnableConfigurationProperties(IndexerProperties.class)
@TestPropertySource(properties = "indexer.api.key=change-me")
@DisplayName("WalletController")
class WalletControllerTest {

    private static final String API_KEY = "change-me";
    private static final String API_KEY_HEADER = "X-API-Key";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WalletCommandHandler walletCommandHandler;

    @MockitoBean
    private WalletControllerMapper mapper;

    @Nested
    @DisplayName("POST /api/v1/wallets")
    class AddWallet {

        @Test
        @DisplayName("returns 201 Created when wallet is added with valid API key")
        void returnsCreatedWhenWalletAdded() throws Exception {
            WalletAddressRequest request = new WalletAddressRequest(
                    DEFAULT_ADDRESS,
                    com.stablebridge.indexer.api.NetworkType.EVM,
                    DEFAULT_LABEL);
            WalletAddress savedWallet = aWalletAddress().build();

            given(mapper.toDomain(com.stablebridge.indexer.api.NetworkType.EVM))
                    .willReturn(NetworkType.EVM);
            given(walletCommandHandler.addWallet(DEFAULT_ADDRESS, NetworkType.EVM, DEFAULT_LABEL))
                    .willReturn(savedWallet);
            given(mapper.toResponse(savedWallet))
                    .willReturn(com.stablebridge.indexer.testutil.WalletAddressFixtures
                            .aWalletAddressResponse());

            mockMvc.perform(post("/api/v1/wallets")
                            .header(API_KEY_HEADER, API_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.address").value(DEFAULT_ADDRESS))
                    .andExpect(jsonPath("$.networkType").value("EVM"));
        }

        @Test
        @DisplayName("returns 401 Unauthorized when API key is missing")
        void returnsUnauthorizedWithoutApiKey() throws Exception {
            WalletAddressRequest request = new WalletAddressRequest(
                    DEFAULT_ADDRESS,
                    com.stablebridge.indexer.api.NetworkType.EVM,
                    DEFAULT_LABEL);

            mockMvc.perform(post("/api/v1/wallets")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("returns 400 Bad Request when address is blank")
        void returnsBadRequestWhenAddressBlank() throws Exception {
            WalletAddressRequest request = new WalletAddressRequest(
                    "",
                    com.stablebridge.indexer.api.NetworkType.EVM,
                    DEFAULT_LABEL);

            mockMvc.perform(post("/api/v1/wallets")
                            .header(API_KEY_HEADER, API_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 400 Bad Request when networkType is null")
        void returnsBadRequestWhenNetworkTypeNull() throws Exception {
            String requestJson = """
                    {"address": "%s", "label": "%s"}
                    """.formatted(DEFAULT_ADDRESS, DEFAULT_LABEL);

            mockMvc.perform(post("/api/v1/wallets")
                            .header(API_KEY_HEADER, API_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/wallets/batch")
    class AddWalletsBatch {

        @Test
        @DisplayName("returns 201 Created when batch wallets are added with valid API key")
        void returnsCreatedWhenBatchAdded() throws Exception {
            WalletAddressRequest request = new WalletAddressRequest(
                    DEFAULT_ADDRESS,
                    com.stablebridge.indexer.api.NetworkType.EVM,
                    DEFAULT_LABEL);
            WalletAddress savedWallet = aWalletAddress().build();
            List<WalletAddressTuple> expectedTuples = List.of(
                    new WalletAddressTuple(DEFAULT_ADDRESS, NetworkType.EVM, DEFAULT_LABEL));

            given(mapper.toDomain(com.stablebridge.indexer.api.NetworkType.EVM))
                    .willReturn(NetworkType.EVM);
            given(walletCommandHandler.addWalletsBatch(expectedTuples))
                    .willReturn(List.of(savedWallet));
            given(mapper.toResponse(savedWallet))
                    .willReturn(com.stablebridge.indexer.testutil.WalletAddressFixtures
                            .aWalletAddressResponse());

            mockMvc.perform(post("/api/v1/wallets/batch")
                            .header(API_KEY_HEADER, API_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(List.of(request))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$[0].address").value(DEFAULT_ADDRESS));
        }

        @Test
        @DisplayName("returns 401 Unauthorized when API key is missing")
        void returnsUnauthorizedWithoutApiKey() throws Exception {
            WalletAddressRequest request = new WalletAddressRequest(
                    DEFAULT_ADDRESS,
                    com.stablebridge.indexer.api.NetworkType.EVM,
                    DEFAULT_LABEL);

            mockMvc.perform(post("/api/v1/wallets/batch")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(List.of(request))))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/wallets")
    class ListWallets {

        @Test
        @DisplayName("returns 200 OK with wallets for the given network type")
        void returnsWalletsForNetworkType() throws Exception {
            WalletAddress wallet = aWalletAddress().build();

            given(mapper.toDomain(com.stablebridge.indexer.api.NetworkType.EVM))
                    .willReturn(NetworkType.EVM);
            given(walletCommandHandler.listWallets(NetworkType.EVM))
                    .willReturn(List.of(wallet));
            given(mapper.toResponse(wallet))
                    .willReturn(com.stablebridge.indexer.testutil.WalletAddressFixtures
                            .aWalletAddressResponse());

            mockMvc.perform(get("/api/v1/wallets")
                            .header(API_KEY_HEADER, API_KEY)
                            .param("networkType", "EVM"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].address").value(DEFAULT_ADDRESS))
                    .andExpect(jsonPath("$[0].networkType").value("EVM"));
        }

        @Test
        @DisplayName("returns 401 Unauthorized when API key is missing")
        void returnsUnauthorizedWithoutApiKey() throws Exception {
            mockMvc.perform(get("/api/v1/wallets")
                            .param("networkType", "EVM"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("returns 400 Bad Request when networkType parameter is missing")
        void returnsBadRequestWhenNetworkTypeMissing() throws Exception {
            mockMvc.perform(get("/api/v1/wallets")
                            .header(API_KEY_HEADER, API_KEY))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/wallets/{address}")
    class RemoveWallet {

        @Test
        @DisplayName("returns 204 No Content when wallet is removed with valid API key")
        void returnsNoContentWhenWalletRemoved() throws Exception {
            given(mapper.toDomain(com.stablebridge.indexer.api.NetworkType.EVM))
                    .willReturn(NetworkType.EVM);

            mockMvc.perform(delete("/api/v1/wallets/{address}", DEFAULT_ADDRESS)
                            .header(API_KEY_HEADER, API_KEY)
                            .param("networkType", "EVM"))
                    .andExpect(status().isNoContent());

            then(walletCommandHandler).should()
                    .removeWallet(DEFAULT_ADDRESS, NetworkType.EVM);
        }

        @Test
        @DisplayName("returns 401 Unauthorized when API key is missing")
        void returnsUnauthorizedWithoutApiKey() throws Exception {
            mockMvc.perform(delete("/api/v1/wallets/{address}", DEFAULT_ADDRESS)
                            .param("networkType", "EVM"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/wallets/bloom/rebuild")
    class RebuildBloom {

        @Test
        @DisplayName("returns 200 OK when bloom filter is rebuilt with valid API key")
        void returnsOkWhenBloomRebuilt() throws Exception {
            mockMvc.perform(post("/api/v1/wallets/bloom/rebuild")
                            .header(API_KEY_HEADER, API_KEY))
                    .andExpect(status().isOk());

            then(walletCommandHandler).should().rebuildBloom();
        }

        @Test
        @DisplayName("returns 401 Unauthorized when API key is missing")
        void returnsUnauthorizedWithoutApiKey() throws Exception {
            mockMvc.perform(post("/api/v1/wallets/bloom/rebuild"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
