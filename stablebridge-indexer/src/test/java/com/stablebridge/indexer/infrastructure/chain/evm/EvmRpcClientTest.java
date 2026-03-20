package com.stablebridge.indexer.infrastructure.chain.evm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@WireMockTest(httpPort = 0)
@DisplayName("EvmRpcClient")
class EvmRpcClientTest {

    private static final int BATCH_SIZE = 50;
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private EvmRpcClient client;
    private String wireMockUrl;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp(WireMockRuntimeInfo wmRuntimeInfo) {
        wireMockUrl = wmRuntimeInfo.getHttpBaseUrl();
        client = new EvmRpcClient(wireMockUrl, BATCH_SIZE, true, TIMEOUT, objectMapper);
    }

    @Nested
    @DisplayName("getLatestBlockNumber")
    class GetLatestBlockNumber {

        @Test
        @DisplayName("returns correct block number from hex response")
        void returnsCorrectBlockNumberFromHex() {
            // given
            stubFor(post(urlEqualTo("/"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {"jsonrpc":"2.0","id":1,"result":"0x1234ab"}
                                    """)));

            // when
            var result = client.getLatestBlockNumber();

            // then
            assertThat(result).isEqualTo(0x1234abL);
        }

        @Test
        @DisplayName("throws EvmRpcException on RPC error response")
        void throwsOnRpcError() {
            // given
            stubFor(post(urlEqualTo("/"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {"jsonrpc":"2.0","id":1,"error":{"code":-32601,"message":"Method not found"}}
                                    """)));

            // when / then
            assertThatThrownBy(() -> client.getLatestBlockNumber())
                    .isInstanceOf(EvmRpcException.class)
                    .hasMessageContaining("Method not found");
        }

        @Test
        @DisplayName("throws EvmRpcException on HTTP 500")
        void throwsOnHttp500() {
            // given
            stubFor(post(urlEqualTo("/"))
                    .willReturn(aResponse()
                            .withStatus(500)
                            .withBody("Internal Server Error")));

            // when / then
            assertThatThrownBy(() -> client.getLatestBlockNumber())
                    .isInstanceOf(EvmRpcException.class)
                    .hasMessageContaining("statusCode=500");
        }
    }

    @Nested
    @DisplayName("getBlockByNumber")
    class GetBlockByNumber {

        @Test
        @DisplayName("returns EvmBlock with transactions")
        void returnsBlockWithTransactions() {
            // given
            stubFor(post(urlEqualTo("/"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {
                                      "jsonrpc": "2.0",
                                      "id": 2,
                                      "result": {
                                        "number": "0x10d4f1",
                                        "hash": "0xblock_hash_abc",
                                        "parentHash": "0xparent_hash_def",
                                        "timestamp": "0x65b3e8c0",
                                        "transactions": [
                                          {
                                            "hash": "0xtx_hash_1",
                                            "from": "0xsender_1",
                                            "to": "0xreceiver_1",
                                            "value": "0xde0b6b3a7640000",
                                            "input": "0x",
                                            "blockNumber": "0x10d4f1",
                                            "transactionIndex": "0x0",
                                            "blockHash": "0xblock_hash_abc"
                                          },
                                          {
                                            "hash": "0xtx_hash_2",
                                            "from": "0xsender_2",
                                            "to": "0xreceiver_2",
                                            "value": "0x0",
                                            "input": "0xa9059cbb",
                                            "blockNumber": "0x10d4f1",
                                            "transactionIndex": "0x1",
                                            "blockHash": "0xblock_hash_abc"
                                          }
                                        ]
                                      }
                                    }
                                    """)));

            // when
            var block = client.getBlockByNumber(0x10d4f1L);

            // then
            var expected = EvmBlock.builder()
                    .number("0x10d4f1")
                    .hash("0xblock_hash_abc")
                    .parentHash("0xparent_hash_def")
                    .timestamp("0x65b3e8c0")
                    .transactions(List.of(
                            EvmTransaction.builder()
                                    .hash("0xtx_hash_1")
                                    .from("0xsender_1")
                                    .to("0xreceiver_1")
                                    .value("0xde0b6b3a7640000")
                                    .input("0x")
                                    .blockNumber("0x10d4f1")
                                    .transactionIndex("0x0")
                                    .blockHash("0xblock_hash_abc")
                                    .build(),
                            EvmTransaction.builder()
                                    .hash("0xtx_hash_2")
                                    .from("0xsender_2")
                                    .to("0xreceiver_2")
                                    .value("0x0")
                                    .input("0xa9059cbb")
                                    .blockNumber("0x10d4f1")
                                    .transactionIndex("0x1")
                                    .blockHash("0xblock_hash_abc")
                                    .build()))
                    .build();
            assertThat(block).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        @DisplayName("returns block with empty transactions list")
        void returnsBlockWithEmptyTransactions() {
            // given
            stubFor(post(urlEqualTo("/"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {
                                      "jsonrpc": "2.0",
                                      "id": 2,
                                      "result": {
                                        "number": "0x10d4f1",
                                        "hash": "0xblock_hash_abc",
                                        "parentHash": "0xparent_hash_def",
                                        "timestamp": "0x65b3e8c0",
                                        "transactions": []
                                      }
                                    }
                                    """)));

            // when
            var block = client.getBlockByNumber(0x10d4f1L);

            // then
            assertThat(block.transactions()).isEmpty();
        }
    }

