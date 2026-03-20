package com.stablebridge.indexer.infrastructure.chain.solana;

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
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@WireMockTest(httpPort = 0)
@DisplayName("SolanaRpcClient")
class SolanaRpcClientTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private SolanaRpcClient client;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wmRuntimeInfo) {
        var wireMockUrl = wmRuntimeInfo.getHttpBaseUrl();
        client = new SolanaRpcClient(wireMockUrl, TIMEOUT);
    }

    @Nested
    @DisplayName("getLatestSlot")
    class GetLatestSlot {

        @Test
        @DisplayName("returns correct slot number from response")
        void returnsCorrectSlotNumber() {
            // given
            stubFor(post(urlEqualTo("/"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {"jsonrpc":"2.0","id":1,"result":166974442}
                                    """)));

            // when
            var result = client.getLatestSlot();

            // then
            assertThat(result).isEqualTo(166974442L);
        }

        @Test
        @DisplayName("throws SolanaRpcException on RPC error response")
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
            assertThatThrownBy(() -> client.getLatestSlot())
                    .isInstanceOf(SolanaRpcException.class)
                    .hasMessageContaining("Method not found");
        }

        @Test
        @DisplayName("throws SolanaRpcException on HTTP 500")
        void throwsOnHttp500() {
            // given
            stubFor(post(urlEqualTo("/"))
                    .willReturn(aResponse()
                            .withStatus(500)
                            .withBody("Internal Server Error")));

            // when / then
            assertThatThrownBy(() -> client.getLatestSlot())
                    .isInstanceOf(SolanaRpcException.class)
                    .hasMessageContaining("statusCode=500");
        }

        @Test
        @DisplayName("throws SolanaRpcException on HTTP 429 rate limit")
        void throwsOnRateLimit() {
            // given
            stubFor(post(urlEqualTo("/"))
                    .willReturn(aResponse()
                            .withStatus(429)
                            .withBody("Rate limited")));

            // when / then
            assertThatThrownBy(() -> client.getLatestSlot())
                    .isInstanceOf(SolanaRpcException.class)
                    .hasMessageContaining("statusCode=429");
        }
    }

    @Nested
    @DisplayName("getBlock")
    class GetBlock {

        @Test
        @DisplayName("returns SolanaBlock with transactions")
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
                                        "parentSlot": 166974440,
                                        "blockhash": "3Eq21vXNB5s86c62bVuUfTeaMif1N2kUqRPBmGRJhyTA",
                                        "previousBlockhash": "mfcyqEXB3DnHXki6KjjmZck6YjmZLvpAByy2fj4nh6B",
                                        "blockTime": 1700000000,
                                        "transactions": [
                                          {
                                            "transaction": {
                                              "message": {
                                                "accountKeys": [
                                                  "9aE476sH92Vz7DMPyq5WLPkrKWivxeuTKEFKd2sZZcde",
                                                  "11111111111111111111111111111111"
                                                ],
                                                "instructions": [
                                                  {
                                                    "programId": "11111111111111111111111111111111",
                                                    "accounts": [
                                                      "9aE476sH92Vz7DMPyq5WLPkrKWivxeuTKEFKd2sZZcde"
                                                    ],
                                                    "data": "3Bxs4ThwQbE4vyj5"
                                                  }
                                                ]
                                              },
                                              "signatures": [
                                                "5VERv8NMvzbJMEkV8xnrLkEaWRtSz9CosKDYjCJjBRnbJLgp8uirBgmQpjKhoR4tjF3ZpRzrFmBV6UjKdiSZkQUW"
                                              ]
                                            },
                                            "meta": {
                                              "err": null,
                                              "fee": 5000,
                                              "preBalances": [1000000000, 0],
                                              "postBalances": [999995000, 0],
                                              "preTokenBalances": [],
                                              "postTokenBalances": []
                                            }
                                          }
                                        ]
                                      }
                                    }
                                    """)));

            // when
            var block = client.getBlock(166974441L);

            // then
            var expected = SolanaBlock.builder()
                    .parentSlot(166974440L)
                    .blockhash("3Eq21vXNB5s86c62bVuUfTeaMif1N2kUqRPBmGRJhyTA")
                    .previousBlockhash("mfcyqEXB3DnHXki6KjjmZck6YjmZLvpAByy2fj4nh6B")
                    .blockTime(1700000000L)
                    .transactions(List.of(
                            SolanaTransaction.builder()
                                    .transaction(SolanaTransactionBody.builder()
                                            .message(SolanaTransactionMessage.builder()
                                                    .accountKeys(List.of(
                                                            "9aE476sH92Vz7DMPyq5WLPkrKWivxeuTKEFKd2sZZcde",
                                                            "11111111111111111111111111111111"))
                                                    .instructions(List.of(
                                                            SolanaInstruction.builder()
                                                                    .programId("11111111111111111111111111111111")
                                                                    .accounts(List.of(
                                                                            "9aE476sH92Vz7DMPyq5WLPkrKWivxeuTKEFKd2sZZcde"))
                                                                    .data("3Bxs4ThwQbE4vyj5")
                                                                    .build()))
                                                    .build())
                                            .signatures(List.of(
                                                    "5VERv8NMvzbJMEkV8xnrLkEaWRtSz9CosKDYjCJjBRnbJLgp8uirBgmQpjKhoR4tjF3ZpRzrFmBV6UjKdiSZkQUW"))
                                            .build())
                                    .meta(SolanaTransactionMeta.builder()
                                            .err(null)
                                            .fee(5000L)
                                            .preBalances(List.of(1000000000L, 0L))
                                            .postBalances(List.of(999995000L, 0L))
                                            .preTokenBalances(List.of())
                                            .postTokenBalances(List.of())
                                            .build())
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
                                        "parentSlot": 166974440,
                                        "blockhash": "3Eq21vXNB5s86c62bVuUfTeaMif1N2kUqRPBmGRJhyTA",
                                        "previousBlockhash": "mfcyqEXB3DnHXki6KjjmZck6YjmZLvpAByy2fj4nh6B",
                                        "blockTime": 1700000000,
                                        "transactions": []
                                      }
                                    }
                                    """)));

            // when
            var block = client.getBlock(166974441L);

            // then
            assertThat(block.transactions()).isEmpty();
        }

        @Test
        @DisplayName("converts blockTime to Instant correctly")
        void convertsBlockTimeToInstant() {
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
                                        "parentSlot": 100,
                                        "blockhash": "abc",
                                        "previousBlockhash": "def",
                                        "blockTime": 1700000000,
                                        "transactions": []
                                      }
                                    }
                                    """)));

            // when
            var block = client.getBlock(101L);

            // then
            assertThat(block.blockTimestamp()).isEqualTo(Instant.ofEpochSecond(1700000000L));
        }

        @Test
        @DisplayName("returns block with token balances in transaction meta")
        void returnsBlockWithTokenBalances() {
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
                                        "parentSlot": 200,
                                        "blockhash": "blockhash123",
                                        "previousBlockhash": "prevhash456",
                                        "blockTime": 1700000100,
                                        "transactions": [
                                          {
                                            "transaction": {
                                              "message": {
                                                "accountKeys": ["sender1", "receiver1", "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"],
                                                "instructions": []
                                              },
                                              "signatures": ["sig123"]
                                            },
                                            "meta": {
                                              "err": null,
                                              "fee": 5000,
                                              "preBalances": [1000000, 500000, 0],
                                              "postBalances": [995000, 505000, 0],
                                              "preTokenBalances": [
                                                {
                                                  "accountIndex": 1,
                                                  "mint": "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v",
                                                  "owner": "receiver1",
                                                  "uiTokenAmount": {
                                                    "amount": "1000000",
                                                    "decimals": 6,
                                                    "uiAmountString": "1.0"
                                                  }
                                                }
                                              ],
                                              "postTokenBalances": [
                                                {
                                                  "accountIndex": 1,
                                                  "mint": "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v",
                                                  "owner": "receiver1",
                                                  "uiTokenAmount": {
                                                    "amount": "2000000",
                                                    "decimals": 6,
                                                    "uiAmountString": "2.0"
                                                  }
                                                }
                                              ]
                                            }
                                          }
                                        ]
                                      }
                                    }
                                    """)));

            // when
            var block = client.getBlock(201L);

            // then
            var expectedPreTokenBalance = SolanaTokenBalance.builder()
                    .accountIndex(1)
                    .mint("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v")
                    .owner("receiver1")
                    .uiTokenAmount(SolanaTokenBalance.SolanaTokenAmount.builder()
                            .amount("1000000")
                            .decimals(6)
                            .uiAmountString("1.0")
                            .build())
                    .build();
            var expectedPostTokenBalance = SolanaTokenBalance.builder()
                    .accountIndex(1)
                    .mint("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v")
                    .owner("receiver1")
                    .uiTokenAmount(SolanaTokenBalance.SolanaTokenAmount.builder()
                            .amount("2000000")
                            .decimals(6)
                            .uiAmountString("2.0")
                            .build())
                    .build();
            var tx = block.transactions().getFirst();
            assertThat(tx.meta().preTokenBalances())
                    .usingRecursiveComparison()
                    .isEqualTo(List.of(expectedPreTokenBalance));
            assertThat(tx.meta().postTokenBalances())
                    .usingRecursiveComparison()
                    .isEqualTo(List.of(expectedPostTokenBalance));
        }

        @Test
        @DisplayName("throws SolanaRpcException on RPC error")
        void throwsOnRpcError() {
            // given
            stubFor(post(urlEqualTo("/"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {"jsonrpc":"2.0","id":2,"error":{"code":-32009,"message":"Slot 123 was skipped"}}
                                    """)));

            // when / then
            assertThatThrownBy(() -> client.getBlock(123L))
                    .isInstanceOf(SolanaRpcException.class)
                    .hasMessageContaining("Slot 123 was skipped")
                    .hasMessageContaining("-32009");
        }
    }

    @Nested
    @DisplayName("getTransaction")
    class GetTransaction {

        @Test
        @DisplayName("returns SolanaTransaction with full details")
        void returnsTransactionWithFullDetails() {
            // given
            var signature = "5VERv8NMvzbJMEkV8xnrLkEaWRtSz9CosKDYjCJjBRnbJLgp8uirBgmQpjKhoR4tjF3ZpRzrFmBV6UjKdiSZkQUW";
            stubFor(post(urlEqualTo("/"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {
                                      "jsonrpc": "2.0",
                                      "id": 3,
                                      "result": {
                                        "transaction": {
                                          "message": {
                                            "accountKeys": [
                                              "9aE476sH92Vz7DMPyq5WLPkrKWivxeuTKEFKd2sZZcde",
                                              "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"
                                            ],
                                            "instructions": [
                                              {
                                                "programId": "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA",
                                                "accounts": ["9aE476sH92Vz7DMPyq5WLPkrKWivxeuTKEFKd2sZZcde"],
                                                "data": "3Bxs4ThwQbE4vyj5"
                                              }
                                            ]
                                          },
                                          "signatures": [
                                            "5VERv8NMvzbJMEkV8xnrLkEaWRtSz9CosKDYjCJjBRnbJLgp8uirBgmQpjKhoR4tjF3ZpRzrFmBV6UjKdiSZkQUW"
                                          ]
                                        },
                                        "meta": {
                                          "err": null,
                                          "fee": 5000,
                                          "preBalances": [1000000000],
                                          "postBalances": [999995000],
                                          "preTokenBalances": [],
                                          "postTokenBalances": []
                                        }
                                      }
                                    }
                                    """)));

            // when
            var tx = client.getTransaction(signature);

            // then
            var expected = SolanaTransaction.builder()
                    .transaction(SolanaTransactionBody.builder()
                            .message(SolanaTransactionMessage.builder()
                                    .accountKeys(List.of(
                                            "9aE476sH92Vz7DMPyq5WLPkrKWivxeuTKEFKd2sZZcde",
                                            "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"))
                                    .instructions(List.of(
                                            SolanaInstruction.builder()
                                                    .programId("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA")
                                                    .accounts(List.of("9aE476sH92Vz7DMPyq5WLPkrKWivxeuTKEFKd2sZZcde"))
                                                    .data("3Bxs4ThwQbE4vyj5")
                                                    .build()))
                                    .build())
                            .signatures(List.of(signature))
                            .build())
                    .meta(SolanaTransactionMeta.builder()
                            .err(null)
                            .fee(5000L)
                            .preBalances(List.of(1000000000L))
                            .postBalances(List.of(999995000L))
                            .preTokenBalances(List.of())
                            .postTokenBalances(List.of())
                            .build())
                    .build();
            assertThat(tx).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        @DisplayName("throws SolanaRpcException on RPC error")
        void throwsOnRpcError() {
            // given
            stubFor(post(urlEqualTo("/"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {"jsonrpc":"2.0","id":3,"error":{"code":-32602,"message":"Invalid params"}}
                                    """)));

            // when / then
            assertThatThrownBy(() -> client.getTransaction("invalid_sig"))
                    .isInstanceOf(SolanaRpcException.class)
                    .hasMessageContaining("Invalid params");
        }

        @Test
        @DisplayName("returns null result for non-existent transaction")
        void returnsNullForNonExistentTransaction() {
            // given
            stubFor(post(urlEqualTo("/"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {"jsonrpc":"2.0","id":3,"result":null}
                                    """)));

            // when
            var tx = client.getTransaction("nonexistent_sig");

            // then
            assertThat(tx).isNull();
        }
    }

    @Nested
    @DisplayName("error handling")
    class ErrorHandling {

        @Test
        @DisplayName("throws SolanaRpcException with error details on RPC error")
        void throwsWithErrorDetails() {
            // given
            stubFor(post(urlEqualTo("/"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {"jsonrpc":"2.0","id":1,"error":{"code":-32000,"message":"Server error"}}
                                    """)));

            // when / then
            assertThatThrownBy(() -> client.getLatestSlot())
                    .isInstanceOf(SolanaRpcException.class)
                    .hasMessageContaining("Server error")
                    .hasMessageContaining("-32000");
        }

        @Test
        @DisplayName("throws SolanaRpcException on malformed JSON response")
        void throwsOnMalformedJson() {
            // given
            stubFor(post(urlEqualTo("/"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("not valid json")));

            // when / then
            assertThatThrownBy(() -> client.getLatestSlot())
                    .isInstanceOf(SolanaRpcException.class)
                    .hasMessageContaining("parse error");
        }
    }
}
