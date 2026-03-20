package com.stablebridge.indexer.infrastructure.chain.evm;

import com.stablebridge.indexer.domain.model.ChainId;
import com.stablebridge.indexer.domain.model.Transfer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.List;

import static com.stablebridge.indexer.domain.model.ChainId.ETHEREUM;
import static com.stablebridge.indexer.infrastructure.chain.evm.EvmErc20TransferParser.TRANSFER_EVENT_SIGNATURE;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EvmErc20TransferParser")
class EvmErc20TransferParserTest {

    private static final String USDC_CONTRACT = "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48";
    private static final String DAI_CONTRACT = "0x6B175474E89094C44Da98b954EedeAC495271d0F";
    private static final String UNKNOWN_TOKEN_CONTRACT = "0xdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef";

    private static final String FROM_PADDED =
            "0x0000000000000000000000001234567890abcdef1234567890abcdef12345678";
    private static final String TO_PADDED =
            "0x000000000000000000000000abcdef1234567890abcdef1234567890abcdef12";
    private static final String FROM_ADDRESS = "0x1234567890abcdef1234567890abcdef12345678";
    private static final String TO_ADDRESS = "0xabcdef1234567890abcdef1234567890abcdef12";

    // 1000000 in hex = 0xF4240, padded to 32 bytes
    private static final String USDC_AMOUNT_HEX =
            "0x00000000000000000000000000000000000000000000000000000000000f4240";
    private static final String USDC_RAW_AMOUNT = "1000000";

    private static final String BLOCK_NUMBER_HEX = "0x129ab00";
    private static final String BLOCK_HASH = "0xblockhash1234567890abcdef1234567890abcdef1234567890abcdef12345678";
    private static final String BLOCK_TIMESTAMP_HEX = "0x65b3e8c0";
    private static final String TX_HASH = "0xabc123def456789012345678901234567890abcdef1234567890abcdef123456";

    private static final EvmTokenConfig USDC_CONFIG =
            new EvmTokenConfig(USDC_CONTRACT, "USDC", 6);
    private static final EvmTokenConfig DAI_CONFIG =
            new EvmTokenConfig(DAI_CONTRACT, "DAI", 18);

    private EvmErc20TransferParser parser;

    @BeforeEach
    void setUp() {
        parser = new EvmErc20TransferParser(ETHEREUM, List.of(USDC_CONFIG, DAI_CONFIG));
    }

    @Nested
    @DisplayName("USDC transfer parsing")
    class UsdcTransferParsing {

        @Test
        @DisplayName("parses USDC Transfer event log correctly")
        void parsesUsdcTransferEventLog() {
            // given
            var log = anErc20TransferLog(USDC_CONTRACT, FROM_PADDED, TO_PADDED, USDC_AMOUNT_HEX);
            var receipt = aSuccessfulReceipt(List.of(log));
            var block = aBlock();

            var expected = Transfer.builder()
                    .txHash(TX_HASH)
                    .fromAddress(FROM_ADDRESS)
                    .toAddress(TO_ADDRESS)
                    .rawAmount(USDC_RAW_AMOUNT)
                    .amount(new BigDecimal("1.000000"))
                    .decimals(6)
                    .tokenSymbol("USDC")
                    .tokenContractAddress(USDC_CONTRACT)
                    .blockNumber(HexUtils.hexToLong(BLOCK_NUMBER_HEX))
                    .blockHash(BLOCK_HASH)
                    .transactionIndex(0)
                    .logIndex(0)
                    .chainId(ETHEREUM)
                    .timestamp(HexUtils.hexToInstant(BLOCK_TIMESTAMP_HEX))
                    .nativeTransfer(false)
                    .build();

            // when
            var result = parser.parseErc20Transfers(List.of(receipt), block);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.getFirst())
                    .usingRecursiveComparison()
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("DAI transfer parsing")
    class DaiTransferParsing {