    @Nested
    @DisplayName("getTransactionReceipts")
    class GetTransactionReceipts {

        @Test
        @DisplayName("returns single receipt from batch request")
        void returnsSingleReceipt() {
            // given
            stubFor(post(urlEqualTo("/"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    [
                                      {
                                        "jsonrpc": "2.0",
                                        "id": 3,
                                        "result": {
                                          "transactionHash": "0xtx_hash_1",
                                          "transactionIndex": "0x0",
                                          "blockNumber": "0x10d4f1",
                                          "blockHash": "0xblock_hash_abc",
                                          "from": "0xsender_1",
                                          "to": "0xreceiver_1",
                                          "status": "0x1",
                                          "logs": [
                                            {
                                              "address": "0xtoken_contract",
                                              "topics": [
                                                "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef",
                                                "0x000000000000000000000000sender_1",
                                                "0x000000000000000000000000receiver_1"
                                              ],
                                              "data": "0x00000000000000000000000000000000000000000000000000000000000f4240",
                                              "logIndex": "0x0",
                                              "transactionIndex": "0x0",
                                              "transactionHash": "0xtx_hash_1",
                                              "blockNumber": "0x10d4f1",
                                              "blockHash": "0xblock_hash_abc"
                                            }
                                          ]
                                        }
                                      }
                                    ]
                                    """)));

            // when
            var receipts = client.getTransactionReceipts(List.of("0xtx_hash_1"));

            // then
            var expected = EvmReceipt.builder()
                    .transactionHash("0xtx_hash_1")
                    .transactionIndex("0x0")
                    .blockNumber("0x10d4f1")
                    .blockHash("0xblock_hash_abc")
                    .from("0xsender_1")
                    .to("0xreceiver_1")
                    .status("0x1")
                    .logs(List.of(
                            EvmLog.builder()
                                    .address("0xtoken_contract")
                                    .topics(List.of(
                                            "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef",
                                            "0x000000000000000000000000sender_1",
                                            "0x000000000000000000000000receiver_1"))
                                    .data("0x00000000000000000000000000000000000000000000000000000000000f4240")
                                    .logIndex("0x0")
                                    .transactionIndex("0x0")
                                    .transactionHash("0xtx_hash_1")
                                    .blockNumber("0x10d4f1")
                                    .blockHash("0xblock_hash_abc")
                                    .build()))
                    .build();
            assertThat(receipts).hasSize(1);
            assertThat(receipts.getFirst()).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        @DisplayName("returns multiple receipts from batch request")
        void returnsMultipleReceipts() {
            // given
            stubFor(post(urlEqualTo("/"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    [
                                      {
                                        "jsonrpc": "2.0",
                                        "id": 4,
                                        "result": {
                                          "transactionHash": "0xtx_hash_1",
                                          "transactionIndex": "0x0",
                                          "blockNumber": "0x10d4f1",
                                          "blockHash": "0xblock_hash",
                                          "from": "0xsender_1",
                                          "to": "0xreceiver_1",
                                          "status": "0x1",
                                          "logs": []
                                        }
                                      },
                                      {
                                        "jsonrpc": "2.0",
                                        "id": 5,
                                        "result": {
                                          "transactionHash": "0xtx_hash_2",
                                          "transactionIndex": "0x1",
                                          "blockNumber": "0x10d4f1",
                                          "blockHash": "0xblock_hash",
                                          "from": "0xsender_2",
                                          "to": "0xreceiver_2",
                                          "status": "0x1",
                                          "logs": []
                                        }
                                      },
                                      {
                                        "jsonrpc": "2.0",
                                        "id": 6,
                                        "result": {
                                          "transactionHash": "0xtx_hash_3",
                                          "transactionIndex": "0x2",
                                          "blockNumber": "0x10d4f1",
                                          "blockHash": "0xblock_hash",
                                          "from": "0xsender_3",
                                          "to": "0xreceiver_3",
                                          "status": "0x0",
                                          "logs": []
                                        }
                                      }
                                    ]
                                    """)));

            // when
            var receipts = client.getTransactionReceipts(
                    List.of("0xtx_hash_1", "0xtx_hash_2", "0xtx_hash_3"));

            // then
            assertThat(receipts).hasSize(3);
            assertThat(receipts.get(2).isSuccessful()).isFalse();
        }

