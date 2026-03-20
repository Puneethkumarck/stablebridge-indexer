package com.stablebridge.indexer.infrastructure.chain.bitcoin;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@WireMockTest(httpPort = 0)
@DisplayName("BitcoinRpcClient")
class BitcoinRpcClientTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final String USERNAME = "bitcoin";
    private static final String PASSWORD = "secret";

    private BitcoinRpcClient client;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wmRuntimeInfo) {
        var wireMockUrl = wmRuntimeInfo.getHttpBaseUrl();
        client = new BitcoinRpcClient(wireMockUrl, USERNAME, PASSWORD, TIMEOUT);
    }

    @Nested
    @DisplayName("getBlockCount")
    class GetBlockCount {

        @Test
        @DisplayName("returns current block height")
        void returnsCurrentBlockHeight() {
            // given
            stubFor(post(urlEqualTo("/"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {"jsonrpc":"1.0","id":1,"result":840000,"error":null}
                                    """)));

            // when
            var result = client.getBlockCount();

            // then
            assertThat(result).isEqualTo(840000L);
        }

        @Test
        @DisplayName("sends Basic Auth header on every request")
        void sendsBasicAuthHeader() {
            // given
            stubFor(post(urlEqualTo("/"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {"jsonrpc":"1.0","id":1,"result":840000,"error":null}
                                    """)));

            // when
            client.getBlockCount();

            // then
            verify(postRequestedFor(urlEqualTo("/"))
                    .withHeader("Authorization", equalTo("Basic Yml0Y29pbjpzZWNyZXQ=")));
        }

        @Test
        @DisplayName("sends JSON-RPC 1.0 request format")
        void sendsJsonRpc10Format() {
            // given
            stubFor(post(urlEqualTo("/"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {"jsonrpc":"1.0","id":1,"result":840000,"error":null}
                                    """)));

            // when
            client.getBlockCount();

            // then
            verify(postRequestedFor(urlEqualTo("/"))
                    .withRequestBody(containing("\"jsonrpc\":\"1.0\""))
                    .withRequestBody(containing("\"method\":\"getblockcount\"")));
        }

        @Test
        @DisplayName("throws BitcoinRpcException on RPC error response")
        void throwsOnRpcError() {
            // given
            stubFor(post(urlEqualTo("/"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {"jsonrpc":"1.0","id":1,"result":null,"error":{"code":-1,"message":"Method not found"}}
                                    """)));

            // when / then
            assertThatThrownBy(() -> client.getBlockCount())
                    .isInstanceOf(BitcoinRpcException.class)
                    .hasMessageContaining("Method not found");
        }

        @Test
        @DisplayName("throws BitcoinRpcException on HTTP 500")
        void throwsOnHttp500() {
            // given
            stubFor(post(urlEqualTo("/"))
                    .willReturn(aResponse()
                            .withStatus(500)
                            .withBody("Internal Server Error")));

            // when / then
            assertThatThrownBy(() -> client.getBlockCount())
                    .isInstanceOf(BitcoinRpcException.class)
                    .hasMessageContaining("statusCode=500");
        }

        @Test
        @DisplayName("throws BitcoinRpcException on HTTP 401 Unauthorized")
        void throwsOnHttp401() {
            // given
            stubFor(post(urlEqualTo("/"))
                    .willReturn(aResponse()
                            .withStatus(401)
                            .withBody("Unauthorized")));

            // when / then
            assertThatThrownBy(() -> client.getBlockCount())
                    .isInstanceOf(BitcoinRpcException.class)
                    .hasMessageContaining("statusCode=401");
        }
    }

    @Nested
    @DisplayName("getBlockHash")
    class GetBlockHash {

        @Test
        @DisplayName("returns block hash for given height")
        void returnsBlockHashForGivenHeight() {
            // given
            stubFor(post(urlEqualTo("/"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {"jsonrpc":"1.0","id":2,"result":"0000000000000000000320283a032748cef8227873ff4872689bf23f1cda83a5","error":null}
                                    """)));

            // when
            var result = client.getBlockHash(840000L);

            // then
            assertThat(result).isEqualTo("0000000000000000000320283a032748cef8227873ff4872689bf23f1cda83a5");
        }

        @Test
        @DisplayName("sends height parameter in request body")
        void sendsHeightParameter() {
            // given
            stubFor(post(urlEqualTo("/"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {"jsonrpc":"1.0","id":2,"result":"000000000000hash","error":null}
                                    """)));

            // when
            client.getBlockHash(840000L);

            // then
            verify(postRequestedFor(urlEqualTo("/"))
                    .withRequestBody(containing("\"method\":\"getblockhash\""))
                    .withRequestBody(containing("\"params\":[840000]")));
        }

        @Test
        @DisplayName("throws BitcoinRpcException on invalid block height")
        void throwsOnInvalidBlockHeight() {
            // given
            stubFor(post(urlEqualTo("/"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {"jsonrpc":"1.0","id":2,"result":null,"error":{"code":-8,"message":"Block height out of range"}}
                                    """)));

            // when / then
            assertThatThrownBy(() -> client.getBlockHash(999999999L))
                    .isInstanceOf(BitcoinRpcException.class)
                    .hasMessageContaining("Block height out of range");
        }
    }

    @Nested
    @DisplayName("getBlock")
    class GetBlock {

        @Test
        @DisplayName("returns block with fully decoded transactions")
        void returnsBlockWithFullyDecodedTransactions() {
            // given
            stubFor(post(urlEqualTo("/"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {
                                      "jsonrpc": "1.0",
                                      "id": 3,
                                      "result": {
                                        "hash": "0000000000000000000320283a032748cef8227873ff4872689bf23f1cda83a5",
                                        "height": 840000,
                                        "time": 1713571767,
                                        "confirmations": 100,
                                        "tx": [
                                          {
                                            "txid": "tx_coinbase_001",
                                            "vin": [
                                              {
                                                "txid": null,
                                                "vout": 0,
                                                "scriptSig": {
                                                  "asm": "OP_0 coinbase",
                                                  "hex": "03a0d20c"
                                                }
                                              }
                                            ],
                                            "vout": [
                                              {
                                                "value": 6.25000000,
                                                "n": 0,
                                                "scriptPubKey": {
                                                  "asm": "OP_HASH160 hash OP_EQUAL",
                                                  "hex": "a914...",
                                                  "type": "scripthash",
                                                  "address": "3EktnHQD7RiAE6uzMj2ZifT9YgRnMLkHnR"
                                                }
                                              }
                                            ]
                                          },
                                          {
                                            "txid": "tx_transfer_002",
                                            "vin": [
                                              {
                                                "txid": "prev_tx_hash_001",
                                                "vout": 1,
                                                "scriptSig": {
                                                  "asm": "sig pubkey",
                                                  "hex": "4830..."
                                                }
                                              }
                                            ],
                                            "vout": [
                                              {
                                                "value": 0.50000000,
                                                "n": 0,
                                                "scriptPubKey": {
                                                  "asm": "OP_DUP OP_HASH160 ...",
                                                  "hex": "76a914...",
                                                  "type": "pubkeyhash",
                                                  "address": "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa"
                                                }
                                              },
                                              {
                                                "value": 0.49990000,
                                                "n": 1,
                                                "scriptPubKey": {
                                                  "asm": "OP_DUP OP_HASH160 ...",
                                                  "hex": "76a914...",
                                                  "type": "pubkeyhash",
                                                  "address": "1BvBMSEYstWetqTFn5Au4m4GFg7xJaNVN2"
                                                }
                                              }
                                            ]
                                          }
                                        ]
                                      },
                                      "error": null
                                    }
                                    """)));

            // when
            var block = client.getBlock("0000000000000000000320283a032748cef8227873ff4872689bf23f1cda83a5");

            // then
            var expected = BtcBlock.builder()
                    .hash("0000000000000000000320283a032748cef8227873ff4872689bf23f1cda83a5")
                    .height(840000L)
                    .time(1713571767L)
                    .confirmations(100)
                    .tx(List.of(
                            BtcTransaction.builder()
                                    .txid("tx_coinbase_001")
                                    .vin(List.of(
                                            BtcVin.builder()
                                                    .txid(null)
                                                    .vout(0)
                                                    .scriptSig(BtcScriptSig.builder()
                                                            .asm("OP_0 coinbase")
                                                            .hex("03a0d20c")
                                                            .build())
                                                    .build()))
                                    .vout(List.of(
                                            BtcVout.builder()
                                                    .value(new BigDecimal("6.25000000"))
                                                    .n(0)
                                                    .scriptPubKey(BtcScriptPubKey.builder()
                                                            .asm("OP_HASH160 hash OP_EQUAL")
                                                            .hex("a914...")
                                                            .type("scripthash")
                                                            .address("3EktnHQD7RiAE6uzMj2ZifT9YgRnMLkHnR")
                                                            .build())
                                                    .build()))
                                    .build(),
                            BtcTransaction.builder()
                                    .txid("tx_transfer_002")
                                    .vin(List.of(
                                            BtcVin.builder()
                                                    .txid("prev_tx_hash_001")
                                                    .vout(1)
                                                    .scriptSig(BtcScriptSig.builder()
                                                            .asm("sig pubkey")
                                                            .hex("4830...")
                                                            .build())
                                                    .build()))
                                    .vout(List.of(
                                            BtcVout.builder()
                                                    .value(new BigDecimal("0.50000000"))
                                                    .n(0)
                                                    .scriptPubKey(BtcScriptPubKey.builder()
                                                            .asm("OP_DUP OP_HASH160 ...")
                                                            .hex("76a914...")
                                                            .type("pubkeyhash")
                                                            .address("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa")
                                                            .build())
                                                    .build(),
                                            BtcVout.builder()
                                                    .value(new BigDecimal("0.49990000"))
                                                    .n(1)
                                                    .scriptPubKey(BtcScriptPubKey.builder()
                                                            .asm("OP_DUP OP_HASH160 ...")
                                                            .hex("76a914...")
                                                            .type("pubkeyhash")
                                                            .address("1BvBMSEYstWetqTFn5Au4m4GFg7xJaNVN2")
                                                            .build())
                                                    .build()))
                                    .build()))
                    .build();
            assertThat(block)
                    .usingRecursiveComparison()
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("sends hash and verbosity=2 parameters")
        void sendsHashAndVerbosityParameters() {
            // given
            stubFor(post(urlEqualTo("/"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {
                                      "jsonrpc": "1.0",
                                      "id": 3,
                                      "result": {
                                        "hash": "000000hash",
                                        "height": 1,
                                        "time": 1713571767,
                                        "confirmations": 6,
                                        "tx": []
                                      },
                                      "error": null
                                    }
                                    """)));

            // when
            client.getBlock("000000hash");

            // then
            verify(postRequestedFor(urlEqualTo("/"))
                    .withRequestBody(containing("\"method\":\"getblock\""))
                    .withRequestBody(containing("\"params\":[\"000000hash\",2]")));
        }

        @Test
        @DisplayName("returns block with empty transaction list")
        void returnsBlockWithEmptyTransactions() {
            // given
            stubFor(post(urlEqualTo("/"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {
                                      "jsonrpc": "1.0",
                                      "id": 3,
                                      "result": {
                                        "hash": "000000empty",
                                        "height": 0,
                                        "time": 1231006505,
                                        "confirmations": 900000,
                                        "tx": []
                                      },
                                      "error": null
                                    }
                                    """)));

            // when
            var block = client.getBlock("000000empty");

            // then
            assertThat(block.tx()).isEmpty();
        }

        @Test
        @DisplayName("handles legacy addresses array in scriptPubKey")
        void handlesLegacyAddressesArray() {
            // given
            stubFor(post(urlEqualTo("/"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {
                                      "jsonrpc": "1.0",
                                      "id": 3,
                                      "result": {
                                        "hash": "000000legacy",
                                        "height": 100,
                                        "time": 1231006505,
                                        "confirmations": 6,
                                        "tx": [
                                          {
                                            "txid": "tx_legacy_001",
                                            "vin": [],
                                            "vout": [
                                              {
                                                "value": 1.00000000,
                                                "n": 0,
                                                "scriptPubKey": {
                                                  "asm": "multisig",
                                                  "hex": "5221...",
                                                  "type": "multisig",
                                                  "addresses": [
                                                    "1addr1",
                                                    "1addr2"
                                                  ]
                                                }
                                              }
                                            ]
                                          }
                                        ]
                                      },
                                      "error": null
                                    }
                                    """)));

            // when
            var block = client.getBlock("000000legacy");

            // then
            var vout = block.tx().getFirst().vout().getFirst();
            assertThat(vout.scriptPubKey().addresses()).containsExactly("1addr1", "1addr2");
        }

        @Test
        @DisplayName("throws BitcoinRpcException on invalid block hash")
        void throwsOnInvalidBlockHash() {
            // given
            stubFor(post(urlEqualTo("/"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {"jsonrpc":"1.0","id":3,"result":null,"error":{"code":-5,"message":"Block not found"}}
                                    """)));

            // when / then
            assertThatThrownBy(() -> client.getBlock("invalidhash"))
                    .isInstanceOf(BitcoinRpcException.class)
                    .hasMessageContaining("Block not found");
        }
    }

    @Nested
    @DisplayName("error handling")
    class ErrorHandling {

        @Test
        @DisplayName("throws BitcoinRpcException with error details on RPC error")
        void throwsWithErrorDetails() {
            // given
            stubFor(post(urlEqualTo("/"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {"jsonrpc":"1.0","id":1,"result":null,"error":{"code":-28,"message":"Loading block index..."}}
                                    """)));

            // when / then
            assertThatThrownBy(() -> client.getBlockCount())
                    .isInstanceOf(BitcoinRpcException.class)
                    .hasMessageContaining("Loading block index...")
                    .hasMessageContaining("-28");
        }

        @Test
        @DisplayName("throws BitcoinRpcException on HTTP error status")
        void throwsOnHttpError() {
            // given
            stubFor(post(urlEqualTo("/"))
                    .willReturn(aResponse()
                            .withStatus(403)
                            .withBody("Forbidden")));

            // when / then
            assertThatThrownBy(() -> client.getBlockCount())
                    .isInstanceOf(BitcoinRpcException.class)
                    .hasMessageContaining("statusCode=403");
        }

        @Test
        @DisplayName("throws BitcoinRpcException on malformed JSON response")
        void throwsOnMalformedJson() {
            // given
            stubFor(post(urlEqualTo("/"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("not valid json")));

            // when / then
            assertThatThrownBy(() -> client.getBlockCount())
                    .isInstanceOf(BitcoinRpcException.class)
                    .hasMessageContaining("parse error");
        }
    }
}