        @Test
        @DisplayName("parses DAI Transfer event with 18 decimals correctly")
        void parsesDaiTransferWithEighteenDecimals() {
            // given
            // 1 DAI = 10^18 = 0xDE0B6B3A7640000
            var daiAmountHex = "0x0000000000000000000000000000000000000000000000000de0b6b3a7640000";
            var daiRawAmount = new BigInteger("de0b6b3a7640000", 16).toString();
            var log = anErc20TransferLog(DAI_CONTRACT, FROM_PADDED, TO_PADDED, daiAmountHex);
            var receipt = aSuccessfulReceipt(List.of(log));
            var block = aBlock();

            var expected = Transfer.builder()
                    .txHash(TX_HASH)
                    .fromAddress(FROM_ADDRESS)
                    .toAddress(TO_ADDRESS)
                    .rawAmount(daiRawAmount)
                    .amount(new BigDecimal(new BigInteger("de0b6b3a7640000", 16))
                            .divide(BigDecimal.TEN.pow(18), MathContext.DECIMAL128))
                    .decimals(18)
                    .tokenSymbol("DAI")
                    .tokenContractAddress(DAI_CONTRACT)
                    .blockNumber(HexUtils.hexToLong(BLOCK_NUMBER_HEX))
                    .blockHash(BLOCK_HASH)
                    .transactionIndex(0)
                    .logIndex(0)
                    .chainId(ETHEREUM)
                    .timestamp(HexUtils.hexToInstant(BLOCK_TIMESTAMP_HEX))
                    .nativeTransfer(false)
                    .build();

            // when
            var result = parser.parseErc20Transfers(List.of(receipt), block);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.getFirst())
                    .usingRecursiveComparison()
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("whitelist filtering")
    class WhitelistFiltering {

        @Test
        @DisplayName("skips unknown token not in whitelist")
        void skipsUnknownToken() {
            // given
            var log = anErc20TransferLog(UNKNOWN_TOKEN_CONTRACT, FROM_PADDED, TO_PADDED, USDC_AMOUNT_HEX);
            var receipt = aSuccessfulReceipt(List.of(log));
            var block = aBlock();

            // when
            var result = parser.parseErc20Transfers(List.of(receipt), block);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns only whitelisted tokens from mixed logs")
        void returnsOnlyWhitelistedTokens() {
            // given
            var usdcLog = anErc20TransferLog(USDC_CONTRACT, FROM_PADDED, TO_PADDED, USDC_AMOUNT_HEX);
            var unknownLog = anErc20TransferLog(UNKNOWN_TOKEN_CONTRACT, FROM_PADDED, TO_PADDED, USDC_AMOUNT_HEX);
            var daiLog = anErc20TransferLog(DAI_CONTRACT, FROM_PADDED, TO_PADDED, USDC_AMOUNT_HEX);
            var receipt = aSuccessfulReceipt(List.of(usdcLog, unknownLog, daiLog));
            var block = aBlock();

            // when
            var result = parser.parseErc20Transfers(List.of(receipt), block);

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).tokenSymbol()).isEqualTo("USDC");
            assertThat(result.get(1).tokenSymbol()).isEqualTo("DAI");
        }

        @Test
        @DisplayName("matches contract address case-insensitively")
        void matchesContractAddressCaseInsensitively() {
            // given
            var lowercaseContract = USDC_CONTRACT.toLowerCase();
            var log = anErc20TransferLog(lowercaseContract, FROM_PADDED, TO_PADDED, USDC_AMOUNT_HEX);
            var receipt = aSuccessfulReceipt(List.of(log));
            var block = aBlock();

            // when
            var result = parser.parseErc20Transfers(List.of(receipt), block);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().tokenSymbol()).isEqualTo("USDC");
        }
    }

    @Nested
    @DisplayName("non-Transfer event filtering")
    class NonTransferEventFiltering {