        @Test
        @DisplayName("returns empty list when no tx hashes provided")
        void returnsEmptyListForEmptyInput() {
            // given
            var emptyHashes = List.<String>of();

            // when
            var receipts = client.getTransactionReceipts(emptyHashes);

            // then
            assertThat(receipts).isEmpty();
        }

        @Test
        @DisplayName("returns empty list when null tx hashes provided")
        void returnsEmptyListForNullInput() {
            // given / when
            var receipts = client.getTransactionReceipts(null);

            // then
            assertThat(receipts).isEmpty();
        }
    }

    @Nested
    @DisplayName("getTransactionReceipts with batch splitting")
    class GetTransactionReceiptsBatchSplitting {

        @Test
        @DisplayName("sends multiple batch requests when tx count exceeds batch size")
        void sendsMultipleBatchRequests() {
            // given
            var smallBatchClient = new EvmRpcClient(
                    wireMockUrl, 2, false, TIMEOUT, objectMapper);

            stubFor(post(urlEqualTo("/"))
                    .inScenario("batch-split")
                    .whenScenarioStateIs(STARTED)
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    [
                                      {
                                        "jsonrpc": "2.0",
                                        "id": 1,
                                        "result": {
                                          "transactionHash": "0xtx_1",
                                          "transactionIndex": "0x0",
                                          "blockNumber": "0x1",
                                          "blockHash": "0xhash",
                                          "from": "0xa",
                                          "to": "0xb",
                                          "status": "0x1",
                                          "logs": []
                                        }
                                      },
                                      {
                                        "jsonrpc": "2.0",
                                        "id": 2,
                                        "result": {
                                          "transactionHash": "0xtx_2",
                                          "transactionIndex": "0x1",
                                          "blockNumber": "0x1",
                                          "blockHash": "0xhash",
                                          "from": "0xa",
                                          "to": "0xb",
                                          "status": "0x1",
                                          "logs": []
                                        }
                                      }
                                    ]
                                    """))
                    .willSetStateTo("first-batch-done"));

            stubFor(post(urlEqualTo("/"))
                    .inScenario("batch-split")
                    .whenScenarioStateIs("first-batch-done")
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    [
                                      {
                                        "jsonrpc": "2.0",
                                        "id": 3,
                                        "result": {
                                          "transactionHash": "0xtx_3",
                                          "transactionIndex": "0x2",
                                          "blockNumber": "0x1",
                                          "blockHash": "0xhash",
                                          "from": "0xa",
                                          "to": "0xb",
                                          "status": "0x1",
                                          "logs": []
                                        }
                                      }
                                    ]
                                    """)));

            // when
            var receipts = smallBatchClient.getTransactionReceipts(
                    List.of("0xtx_1", "0xtx_2", "0xtx_3"));

