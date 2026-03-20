package com.stablebridge.indexer.infrastructure.chain.evm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.stablebridge.indexer.domain.model.BlockResult;
import com.stablebridge.indexer.domain.model.IndexedBlock;
import com.stablebridge.indexer.domain.model.Transfer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static com.stablebridge.indexer.domain.model.ChainId.ETHEREUM;
import static org.assertj.core.api.Assertions.assertThat;

@WireMockTest(httpPort = 0)
@DisplayName("EvmChainIndexer E2E")
class EvmChainIndexerE2eTest {

    private static final int BATCH_SIZE = 50;
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final long BLOCK_NUMBER = 19_500_032L;
    private static final String BLOCK_NUMBER_HEX = "0x1298c00";
    private static final String BLOCK_HASH =
            "0xb10c4a541234567890abcdef1234567890abcdef1234567890abcdef12345678";
    private static final String PARENT_HASH =
            "0xpa4e274a5234567890abcdef1234567890abcdef1234567890abcdef12345678";
    private static final String BLOCK_TIMESTAMP_HEX = "0x65b3e8c0";
    private static final Instant BLOCK_TIMESTAMP = HexUtils.hexToInstant(BLOCK_TIMESTAMP_HEX);

    private static final String USDC_CONTRACT = "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48";
    private static final String UNKNOWN_TOKEN_CONTRACT = "0x1111111111111111111111111111111111111111";
    private static final String TRANSFER_SIGNATURE =
            "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef";

    private static final String FROM_PADDED =
            "0x0000000000000000000000001234567890abcdef1234567890abcdef12345678";
    private static final String TO_PADDED =
            "0x000000000000000000000000abcdef1234567890abcdef1234567890abcdef12";
    private static final String FROM_ADDRESS = "0x1234567890abcdef1234567890abcdef12345678";
    private static final String TO_ADDRESS = "0xabcdef1234567890abcdef1234567890abcdef12";

    private static final String USDC_AMOUNT_HEX =
            "0x00000000000000000000000000000000000000000000000000000000000f4240";
    private static final String USDC_RAW_AMOUNT = "1000000";

    private static final String TX_HASH_USDC = "0xabc123def456789012345678901234567890abcdef1234567890abcdef123456";
    private static final String TX_HASH_UNKNOWN = "0xdef456789012345678901234567890abcdef1234567890abcdef1234567890ab";

    private static final String ETH_SENDER = "0xe74534de890abcdef1234567890abcdef12345678";
    private static final String ETH_RECEIVER = "0xe74ece14e0abcdef1234567890abcdef12345678";
    private static final String TX_HASH_ETH = "0xe74789012345678901234567890abcdef1234567890abcdef1234567890abcd";