        @Test
        @DisplayName("skips log with different topic0 signature")
        void skipsNonTransferEvent() {
            // given
            var approvalSignature = "0x8c5be1e5ebec7d5bd14f71427d1e84f3dd0314c0f7b2291e5b200ac8c7c3b925";
            var log = EvmLog.builder()
                    .address(USDC_CONTRACT)
                    .topics(List.of(approvalSignature, FROM_PADDED, TO_PADDED))
                    .data(USDC_AMOUNT_HEX)
                    .logIndex("0x0")
                    .transactionIndex("0x0")
                    .transactionHash(TX_HASH)
                    .blockNumber(BLOCK_NUMBER_HEX)
                    .blockHash(BLOCK_HASH)
                    .build();
            var receipt = aSuccessfulReceipt(List.of(log));
            var block = aBlock();

            // when
            var result = parser.parseErc20Transfers(List.of(receipt), block);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("skips log with insufficient topics")
        void skipsLogWithInsufficientTopics() {
            // given
            var log = EvmLog.builder()
                    .address(USDC_CONTRACT)
                    .topics(List.of(TRANSFER_EVENT_SIGNATURE, FROM_PADDED))
                    .data(USDC_AMOUNT_HEX)
                    .logIndex("0x0")
                    .transactionIndex("0x0")
                    .transactionHash(TX_HASH)
                    .blockNumber(BLOCK_NUMBER_HEX)
                    .blockHash(BLOCK_HASH)
                    .build();
            var receipt = aSuccessfulReceipt(List.of(log));
            var block = aBlock();

            // when
            var result = parser.parseErc20Transfers(List.of(receipt), block);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("skips log with null topics")
        void skipsLogWithNullTopics() {
            // given
            var log = EvmLog.builder()
                    .address(USDC_CONTRACT)
                    .topics(null)
                    .data(USDC_AMOUNT_HEX)
                    .logIndex("0x0")
                    .transactionIndex("0x0")
                    .transactionHash(TX_HASH)
                    .blockNumber(BLOCK_NUMBER_HEX)
                    .blockHash(BLOCK_HASH)
                    .build();
            var receipt = aSuccessfulReceipt(List.of(log));
            var block = aBlock();

            // when
            var result = parser.parseErc20Transfers(List.of(receipt), block);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("failed transaction handling")
    class FailedTransactionHandling {

        @Test
        @DisplayName("skips receipts from failed transactions")
        void skipsFailedTransactions() {
            // given
            var log = anErc20TransferLog(USDC_CONTRACT, FROM_PADDED, TO_PADDED, USDC_AMOUNT_HEX);
            var failedReceipt = EvmReceipt.builder()
                    .transactionHash(TX_HASH)
                    .transactionIndex("0x0")
                    .blockNumber(BLOCK_NUMBER_HEX)
                    .blockHash(BLOCK_HASH)
                    .from(FROM_ADDRESS)
                    .to(USDC_CONTRACT)
                    .status("0x0")
                    .logs(List.of(log))
                    .build();
            var block = aBlock();

            // when
            var result = parser.parseErc20Transfers(List.of(failedReceipt), block);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("empty input handling")
    class EmptyInputHandling {

        @Test
        @DisplayName("returns empty list for empty receipts")
        void returnsEmptyForEmptyReceipts() {
            // given
            var block = aBlock();

            // when
            var result = parser.parseErc20Transfers(List.of(), block);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty list when receipt has no logs")
        void returnsEmptyForReceiptWithNoLogs() {
            // given
            var receipt = EvmReceipt.builder()
                    .transactionHash(TX_HASH)
                    .transactionIndex("0x0")
                    .blockNumber(BLOCK_NUMBER_HEX)
                    .blockHash(BLOCK_HASH)
                    .from(FROM_ADDRESS)
                    .to(USDC_CONTRACT)
                    .status("0x1")
                    .logs(List.of())
                    .build();
            var block = aBlock();

            // when
            var result = parser.parseErc20Transfers(List.of(receipt), block);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("amount parsing edge cases")
    class AmountParsingEdgeCases {

        @Test
        @DisplayName("handles zero amount correctly")
        void handlesZeroAmount() {
            // given
            var log = anErc20TransferLog(USDC_CONTRACT, FROM_PADDED, TO_PADDED, "0x0");
            var receipt = aSuccessfulReceipt(List.of(log));
            var block = aBlock();

            // when
            var result = parser.parseErc20Transfers(List.of(receipt), block);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().rawAmount()).isEqualTo("0");
            assertThat(result.getFirst().amount()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("handles empty data (0x) as zero amount")
        void handlesEmptyDataAsZero() {
            // given
            var log = anErc20TransferLog(USDC_CONTRACT, FROM_PADDED, TO_PADDED, "0x");
            var receipt = aSuccessfulReceipt(List.of(log));
            var block = aBlock();

            // when
            var result = parser.parseErc20Transfers(List.of(receipt), block);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().rawAmount()).isEqualTo("0");
        }

        @Test
        @DisplayName("handles null data as zero amount")
        void handlesNullDataAsZero() {
            // given
            var log = anErc20TransferLog(USDC_CONTRACT, FROM_PADDED, TO_PADDED, null);
            var receipt = aSuccessfulReceipt(List.of(log));
            var block = aBlock();

            // when
            var result = parser.parseErc20Transfers(List.of(receipt), block);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().rawAmount()).isEqualTo("0");
        }
    }

    @Nested
    @DisplayName("address extraction")
    class AddressExtraction {

        @Test
        @DisplayName("extracts from and to addresses from padded topics")
        void extractsAddressesFromPaddedTopics() {
            // given
            var log = anErc20TransferLog(USDC_CONTRACT, FROM_PADDED, TO_PADDED, USDC_AMOUNT_HEX);
            var receipt = aSuccessfulReceipt(List.of(log));
            var block = aBlock();

            // when
            var result = parser.parseErc20Transfers(List.of(receipt), block);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().fromAddress()).isEqualTo(FROM_ADDRESS);
            assertThat(result.getFirst().toAddress()).isEqualTo(TO_ADDRESS);
        }
    }

    @Nested
    @DisplayName("multiple receipts")
    class MultipleReceipts {

        @Test
        @DisplayName("processes transfers from multiple receipts")
        void processesMultipleReceipts() {
            // given
            var log1 = anErc20TransferLog(USDC_CONTRACT, FROM_PADDED, TO_PADDED, USDC_AMOUNT_HEX);
            var receipt1 = aSuccessfulReceipt(List.of(log1));

            var log2 = EvmLog.builder()
                    .address(DAI_CONTRACT)
                    .topics(List.of(TRANSFER_EVENT_SIGNATURE, FROM_PADDED, TO_PADDED))
                    .data(USDC_AMOUNT_HEX)
                    .logIndex("0x1")
                    .transactionIndex("0x1")
                    .transactionHash("0xsecond_tx_hash")
                    .blockNumber(BLOCK_NUMBER_HEX)
                    .blockHash(BLOCK_HASH)
                    .build();
            var receipt2 = EvmReceipt.builder()
                    .transactionHash("0xsecond_tx_hash")
                    .transactionIndex("0x1")
                    .blockNumber(BLOCK_NUMBER_HEX)
                    .blockHash(BLOCK_HASH)
                    .from(FROM_ADDRESS)
                    .to(DAI_CONTRACT)
                    .status("0x1")
                    .logs(List.of(log2))
                    .build();
            var block = aBlock();

            // when
            var result = parser.parseErc20Transfers(List.of(receipt1, receipt2), block);

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).tokenSymbol()).isEqualTo("USDC");
            assertThat(result.get(1).tokenSymbol()).isEqualTo("DAI");
        }
    }

    @Nested
    @DisplayName("chain ID assignment")
    class ChainIdAssignment {

        @Test
        @DisplayName("assigns configured chain ID to parsed transfers")
        void assignsConfiguredChainId() {
            // given
            var polygonParser = new EvmErc20TransferParser(ChainId.POLYGON, List.of(USDC_CONFIG));
            var log = anErc20TransferLog(USDC_CONTRACT, FROM_PADDED, TO_PADDED, USDC_AMOUNT_HEX);
            var receipt = aSuccessfulReceipt(List.of(log));
            var block = aBlock();

            // when
            var result = polygonParser.parseErc20Transfers(List.of(receipt), block);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().chainId()).isEqualTo(ChainId.POLYGON);
        }
    }

    @Nested
    @DisplayName("nativeTransfer flag")
    class NativeTransferFlag {

        @Test
        @DisplayName("sets nativeTransfer to false for ERC-20 transfers")
        void setsNativeTransferToFalse() {
            // given
            var log = anErc20TransferLog(USDC_CONTRACT, FROM_PADDED, TO_PADDED, USDC_AMOUNT_HEX);
            var receipt = aSuccessfulReceipt(List.of(log));
            var block = aBlock();

            // when
            var result = parser.parseErc20Transfers(List.of(receipt), block);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().nativeTransfer()).isFalse();
        }
    }

    // -- ACL DTO factory methods (package-private types cannot be in testFixtures) --

    private static EvmLog anErc20TransferLog(String contractAddress, String fromPadded,
                                              String toPadded, String dataHex) {
        return EvmLog.builder()
                .address(contractAddress)
                .topics(List.of(TRANSFER_EVENT_SIGNATURE, fromPadded, toPadded))
                .data(dataHex)
                .logIndex("0x0")
                .transactionIndex("0x0")
                .transactionHash(TX_HASH)
                .blockNumber(BLOCK_NUMBER_HEX)
                .blockHash(BLOCK_HASH)
                .build();
    }

    private static EvmReceipt aSuccessfulReceipt(List<EvmLog> logs) {
        return EvmReceipt.builder()
                .transactionHash(TX_HASH)
                .transactionIndex("0x0")
                .blockNumber(BLOCK_NUMBER_HEX)
                .blockHash(BLOCK_HASH)
                .from(FROM_ADDRESS)
                .to(USDC_CONTRACT)
                .status("0x1")
                .logs(logs)
                .build();
    }

    private static EvmBlock aBlock() {
        return EvmBlock.builder()
                .number(BLOCK_NUMBER_HEX)
                .hash(BLOCK_HASH)
                .parentHash("0xparenthash234567890abcdef1234567890abcdef1234567890abcdef12345678")
                .timestamp(BLOCK_TIMESTAMP_HEX)
                .transactions(List.of())
                .build();
    }
}