            // then
            assertThat(receipts).hasSize(3);
            verify(2, postRequestedFor(urlEqualTo("/")));
        }
    }

    @Nested
    @DisplayName("getBlockReceipts")
    class GetBlockReceipts {

        @Test
        @DisplayName("returns all receipts for a block")
        void returnsAllBlockReceipts() {
            // given
            stubFor(post(urlEqualTo("/"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {
                                      "jsonrpc": "2.0",
                                      "id": 9,
                                      "result": [
                                        {
                                          "transactionHash": "0xtx_hash_1",
                                          "transactionIndex": "0x0",
                                          "blockNumber": "0x10d4f1",
                                          "blockHash": "0xblock_hash_abc",
                                          "from": "0xsender_1",
                                          "to": "0xreceiver_1",
                                          "status": "0x1",
                                          "logs": []
                                        },
                                        {
                                          "transactionHash": "0xtx_hash_2",
                                          "transactionIndex": "0x1",
                                          "blockNumber": "0x10d4f1",
                                          "blockHash": "0xblock_hash_abc",
                                          "from": "0xsender_2",
                                          "to": "0xreceiver_2",
                                          "status": "0x1",
                                          "logs": [
                                            {
                                              "address": "0xtoken_addr",
                                              "topics": ["0xtopic1"],
                                              "data": "0xdata",
                                              "logIndex": "0x0",
                                              "transactionIndex": "0x1",
                                              "transactionHash": "0xtx_hash_2",
                                              "blockNumber": "0x10d4f1",
                                              "blockHash": "0xblock_hash_abc"
                                            }
                                          ]
                                        }
                                      ]
                                    }
                                    """)));

            // when
            var receipts = client.getBlockReceipts(0x10d4f1L);

            // then
            var expectedFirst = EvmReceipt.builder()
                    .transactionHash("0xtx_hash_1")
                    .transactionIndex("0x0")
                    .blockNumber("0x10d4f1")
                    .blockHash("0xblock_hash_abc")
                    .from("0xsender_1")
                    .to("0xreceiver_1")
                    .status("0x1")
                    .logs(List.of())
                    .build();
            var expectedSecond = EvmReceipt.builder()
                    .transactionHash("0xtx_hash_2")
                    .transactionIndex("0x1")
                    .blockNumber("0x10d4f1")
                    .blockHash("0xblock_hash_abc")
                    .from("0xsender_2")
                    .to("0xreceiver_2")
                    .status("0x1")
                    .logs(List.of(
                            EvmLog.builder()
                                    .address("0xtoken_addr")
                                    .topics(List.of("0xtopic1"))
                                    .data("0xdata")
                                    .logIndex("0x0")
                                    .transactionIndex("0x1")
                                    .transactionHash("0xtx_hash_2")
                                    .blockNumber("0x10d4f1")
                                    .blockHash("0xblock_hash_abc")
                                    .build()))
                    .build();
            assertThat(receipts)
                    .usingRecursiveComparison()
                    .isEqualTo(List.of(expectedFirst, expectedSecond));
        }

        @Test
        @DisplayName("returns empty list when block has no receipts")
        void returnsEmptyListForNoReceipts() {
            // given
            stubFor(post(urlEqualTo("/"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {"jsonrpc":"2.0","id":10,"result":[]}
                                    """)));

            // when
            var receipts = client.getBlockReceipts(100L);

            // then
            assertThat(receipts).isEmpty();
        }
    }

    @Nested
    @DisplayName("supportsBlockReceipts")
    class SupportsBlockReceipts {

        @Test
        @DisplayName("returns true when useBlockReceipts is enabled")
        void returnsTrueWhenEnabled() {
            // given
            var clientWithBlockReceipts = new EvmRpcClient(
                    wireMockUrl, BATCH_SIZE, true, TIMEOUT, objectMapper);

            // when
            var result = clientWithBlockReceipts.supportsBlockReceipts();

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("returns false when useBlockReceipts is disabled")
        void returnsFalseWhenDisabled() {
            // given
            var clientWithoutBlockReceipts = new EvmRpcClient(
                    wireMockUrl, BATCH_SIZE, false, TIMEOUT, objectMapper);

            // when
            var result = clientWithoutBlockReceipts.supportsBlockReceipts();

            // then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("error handling")
    class ErrorHandling {

        @Test
        @DisplayName("throws EvmRpcException with error details on RPC error")
        void throwsWithErrorDetails() {
            // given
            stubFor(post(urlEqualTo("/"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {"jsonrpc":"2.0","id":1,"error":{"code":-32000,"message":"execution reverted"}}
                                    """)));

            // when / then
            assertThatThrownBy(() -> client.getLatestBlockNumber())
                    .isInstanceOf(EvmRpcException.class)
                    .hasMessageContaining("execution reverted")
                    .hasMessageContaining("-32000");
        }

        @Test
        @DisplayName("throws EvmRpcException on HTTP error status")
        void throwsOnHttpError() {
            // given
            stubFor(post(urlEqualTo("/"))
                    .willReturn(aResponse()
                            .withStatus(429)
                            .withBody("Rate limited")));

            // when / then
            assertThatThrownBy(() -> client.getLatestBlockNumber())
                    .isInstanceOf(EvmRpcException.class)
                    .hasMessageContaining("statusCode=429");
        }

        @Test
        @DisplayName("throws EvmRpcException on batch RPC error")
        void throwsOnBatchRpcError() {
            // given
            stubFor(post(urlEqualTo("/"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    [
                                      {"jsonrpc":"2.0","id":1,"error":{"code":-32602,"message":"invalid params"}}
                                    ]
                                    """)));

            // when / then
            assertThatThrownBy(() -> client.getTransactionReceipts(List.of("0xbad_hash")))
                    .isInstanceOf(EvmRpcException.class)
                    .hasMessageContaining("invalid params");
        }
    }
}