    private EvmChainIndexer indexer;
    private EvmChainIndexer indexerWithNativeTransfers;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wmRuntimeInfo) {
        var objectMapper = new ObjectMapper();
        var rpcClient = new EvmRpcClient(
                wmRuntimeInfo.getHttpBaseUrl(), BATCH_SIZE, false, TIMEOUT, objectMapper);
        var resilientClient = new ResilientEvmRpcClient(
                rpcClient, "test_chain", 1, 100, 100);

        var usdcConfig = EvmTokenConfig.builder()
                .address(USDC_CONTRACT)
                .symbol("USDC")
                .decimals(6)
                .build();
        var erc20Parser = new EvmErc20TransferParser(ETHEREUM, List.of(usdcConfig));
        var nativeParser = new EvmNativeTransferParser(ETHEREUM, 18);

        indexer = new EvmChainIndexer(
                resilientClient, ETHEREUM, true, 0, false,
                nativeParser, erc20Parser);

        indexerWithNativeTransfers = new EvmChainIndexer(
                resilientClient, ETHEREUM, true, 0, true,
                nativeParser, erc20Parser);
    }

    @Nested
    @DisplayName("indexBlock")
    class IndexBlock {

        @Test
        @DisplayName("returns only USDC transfer when block has USDC and unknown token transfers")
        void indexBlock_withUsdcTransferAndUnknownToken_returnsOnlyUsdcTransfer() {
            // given
            stubFor(post(urlEqualTo("/"))
                    .inScenario("indexBlock")
                    .whenScenarioStateIs(STARTED)
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(blockResponseWithTwoTransactions()))
                    .willSetStateTo("block-fetched"));

            stubFor(post(urlEqualTo("/"))
                    .inScenario("indexBlock")
                    .whenScenarioStateIs("block-fetched")
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(batchReceiptResponseUsdcAndUnknown())));

            var expectedTransfer = Transfer.builder()
                    .txHash(TX_HASH_USDC)
                    .fromAddress(FROM_ADDRESS)
                    .toAddress(TO_ADDRESS)
                    .rawAmount(USDC_RAW_AMOUNT)
                    .amount(new BigDecimal(new BigInteger(USDC_RAW_AMOUNT))
                            .divide(BigDecimal.TEN.pow(6), MathContext.DECIMAL128))
                    .decimals(6)
                    .tokenSymbol("USDC")
                    .tokenContractAddress(USDC_CONTRACT)
                    .blockNumber(BLOCK_NUMBER)
                    .blockHash(BLOCK_HASH)
                    .transactionIndex(0)
                    .logIndex(0)
                    .chainId(ETHEREUM)
                    .timestamp(BLOCK_TIMESTAMP)
                    .nativeTransfer(false)
                    .build();

            var expectedBlock = IndexedBlock.builder()
                    .blockNumber(BLOCK_NUMBER)
                    .blockHash(BLOCK_HASH)
                    .parentHash(PARENT_HASH)
                    .timestamp(BLOCK_TIMESTAMP)
                    .chainId(ETHEREUM)
                    .transactionCount(2)
                    .build();

            var expected = BlockResult.builder()
                    .indexedBlock(expectedBlock)
                    .transfers(List.of(expectedTransfer))
                    .build();

            // when
            var result = indexer.indexBlock(BLOCK_NUMBER);

            // then
            assertThat(result)
                    .usingRecursiveComparison()
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("returns both ERC-20 and native transfers when native indexing is enabled")
        void indexBlock_withNativeTransfersEnabled_returnsBothErc20AndNative() {
            // given
            stubFor(post(urlEqualTo("/"))
                    .inScenario("indexBlockNative")
                    .whenScenarioStateIs(STARTED)
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(blockResponseWithEthAndUsdcTransactions()))
                    .willSetStateTo("block-fetched"));

            stubFor(post(urlEqualTo("/"))
                    .inScenario("indexBlockNative")
                    .whenScenarioStateIs("block-fetched")
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(batchReceiptResponseEthAndUsdc())));

            var expectedUsdcTransfer = Transfer.builder()
                    .txHash(TX_HASH_USDC)
                    .fromAddress(FROM_ADDRESS)
                    .toAddress(TO_ADDRESS)
                    .rawAmount(USDC_RAW_AMOUNT)
                    .amount(new BigDecimal(new BigInteger(USDC_RAW_AMOUNT))
                            .divide(BigDecimal.TEN.pow(6), MathContext.DECIMAL128))
                    .decimals(6)
                    .tokenSymbol("USDC")
                    .tokenContractAddress(USDC_CONTRACT)
                    .blockNumber(BLOCK_NUMBER)
                    .blockHash(BLOCK_HASH)
                    .transactionIndex(1)
                    .logIndex(0)
                    .chainId(ETHEREUM)
                    .timestamp(BLOCK_TIMESTAMP)
                    .nativeTransfer(false)
                    .build();

            // 1 ETH = 10^18 wei = 0xde0b6b3a7640000
            var ethRawBigInt = new BigInteger("de0b6b3a7640000", 16);
            var expectedNativeTransfer = Transfer.builder()
                    .txHash(TX_HASH_ETH)
                    .fromAddress(ETH_SENDER)
                    .toAddress(ETH_RECEIVER)
                    .rawAmount(ethRawBigInt.toString())
                    .amount(new BigDecimal(ethRawBigInt)
                            .divide(BigDecimal.TEN.pow(18), 18, RoundingMode.HALF_UP))
                    .decimals(18)
                    .tokenSymbol("ETH")
                    .tokenContractAddress(null)
                    .blockNumber(BLOCK_NUMBER)
                    .blockHash(BLOCK_HASH)
                    .transactionIndex(0)
                    .logIndex(-1)
                    .chainId(ETHEREUM)
                    .timestamp(BLOCK_TIMESTAMP)
                    .nativeTransfer(true)
                    .build();

            var expectedBlock = IndexedBlock.builder()
                    .blockNumber(BLOCK_NUMBER)
                    .blockHash(BLOCK_HASH)
                    .parentHash(PARENT_HASH)
                    .timestamp(BLOCK_TIMESTAMP)
                    .chainId(ETHEREUM)
                    .transactionCount(2)
                    .build();

            var expected = BlockResult.builder()
                    .indexedBlock(expectedBlock)
                    .transfers(List.of(expectedUsdcTransfer, expectedNativeTransfer))
                    .build();

            // when
            var result = indexerWithNativeTransfers.indexBlock(BLOCK_NUMBER);

            // then
            assertThat(result)
                    .usingRecursiveComparison()
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("returns empty transfers for block with no transactions")
        void indexBlock_emptyBlock_returnsEmptyTransfers() {
            // given
            stubFor(post(urlEqualTo("/"))
                    .inScenario("indexBlockEmpty")
                    .whenScenarioStateIs(STARTED)
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(emptyBlockResponse()))
                    .willSetStateTo("block-fetched"));

            stubFor(post(urlEqualTo("/"))
                    .inScenario("indexBlockEmpty")
                    .whenScenarioStateIs("block-fetched")
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("[]")));

            var expectedBlock = IndexedBlock.builder()
                    .blockNumber(BLOCK_NUMBER)
                    .blockHash(BLOCK_HASH)
                    .parentHash(PARENT_HASH)
                    .timestamp(BLOCK_TIMESTAMP)
                    .chainId(ETHEREUM)
                    .transactionCount(0)
                    .build();

            var expected = BlockResult.builder()
                    .indexedBlock(expectedBlock)
                    .transfers(List.of())
                    .build();

            // when
            var result = indexer.indexBlock(BLOCK_NUMBER);

            // then
            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("getLatestFinalizedBlockNumber")
    class GetLatestFinalizedBlockNumber {

        @Test
        @DisplayName("returns latest block number from RPC")
        void getLatestFinalizedBlockNumber_returnsLatestBlock() {
            // given
            stubFor(post(urlEqualTo("/"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {"jsonrpc":"2.0","id":1,"result":"%s"}
                                    """.formatted(BLOCK_NUMBER_HEX))));

            // when
            var result = indexer.getLatestFinalizedBlockNumber();

            // then
            assertThat(result).isEqualTo(BLOCK_NUMBER);
        }
    }

    // -- JSON response builders (package-private ACL DTOs require in-package helpers) --

    private static String blockResponseWithTwoTransactions() {
        return """
                {
                  "jsonrpc": "2.0",
                  "id": 1,
                  "result": {
                    "number": "%s",
                    "hash": "%s",
                    "parentHash": "%s",
                    "timestamp": "%s",
                    "transactions": [
                      {
                        "hash": "%s",
                        "from": "%s",
                        "to": "%s",
                        "value": "0x0",
                        "input": "0xa9059cbb",
                        "blockNumber": "%s",
                        "transactionIndex": "0x0",
                        "blockHash": "%s"
                      },
                      {
                        "hash": "%s",
                        "from": "%s",
                        "to": "%s",
                        "value": "0x0",
                        "input": "0xa9059cbb",
                        "blockNumber": "%s",
                        "transactionIndex": "0x1",
                        "blockHash": "%s"
                      }
                    ]
                  }
                }
                """.formatted(
                BLOCK_NUMBER_HEX, BLOCK_HASH, PARENT_HASH, BLOCK_TIMESTAMP_HEX,
                TX_HASH_USDC, FROM_ADDRESS, USDC_CONTRACT, BLOCK_NUMBER_HEX, BLOCK_HASH,
                TX_HASH_UNKNOWN, FROM_ADDRESS, UNKNOWN_TOKEN_CONTRACT, BLOCK_NUMBER_HEX, BLOCK_HASH);
    }

    private static String batchReceiptResponseUsdcAndUnknown() {
        return """
                [
                  {
                    "jsonrpc": "2.0",
                    "id": 1,
                    "result": {
                      "transactionHash": "%s",
                      "transactionIndex": "0x0",
                      "blockNumber": "%s",
                      "blockHash": "%s",
                      "from": "%s",
                      "to": "%s",
                      "status": "0x1",
                      "logs": [
                        {
                          "address": "%s",
                          "topics": [
                            "%s",
                            "%s",
                            "%s"
                          ],
                          "data": "%s",
                          "logIndex": "0x0",
                          "transactionIndex": "0x0",
                          "transactionHash": "%s",
                          "blockNumber": "%s",
                          "blockHash": "%s"
                        }
                      ]
                    }
                  },
                  {
                    "jsonrpc": "2.0",
                    "id": 2,
                    "result": {
                      "transactionHash": "%s",
                      "transactionIndex": "0x1",
                      "blockNumber": "%s",
                      "blockHash": "%s",
                      "from": "%s",
                      "to": "%s",
                      "status": "0x1",
                      "logs": [
                        {
                          "address": "%s",
                          "topics": [
                            "%s",
                            "%s",
                            "%s"
                          ],
                          "data": "%s",
                          "logIndex": "0x0",
                          "transactionIndex": "0x1",
                          "transactionHash": "%s",
                          "blockNumber": "%s",
                          "blockHash": "%s"
                        }
                      ]
                    }
                  }
                ]
                """.formatted(
                // Receipt 1: USDC transfer
                TX_HASH_USDC, BLOCK_NUMBER_HEX, BLOCK_HASH,
                FROM_ADDRESS, USDC_CONTRACT,
                USDC_CONTRACT, TRANSFER_SIGNATURE, FROM_PADDED, TO_PADDED, USDC_AMOUNT_HEX,
                TX_HASH_USDC, BLOCK_NUMBER_HEX, BLOCK_HASH,
                // Receipt 2: Unknown token transfer
                TX_HASH_UNKNOWN, BLOCK_NUMBER_HEX, BLOCK_HASH,
                FROM_ADDRESS, UNKNOWN_TOKEN_CONTRACT,
                UNKNOWN_TOKEN_CONTRACT, TRANSFER_SIGNATURE,
                "0x0000000000000000000000005555555555555555555555555555555555555555",
                "0x0000000000000000000000006666666666666666666666666666666666666666",
                "0x0000000000000000000000000000000000000000000000000000000000002710",
                TX_HASH_UNKNOWN, BLOCK_NUMBER_HEX, BLOCK_HASH);
    }

    private static String blockResponseWithEthAndUsdcTransactions() {
        return """
                {
                  "jsonrpc": "2.0",
                  "id": 1,
                  "result": {
                    "number": "%s",
                    "hash": "%s",
                    "parentHash": "%s",
                    "timestamp": "%s",
                    "transactions": [
                      {
                        "hash": "%s",
                        "from": "%s",
                        "to": "%s",
                        "value": "0xde0b6b3a7640000",
                        "input": "0x",
                        "blockNumber": "%s",
                        "transactionIndex": "0x0",
                        "blockHash": "%s"
                      },
                      {
                        "hash": "%s",
                        "from": "%s",
                        "to": "%s",
                        "value": "0x0",
                        "input": "0xa9059cbb",
                        "blockNumber": "%s",
                        "transactionIndex": "0x1",
                        "blockHash": "%s"
                      }
                    ]
                  }
                }
                """.formatted(
                BLOCK_NUMBER_HEX, BLOCK_HASH, PARENT_HASH, BLOCK_TIMESTAMP_HEX,
                TX_HASH_ETH, ETH_SENDER, ETH_RECEIVER, BLOCK_NUMBER_HEX, BLOCK_HASH,
                TX_HASH_USDC, FROM_ADDRESS, USDC_CONTRACT, BLOCK_NUMBER_HEX, BLOCK_HASH);
    }

    private static String batchReceiptResponseEthAndUsdc() {
        return """
                [
                  {
                    "jsonrpc": "2.0",
                    "id": 1,
                    "result": {
                      "transactionHash": "%s",
                      "transactionIndex": "0x0",
                      "blockNumber": "%s",
                      "blockHash": "%s",
                      "from": "%s",
                      "to": "%s",
                      "status": "0x1",
                      "logs": []
                    }
                  },
                  {
                    "jsonrpc": "2.0",
                    "id": 2,
                    "result": {
                      "transactionHash": "%s",
                      "transactionIndex": "0x1",
                      "blockNumber": "%s",
                      "blockHash": "%s",
                      "from": "%s",
                      "to": "%s",
                      "status": "0x1",
                      "logs": [
                        {
                          "address": "%s",
                          "topics": [
                            "%s",
                            "%s",
                            "%s"
                          ],
                          "data": "%s",
                          "logIndex": "0x0",
                          "transactionIndex": "0x1",
                          "transactionHash": "%s",
                          "blockNumber": "%s",
                          "blockHash": "%s"
                        }
                      ]
                    }
                  }
                ]
                """.formatted(
                // Receipt 1: ETH transfer (no logs)
                TX_HASH_ETH, BLOCK_NUMBER_HEX, BLOCK_HASH,
                ETH_SENDER, ETH_RECEIVER,
                // Receipt 2: USDC transfer
                TX_HASH_USDC, BLOCK_NUMBER_HEX, BLOCK_HASH,
                FROM_ADDRESS, USDC_CONTRACT,
                USDC_CONTRACT, TRANSFER_SIGNATURE, FROM_PADDED, TO_PADDED, USDC_AMOUNT_HEX,
                TX_HASH_USDC, BLOCK_NUMBER_HEX, BLOCK_HASH);
    }

    private static String emptyBlockResponse() {
        return """
                {
                  "jsonrpc": "2.0",
                  "id": 1,
                  "result": {
                    "number": "%s",
                    "hash": "%s",
                    "parentHash": "%s",
                    "timestamp": "%s",
                    "transactions": []
                  }
                }
                """.formatted(BLOCK_NUMBER_HEX, BLOCK_HASH, PARENT_HASH, BLOCK_TIMESTAMP_HEX);
    }
}
