package com.stablebridge.indexer.infrastructure.chain.tron;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@WireMockTest(httpPort = 0)
@DisplayName("TronRpcClient")
class TronRpcClientTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final String API_KEY = "test-api-key";

    private TronRpcClient client;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wmRuntimeInfo) {
        var wireMockUrl = wmRuntimeInfo.getHttpBaseUrl();
        client = new TronRpcClient(wireMockUrl, API_KEY, TIMEOUT);
    }

    @Nested
    @DisplayName("getLatestSolidifiedBlockNumber")
    class GetLatestSolidifiedBlockNumber {

        @Test
        @DisplayName("returns correct block number from solidified block")
        void returnsCorrectBlockNumber() {
            // given
            stubFor(post(urlEqualTo("/walletsolidity/getnowblock"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {
                                      "blockID": "000000000204a39e1234567890abcdef",
                                      "block_header": {
                                        "raw_data": {
                                          "number": 33833886,
                                          "timestamp": 1710000000000,
                                          "parentHash": "000000000204a39d1234567890abcdef"
                                        }
                                      }
                                    }
                                    """)));

            // when
            var result = client.getLatestSolidifiedBlockNumber();

            // then
            assertThat(result).isEqualTo(33833886L);
        }

        @Test
        @DisplayName("sends TRON-PRO-API-KEY header on all requests")
        void sendsApiKeyHeader() {
            // given
            stubFor(post(urlEqualTo("/walletsolidity/getnowblock"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {
                                      "blockID": "000000000204a39e",
                                      "block_header": {
                                        "raw_data": {
                                          "number": 100,
                                          "timestamp": 1710000000000,
                                          "parentHash": "000000000204a39d"
                                        }
                                      }
                                    }
                                    """)));

            // when
            client.getLatestSolidifiedBlockNumber();

            // then
            verify(postRequestedFor(urlEqualTo("/walletsolidity/getnowblock"))
                    .withHeader("TRON-PRO-API-KEY", equalTo(API_KEY)));
        }

        @Test
        @DisplayName("throws TronRpcException on API error response")
        void throwsOnApiError() {
            // given
            stubFor(post(urlEqualTo("/walletsolidity/getnowblock"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {"Error": "class java.lang.NullPointerException : null"}
                                    """)));

            // when / then
            assertThatThrownBy(() -> client.getLatestSolidifiedBlockNumber())
                    .isInstanceOf(TronRpcException.class)
                    .hasMessageContaining("NullPointerException");
        }

        @Test
        @DisplayName("throws TronRpcException on HTTP 500")
        void throwsOnHttp500() {
            // given
            stubFor(post(urlEqualTo("/walletsolidity/getnowblock"))
                    .willReturn(aResponse()
                            .withStatus(500)
                            .withBody("Internal Server Error")));

            // when / then
            assertThatThrownBy(() -> client.getLatestSolidifiedBlockNumber())
                    .isInstanceOf(TronRpcException.class)
                    .hasMessageContaining("statusCode=500");
        }

        @Test
        @DisplayName("throws TronRpcException on HTTP 429 rate limit")
        void throwsOnRateLimit() {
            // given
            stubFor(post(urlEqualTo("/walletsolidity/getnowblock"))
                    .willReturn(aResponse()
                            .withStatus(429)
                            .withBody("Rate limited")));

            // when / then
            assertThatThrownBy(() -> client.getLatestSolidifiedBlockNumber())
                    .isInstanceOf(TronRpcException.class)
                    .hasMessageContaining("statusCode=429");
        }
    }

    @Nested
    @DisplayName("getBlockByNumber")
    class GetBlockByNumber {

        @Test
        @DisplayName("returns TronBlock with transactions")
        void returnsBlockWithTransactions() {
            // given
            stubFor(post(urlEqualTo("/walletsolidity/getblockbynum"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {
                                      "blockID": "000000000204a39e1234567890abcdef",
                                      "block_header": {
                                        "raw_data": {
                                          "number": 33833886,
                                          "timestamp": 1710000000000,
                                          "parentHash": "000000000204a39d1234567890abcdef"
                                        }
                                      },
                                      "transactions": [
                                        {
                                          "txID": "abc123def456",
                                          "ret": [{"contractRet": "SUCCESS"}],
                                          "raw_data": {
                                            "contract": [
                                              {
                                                "type": "TransferContract",
                                                "parameter": {
                                                  "value": {
                                                    "owner_address": "TSender123",
                                                    "to_address": "TReceiver456",
                                                    "amount": 1000000
                                                  },
                                                  "type_url": "type.googleapis.com/protocol.TransferContract"
                                                }
                                              }
                                            ]
                                          }
                                        }
                                      ]
                                    }
                                    """)));

            // when
            var block = client.getBlockByNumber(33833886L);

            // then
            var expected = TronBlock.builder()
                    .blockID("000000000204a39e1234567890abcdef")
                    .block_header(TronBlock.BlockHeader.builder()
                            .raw_data(TronBlock.RawData.builder()
                                    .number(33833886L)
                                    .timestamp(1710000000000L)
                                    .parentHash("000000000204a39d1234567890abcdef")
                                    .build())
                            .build())
                    .transactions(List.of(
                            TronTransaction.builder()
                                    .txID("abc123def456")
                                    .ret(List.of(TronTransaction.RetResult.builder()
                                            .contractRet("SUCCESS")
                                            .build()))
                                    .raw_data(TronTransaction.RawData.builder()
                                            .contract(List.of(TronTransaction.Contract.builder()
                                                    .type("TransferContract")
                                                    .parameter(TronTransaction.ContractParameter.builder()
                                                            .value(TronTransaction.ContractValue.builder()
                                                                    .owner_address("TSender123")
                                                                    .to_address("TReceiver456")
                                                                    .amount(1000000L)
                                                                    .build())
                                                            .type_url("type.googleapis.com/protocol.TransferContract")
                                                            .build())
                                                    .build()))
                                            .build())
                                    .build()))
                    .build();
            assertThat(block).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        @DisplayName("returns block with empty transactions list")
        void returnsBlockWithEmptyTransactions() {
            // given
            stubFor(post(urlEqualTo("/walletsolidity/getblockbynum"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {
                                      "blockID": "000000000204a39e",
                                      "block_header": {
                                        "raw_data": {
                                          "number": 33833886,
                                          "timestamp": 1710000000000,
                                          "parentHash": "000000000204a39d"
                                        }
                                      }
                                    }
                                    """)));

            // when
            var block = client.getBlockByNumber(33833886L);

            // then
            assertThat(block.transactions()).isNull();
        }

        @Test
        @DisplayName("helper methods extract nested fields correctly")
        void helperMethodsWork() {
            // given
            stubFor(post(urlEqualTo("/walletsolidity/getblockbynum"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {
                                      "blockID": "000000000204a39e",
                                      "block_header": {
                                        "raw_data": {
                                          "number": 33833886,
                                          "timestamp": 1710000000000,
                                          "parentHash": "000000000204a39d"
                                        }
                                      }
                                    }
                                    """)));

            // when
            var block = client.getBlockByNumber(33833886L);

            // then
            assertThat(block.blockNumber()).isEqualTo(33833886L);
            assertThat(block.blockTimestamp()).isEqualTo(Instant.ofEpochMilli(1710000000000L));
            assertThat(block.parentHash()).isEqualTo("000000000204a39d");
        }

        @Test
        @DisplayName("transaction helper methods work correctly")
        void transactionHelperMethods() {
            // given
            stubFor(post(urlEqualTo("/walletsolidity/getblockbynum"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {
                                      "blockID": "000000000204a39e",
                                      "block_header": {
                                        "raw_data": {
                                          "number": 100,
                                          "timestamp": 1710000000000,
                                          "parentHash": "parent"
                                        }
                                      },
                                      "transactions": [
                                        {
                                          "txID": "tx1",
                                          "ret": [{"contractRet": "SUCCESS"}],
                                          "raw_data": {
                                            "contract": [
                                              {
                                                "type": "TransferContract",
                                                "parameter": {
                                                  "value": {
                                                    "owner_address": "TSender",
                                                    "to_address": "TReceiver",
                                                    "amount": 5000000
                                                  }
                                                }
                                              }
                                            ]
                                          }
                                        },
                                        {
                                          "txID": "tx2",
                                          "ret": [{"contractRet": "REVERT"}],
                                          "raw_data": {
                                            "contract": [
                                              {
                                                "type": "TriggerSmartContract",
                                                "parameter": {
                                                  "value": {
                                                    "owner_address": "TSender2",
                                                    "contract_address": "TTokenContract",
                                                    "data": "a9059cbb000000"
                                                  }
                                                }
                                              }
                                            ]
                                          }
                                        }
                                      ]
                                    }
                                    """)));

            // when
            var block = client.getBlockByNumber(100L);
            var nativeTx = block.transactions().getFirst();
            var contractTx = block.transactions().get(1);

            // then
            assertThat(nativeTx.isSuccessful()).isTrue();
            assertThat(nativeTx.isNativeTransfer()).isTrue();
            assertThat(nativeTx.ownerAddress()).isEqualTo("TSender");
            assertThat(nativeTx.toAddress()).isEqualTo("TReceiver");
            assertThat(nativeTx.amount()).isEqualTo(5000000L);

            assertThat(contractTx.isSuccessful()).isFalse();
            assertThat(contractTx.isNativeTransfer()).isFalse();
        }
    }

    @Nested
    @DisplayName("getTransactionInfoByBlockNum")
    class GetTransactionInfoByBlockNum {

        @Test
        @DisplayName("returns list of transaction info with receipts and logs")
        void returnsTransactionInfoList() {
            // given
            stubFor(post(urlEqualTo("/walletsolidity/gettransactioninfobyblocknum"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    [
                                      {
                                        "id": "abc123def456",
                                        "blockNumber": 33833886,
                                        "blockTimeStamp": 1710000000000,
                                        "receipt": {"result": "SUCCESS"},
                                        "log": [
                                          {
                                            "address": "a614f803b6fd780986a42c78ec9c7f77e6ded13c",
                                            "topics": [
                                              "ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef",
                                              "0000000000000000000000001234567890abcdef1234567890abcdef12345678",
                                              "000000000000000000000000abcdefabcdefabcdefabcdefabcdefabcdefabcd"
                                            ],
                                            "data": "00000000000000000000000000000000000000000000000000000000000f4240"
                                          }
                                        ]
                                      },
                                      {
                                        "id": "def789ghi012",
                                        "blockNumber": 33833886,
                                        "blockTimeStamp": 1710000000000,
                                        "receipt": {"result": "OUT_OF_ENERGY"}
                                      }
                                    ]
                                    """)));

            // when
            var infos = client.getTransactionInfoByBlockNum(33833886L);

            // then
            var expectedFirst = TronTransactionInfo.builder()
                    .id("abc123def456")
                    .blockNumber(33833886L)
                    .blockTimeStamp(1710000000000L)
                    .receipt(TronTransactionInfo.Receipt.builder()
                            .result("SUCCESS")
                            .build())
                    .log(List.of(TronEventLog.builder()
                            .address("a614f803b6fd780986a42c78ec9c7f77e6ded13c")
                            .topics(List.of(
                                    "ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef",
                                    "0000000000000000000000001234567890abcdef1234567890abcdef12345678",
                                    "000000000000000000000000abcdefabcdefabcdefabcdefabcdefabcdefabcd"))
                            .data("00000000000000000000000000000000000000000000000000000000000f4240")
                            .build()))
                    .build();
            var expectedSecond = TronTransactionInfo.builder()
                    .id("def789ghi012")
                    .blockNumber(33833886L)
                    .blockTimeStamp(1710000000000L)
                    .receipt(TronTransactionInfo.Receipt.builder()
                            .result("OUT_OF_ENERGY")
                            .build())
                    .build();

            assertThat(infos).hasSize(2);
            assertThat(infos.getFirst()).usingRecursiveComparison().isEqualTo(expectedFirst);
            assertThat(infos.get(1)).usingRecursiveComparison().isEqualTo(expectedSecond);
        }

        @Test
        @DisplayName("returns empty list for block with no transactions")
        void returnsEmptyList() {
            // given
            stubFor(post(urlEqualTo("/walletsolidity/gettransactioninfobyblocknum"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("[]")));

            // when
            var infos = client.getTransactionInfoByBlockNum(100L);

            // then
            assertThat(infos).isEmpty();
        }

        @Test
        @DisplayName("isSuccessful returns correct values")
        void isSuccessfulReturnsCorrectValues() {
            // given
            stubFor(post(urlEqualTo("/walletsolidity/gettransactioninfobyblocknum"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    [
                                      {
                                        "id": "tx1",
                                        "blockNumber": 100,
                                        "blockTimeStamp": 1710000000000,
                                        "receipt": {"result": "SUCCESS"}
                                      },
                                      {
                                        "id": "tx2",
                                        "blockNumber": 100,
                                        "blockTimeStamp": 1710000000000,
                                        "receipt": {"result": "OUT_OF_ENERGY"}
                                      }
                                    ]
                                    """)));

            // when
            var infos = client.getTransactionInfoByBlockNum(100L);

            // then
            assertThat(infos.getFirst().isSuccessful()).isTrue();
            assertThat(infos.get(1).isSuccessful()).isFalse();
        }

        @Test
        @DisplayName("blockTimestamp converts epoch millis correctly")
        void blockTimestampConvertsCorrectly() {
            // given
            stubFor(post(urlEqualTo("/walletsolidity/gettransactioninfobyblocknum"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    [
                                      {
                                        "id": "tx1",
                                        "blockNumber": 100,
                                        "blockTimeStamp": 1710000000000,
                                        "receipt": {"result": "SUCCESS"}
                                      }
                                    ]
                                    """)));

            // when
            var infos = client.getTransactionInfoByBlockNum(100L);

            // then
            assertThat(infos.getFirst().blockTimestamp())
                    .isEqualTo(Instant.ofEpochMilli(1710000000000L));
        }
    }

    @Nested
    @DisplayName("getBlocksByRange")
    class GetBlocksByRange {

        @Test
        @DisplayName("returns list of blocks within range")
        void returnsBlockList() {
            // given
            stubFor(post(urlEqualTo("/wallet/getblockbylimitnext"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {
                                      "block": [
                                        {
                                          "blockID": "block1",
                                          "block_header": {
                                            "raw_data": {
                                              "number": 100,
                                              "timestamp": 1710000000000,
                                              "parentHash": "parent0"
                                            }
                                          }
                                        },
                                        {
                                          "blockID": "block2",
                                          "block_header": {
                                            "raw_data": {
                                              "number": 101,
                                              "timestamp": 1710000003000,
                                              "parentHash": "block1"
                                            }
                                          }
                                        }
                                      ]
                                    }
                                    """)));

            // when
            var blocks = client.getBlocksByRange(100L, 102L);

            // then
            assertThat(blocks).hasSize(2);
            assertThat(blocks.getFirst().blockNumber()).isEqualTo(100L);
            assertThat(blocks.get(1).blockNumber()).isEqualTo(101L);
        }

        @Test
        @DisplayName("returns empty list when no blocks in range")
        void returnsEmptyListForNoBlocks() {
            // given
            stubFor(post(urlEqualTo("/wallet/getblockbylimitnext"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {}
                                    """)));

            // when
            var blocks = client.getBlocksByRange(100L, 102L);

            // then
            assertThat(blocks).isEmpty();
        }

        @Test
        @DisplayName("throws IllegalArgumentException when range exceeds 100")
        void throwsOnExcessiveRange() {
            // when / then
            assertThatThrownBy(() -> client.getBlocksByRange(100L, 201L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cannot exceed 100");
        }
    }

    @Nested
    @DisplayName("error handling")
    class ErrorHandling {

        @Test
        @DisplayName("throws TronRpcException on API error response")
        void throwsOnApiError() {
            // given
            stubFor(post(urlEqualTo("/walletsolidity/getblockbynum"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {"Error": "class java.lang.IndexOutOfBoundsException : null"}
                                    """)));

            // when / then
            assertThatThrownBy(() -> client.getBlockByNumber(999999999L))
                    .isInstanceOf(TronRpcException.class)
                    .hasMessageContaining("IndexOutOfBoundsException");
        }

        @Test
        @DisplayName("throws TronRpcException on malformed JSON response")
        void throwsOnMalformedJson() {
            // given
            stubFor(post(urlEqualTo("/walletsolidity/getblockbynum"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("not valid json")));

            // when / then
            assertThatThrownBy(() -> client.getBlockByNumber(100L))
                    .isInstanceOf(TronRpcException.class)
                    .hasMessageContaining("parse error");
        }

        @Test
        @DisplayName("throws TronRpcException on HTTP error status")
        void throwsOnHttpError() {
            // given
            stubFor(post(urlEqualTo("/walletsolidity/getblockbynum"))
                    .willReturn(aResponse()
                            .withStatus(503)
                            .withBody("Service Unavailable")));

            // when / then
            assertThatThrownBy(() -> client.getBlockByNumber(100L))
                    .isInstanceOf(TronRpcException.class)
                    .hasMessageContaining("statusCode=503");
        }

        @Test
        @DisplayName("does not send API key header when apiKey is blank")
        void doesNotSendApiKeyWhenBlank(WireMockRuntimeInfo wmRuntimeInfo) {
            // given
            var clientWithoutKey = new TronRpcClient(
                    wmRuntimeInfo.getHttpBaseUrl(), "", TIMEOUT);
            stubFor(post(urlEqualTo("/walletsolidity/getnowblock"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {
                                      "blockID": "block1",
                                      "block_header": {
                                        "raw_data": {
                                          "number": 100,
                                          "timestamp": 1710000000000,
                                          "parentHash": "parent"
                                        }
                                      }
                                    }
                                    """)));

            // when
            clientWithoutKey.getLatestSolidifiedBlockNumber();

            // then
            verify(postRequestedFor(urlEqualTo("/walletsolidity/getnowblock"))
                    .withoutHeader("TRON-PRO-API-KEY"));
        }
    }

    @Nested
    @DisplayName("API key header verification")
    class ApiKeyHeaderVerification {

        @Test
        @DisplayName("sends API key header on getBlockByNumber")
        void sendsApiKeyOnGetBlock() {
            // given
            stubFor(post(urlEqualTo("/walletsolidity/getblockbynum"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {
                                      "blockID": "block1",
                                      "block_header": {
                                        "raw_data": {
                                          "number": 100,
                                          "timestamp": 1710000000000,
                                          "parentHash": "parent"
                                        }
                                      }
                                    }
                                    """)));

            // when
            client.getBlockByNumber(100L);

            // then
            verify(postRequestedFor(urlEqualTo("/walletsolidity/getblockbynum"))
                    .withHeader("TRON-PRO-API-KEY", equalTo(API_KEY))
                    .withHeader("Content-Type", equalTo("application/json")));
        }

        @Test
        @DisplayName("sends API key header on getTransactionInfoByBlockNum")
        void sendsApiKeyOnGetTransactionInfo() {
            // given
            stubFor(post(urlEqualTo("/walletsolidity/gettransactioninfobyblocknum"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("[]")));

            // when
            client.getTransactionInfoByBlockNum(100L);

            // then
            verify(postRequestedFor(urlEqualTo("/walletsolidity/gettransactioninfobyblocknum"))
                    .withHeader("TRON-PRO-API-KEY", equalTo(API_KEY)));
        }

        @Test
        @DisplayName("sends API key header on getBlocksByRange")
        void sendsApiKeyOnGetBlocksByRange() {
            // given
            stubFor(post(urlEqualTo("/wallet/getblockbylimitnext"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{}")));

            // when
            client.getBlocksByRange(100L, 102L);

            // then
            verify(postRequestedFor(urlEqualTo("/wallet/getblockbylimitnext"))
                    .withHeader("TRON-PRO-API-KEY", equalTo(API_KEY)));
        }
    }
}
