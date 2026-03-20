package com.stablebridge.indexer.infrastructure.chain.evm;

import com.stablebridge.indexer.domain.model.Transfer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static com.stablebridge.indexer.domain.model.ChainId.BSC;
import static com.stablebridge.indexer.domain.model.ChainId.ETHEREUM;
import static com.stablebridge.indexer.domain.model.ChainId.POLYGON;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EvmNativeTransferParser")
class EvmNativeTransferParserTest {

    private static final int ETH_DECIMALS = 18;
    private static final String BLOCK_HASH = "0xblockhash123";
    private static final String BLOCK_TIMESTAMP_HEX = "0x65b3e8c0";
    private static final String BLOCK_NUMBER_HEX = "0x1298f10";
    private static final long BLOCK_NUMBER = 19_500_816L;

    private final EvmNativeTransferParser parser = new EvmNativeTransferParser(ETHEREUM, ETH_DECIMALS);

    @Nested
    @DisplayName("parseNativeTransfers")
    class ParseNativeTransfers {

        @Test
        @DisplayName("parses single native ETH transfer with correct amount conversion")
        void parsesSingleNativeTransfer() {
            // given
            var tx = EvmTransaction.builder()
                    .hash("0xtx_hash_1")
                    .from("0xsender_1")
                    .to("0xreceiver_1")
                    .value("0xde0b6b3a7640000") // 1 ETH in wei
                    .input("0x")
                    .blockNumber(BLOCK_NUMBER_HEX)
                    .transactionIndex("0x5")
                    .blockHash(BLOCK_HASH)
                    .build();

            var block = EvmBlock.builder()
                    .number(BLOCK_NUMBER_HEX)
                    .hash(BLOCK_HASH)
                    .parentHash("0xparent_hash")
                    .timestamp(BLOCK_TIMESTAMP_HEX)
                    .transactions(List.of(tx))
                    .build();

            // when
            var result = parser.parseNativeTransfers(block);

            // then
            var expected = Transfer.builder()
                    .txHash("0xtx_hash_1")
                    .fromAddress("0xsender_1")
                    .toAddress("0xreceiver_1")
                    .rawAmount("1000000000000000000")
                    .amount(new BigDecimal("1.000000000000000000"))
                    .decimals(ETH_DECIMALS)
                    .tokenSymbol("ETH")
                    .tokenContractAddress(null)
                    .blockNumber(BLOCK_NUMBER)
                    .blockHash(BLOCK_HASH)
                    .transactionIndex(5)
                    .logIndex(-1)
                    .chainId(ETHEREUM)
                    .timestamp(HexUtils.hexToInstant(BLOCK_TIMESTAMP_HEX))
                    .nativeTransfer(true)
                    .build();

            assertThat(result).hasSize(1);
            assertThat(result.getFirst())
                    .usingRecursiveComparison()
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("parses multiple native transfers from a single block")
        void parsesMultipleNativeTransfers() {
            // given
            var tx1 = EvmTransaction.builder()
                    .hash("0xtx_1")
                    .from("0xsender_1")
                    .to("0xreceiver_1")
                    .value("0xde0b6b3a7640000") // 1 ETH
                    .input("0x")
                    .blockNumber(BLOCK_NUMBER_HEX)
                    .transactionIndex("0x0")
                    .blockHash(BLOCK_HASH)
                    .build();

            var tx2 = EvmTransaction.builder()
                    .hash("0xtx_2")
                    .from("0xsender_2")
                    .to("0xreceiver_2")
                    .value("0x1bc16d674ec80000") // 2 ETH
                    .input("0x")
                    .blockNumber(BLOCK_NUMBER_HEX)
                    .transactionIndex("0x1")
                    .blockHash(BLOCK_HASH)
                    .build();

            var block = EvmBlock.builder()
                    .number(BLOCK_NUMBER_HEX)
                    .hash(BLOCK_HASH)
                    .parentHash("0xparent_hash")
                    .timestamp(BLOCK_TIMESTAMP_HEX)
                    .transactions(List.of(tx1, tx2))
                    .build();

            // when
            var result = parser.parseNativeTransfers(block);

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).rawAmount()).isEqualTo("1000000000000000000");
            assertThat(result.get(1).rawAmount()).isEqualTo("2000000000000000000");
        }

        @Test
        @DisplayName("skips zero-value transactions")
        void skipsZeroValueTransactions() {
            // given
            var zeroTx = EvmTransaction.builder()
                    .hash("0xtx_zero")
                    .from("0xsender")
                    .to("0xreceiver")
                    .value("0x0")
                    .input("0xa9059cbb")
                    .blockNumber(BLOCK_NUMBER_HEX)
                    .transactionIndex("0x0")
                    .blockHash(BLOCK_HASH)
                    .build();

            var nonZeroTx = EvmTransaction.builder()
                    .hash("0xtx_nonzero")
                    .from("0xsender")
                    .to("0xreceiver")
                    .value("0xde0b6b3a7640000")
                    .input("0x")
                    .blockNumber(BLOCK_NUMBER_HEX)
                    .transactionIndex("0x1")
                    .blockHash(BLOCK_HASH)
                    .build();

            var block = EvmBlock.builder()
                    .number(BLOCK_NUMBER_HEX)
                    .hash(BLOCK_HASH)
                    .parentHash("0xparent_hash")
                    .timestamp(BLOCK_TIMESTAMP_HEX)
                    .transactions(List.of(zeroTx, nonZeroTx))
                    .build();

            // when
            var result = parser.parseNativeTransfers(block);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().txHash()).isEqualTo("0xtx_nonzero");
        }

        @Test
        @DisplayName("skips transactions with null to address (contract creation)")
        void skipsContractCreationTransactions() {
            // given
            var contractCreationTx = EvmTransaction.builder()
                    .hash("0xtx_create")
                    .from("0xsender")
                    .to(null)
                    .value("0xde0b6b3a7640000")
                    .input("0x6060604052")
                    .blockNumber(BLOCK_NUMBER_HEX)
                    .transactionIndex("0x0")
                    .blockHash(BLOCK_HASH)
                    .build();

            var block = EvmBlock.builder()
                    .number(BLOCK_NUMBER_HEX)
                    .hash(BLOCK_HASH)
                    .parentHash("0xparent_hash")
                    .timestamp(BLOCK_TIMESTAMP_HEX)
                    .transactions(List.of(contractCreationTx))
                    .build();

            // when
            var result = parser.parseNativeTransfers(block);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("skips transactions with null value")
        void skipsNullValueTransactions() {
            // given
            var nullValueTx = EvmTransaction.builder()
                    .hash("0xtx_null")
                    .from("0xsender")
                    .to("0xreceiver")
                    .value(null)
                    .input("0xa9059cbb")
                    .blockNumber(BLOCK_NUMBER_HEX)
                    .transactionIndex("0x0")
                    .blockHash(BLOCK_HASH)
                    .build();

            var block = EvmBlock.builder()
                    .number(BLOCK_NUMBER_HEX)
                    .hash(BLOCK_HASH)
                    .parentHash("0xparent_hash")
                    .timestamp(BLOCK_TIMESTAMP_HEX)
                    .transactions(List.of(nullValueTx))
                    .build();

            // when
            var result = parser.parseNativeTransfers(block);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("skips transactions with 0x00 value")
        void skipsZeroHexPaddedValueTransactions() {
            // given
            var zeroHexTx = EvmTransaction.builder()
                    .hash("0xtx_zero_padded")
                    .from("0xsender")
                    .to("0xreceiver")
                    .value("0x00")
                    .input("0x")
                    .blockNumber(BLOCK_NUMBER_HEX)
                    .transactionIndex("0x0")
                    .blockHash(BLOCK_HASH)
                    .build();

            var block = EvmBlock.builder()
                    .number(BLOCK_NUMBER_HEX)
                    .hash(BLOCK_HASH)
                    .parentHash("0xparent_hash")
                    .timestamp(BLOCK_TIMESTAMP_HEX)
                    .transactions(List.of(zeroHexTx))
                    .build();

            // when
            var result = parser.parseNativeTransfers(block);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty list for empty block")
        void returnsEmptyForEmptyBlock() {
            // given
            var block = EvmBlock.builder()
                    .number(BLOCK_NUMBER_HEX)
                    .hash(BLOCK_HASH)
                    .parentHash("0xparent_hash")
                    .timestamp(BLOCK_TIMESTAMP_HEX)
                    .transactions(List.of())
                    .build();

            // when
            var result = parser.parseNativeTransfers(block);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty list for block with null transactions")
        void returnsEmptyForNullTransactions() {
            // given
            var block = EvmBlock.builder()
                    .number(BLOCK_NUMBER_HEX)
                    .hash(BLOCK_HASH)
                    .parentHash("0xparent_hash")
                    .timestamp(BLOCK_TIMESTAMP_HEX)
                    .transactions(null)
                    .build();

            // when
            var result = parser.parseNativeTransfers(block);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("handles small wei amounts with correct precision")
        void handlesSmallWeiAmounts() {
            // given
            var tx = EvmTransaction.builder()
                    .hash("0xtx_small")
                    .from("0xsender")
                    .to("0xreceiver")
                    .value("0x1") // 1 wei
                    .input("0x")
                    .blockNumber(BLOCK_NUMBER_HEX)
                    .transactionIndex("0x0")
                    .blockHash(BLOCK_HASH)
                    .build();

            var block = EvmBlock.builder()
                    .number(BLOCK_NUMBER_HEX)
                    .hash(BLOCK_HASH)
                    .parentHash("0xparent_hash")
                    .timestamp(BLOCK_TIMESTAMP_HEX)
                    .transactions(List.of(tx))
                    .build();

            // when
            var result = parser.parseNativeTransfers(block);

            // then
            var expected = Transfer.builder()
                    .txHash("0xtx_small")
                    .fromAddress("0xsender")
                    .toAddress("0xreceiver")
                    .rawAmount("1")
                    .amount(new BigDecimal("0.000000000000000001"))
                    .decimals(ETH_DECIMALS)
                    .tokenSymbol("ETH")
                    .tokenContractAddress(null)
                    .blockNumber(BLOCK_NUMBER)
                    .blockHash(BLOCK_HASH)
                    .transactionIndex(0)
                    .logIndex(-1)
                    .chainId(ETHEREUM)
                    .timestamp(HexUtils.hexToInstant(BLOCK_TIMESTAMP_HEX))
                    .nativeTransfer(true)
                    .build();

            assertThat(result).hasSize(1);
            assertThat(result.getFirst())
                    .usingRecursiveComparison()
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("handles large transfer amount correctly")
        void handlesLargeTransferAmount() {
            // given — 1000 ETH = 0x3635c9adc5dea00000
            var tx = EvmTransaction.builder()
                    .hash("0xtx_large")
                    .from("0xsender")
                    .to("0xreceiver")
                    .value("0x3635c9adc5dea00000")
                    .input("0x")
                    .blockNumber(BLOCK_NUMBER_HEX)
                    .transactionIndex("0x0")
                    .blockHash(BLOCK_HASH)
                    .build();

            var block = EvmBlock.builder()
                    .number(BLOCK_NUMBER_HEX)
                    .hash(BLOCK_HASH)
                    .parentHash("0xparent_hash")
                    .timestamp(BLOCK_TIMESTAMP_HEX)
                    .transactions(List.of(tx))
                    .build();

            // when
            var result = parser.parseNativeTransfers(block);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().rawAmount()).isEqualTo("1000000000000000000000");
            assertThat(result.getFirst().amount()).isEqualByComparingTo(new BigDecimal("1000"));
        }
    }

    @Nested
    @DisplayName("native symbol resolution")
    class NativeSymbolResolution {

        @Test
        @DisplayName("uses MATIC symbol for Polygon chain")
        void usesMaticForPolygon() {
            // given
            var polygonParser = new EvmNativeTransferParser(POLYGON, ETH_DECIMALS);
            var tx = EvmTransaction.builder()
                    .hash("0xtx_1")
                    .from("0xsender")
                    .to("0xreceiver")
                    .value("0xde0b6b3a7640000")
                    .input("0x")
                    .blockNumber(BLOCK_NUMBER_HEX)
                    .transactionIndex("0x0")
                    .blockHash(BLOCK_HASH)
                    .build();

            var block = EvmBlock.builder()
                    .number(BLOCK_NUMBER_HEX)
                    .hash(BLOCK_HASH)
                    .parentHash("0xparent_hash")
                    .timestamp(BLOCK_TIMESTAMP_HEX)
                    .transactions(List.of(tx))
                    .build();

            // when
            var result = polygonParser.parseNativeTransfers(block);

            // then
            assertThat(result.getFirst().tokenSymbol()).isEqualTo("MATIC");
        }

        @Test
        @DisplayName("uses BNB symbol for BSC chain")
        void usesBnbForBsc() {
            // given
            var bscParser = new EvmNativeTransferParser(BSC, ETH_DECIMALS);
            var tx = EvmTransaction.builder()
                    .hash("0xtx_1")
                    .from("0xsender")
                    .to("0xreceiver")
                    .value("0xde0b6b3a7640000")
                    .input("0x")
                    .blockNumber(BLOCK_NUMBER_HEX)
                    .transactionIndex("0x0")
                    .blockHash(BLOCK_HASH)
                    .build();

            var block = EvmBlock.builder()
                    .number(BLOCK_NUMBER_HEX)
                    .hash(BLOCK_HASH)
                    .parentHash("0xparent_hash")
                    .timestamp(BLOCK_TIMESTAMP_HEX)
                    .transactions(List.of(tx))
                    .build();

            // when
            var result = bscParser.parseNativeTransfers(block);

            // then
            assertThat(result.getFirst().tokenSymbol()).isEqualTo("BNB");
        }

        @Test
        @DisplayName("sets correct chainId on transfer")
        void setsCorrectChainId() {
            // given
            var polygonParser = new EvmNativeTransferParser(POLYGON, ETH_DECIMALS);
            var tx = EvmTransaction.builder()
                    .hash("0xtx_1")
                    .from("0xsender")
                    .to("0xreceiver")
                    .value("0xde0b6b3a7640000")
                    .input("0x")
                    .blockNumber(BLOCK_NUMBER_HEX)
                    .transactionIndex("0x0")
                    .blockHash(BLOCK_HASH)
                    .build();

            var block = EvmBlock.builder()
                    .number(BLOCK_NUMBER_HEX)
                    .hash(BLOCK_HASH)
                    .parentHash("0xparent_hash")
                    .timestamp(BLOCK_TIMESTAMP_HEX)
                    .transactions(List.of(tx))
                    .build();

            // when
            var result = polygonParser.parseNativeTransfers(block);

            // then
            assertThat(result.getFirst().chainId()).isEqualTo(POLYGON);
        }
    }

    @Nested
    @DisplayName("transfer properties")
    class TransferProperties {

        @Test
        @DisplayName("sets nativeTransfer flag to true")
        void setsNativeTransferFlag() {
            // given
            var tx = EvmTransaction.builder()
                    .hash("0xtx_1")
                    .from("0xsender")
                    .to("0xreceiver")
                    .value("0xde0b6b3a7640000")
                    .input("0x")
                    .blockNumber(BLOCK_NUMBER_HEX)
                    .transactionIndex("0x0")
                    .blockHash(BLOCK_HASH)
                    .build();

            var block = EvmBlock.builder()
                    .number(BLOCK_NUMBER_HEX)
                    .hash(BLOCK_HASH)
                    .parentHash("0xparent_hash")
                    .timestamp(BLOCK_TIMESTAMP_HEX)
                    .transactions(List.of(tx))
                    .build();

            // when
            var result = parser.parseNativeTransfers(block);

            // then
            assertThat(result.getFirst().nativeTransfer()).isTrue();
        }

        @Test
        @DisplayName("sets tokenContractAddress to null for native transfers")
        void setsTokenContractAddressToNull() {
            // given
            var tx = EvmTransaction.builder()
                    .hash("0xtx_1")
                    .from("0xsender")
                    .to("0xreceiver")
                    .value("0xde0b6b3a7640000")
                    .input("0x")
                    .blockNumber(BLOCK_NUMBER_HEX)
                    .transactionIndex("0x0")
                    .blockHash(BLOCK_HASH)
                    .build();

            var block = EvmBlock.builder()
                    .number(BLOCK_NUMBER_HEX)
                    .hash(BLOCK_HASH)
                    .parentHash("0xparent_hash")
                    .timestamp(BLOCK_TIMESTAMP_HEX)
                    .transactions(List.of(tx))
                    .build();

            // when
            var result = parser.parseNativeTransfers(block);

            // then
            assertThat(result.getFirst().tokenContractAddress()).isNull();
        }

        @Test
        @DisplayName("sets logIndex to -1 for native transfers")
        void setsLogIndexToNegativeOne() {
            // given
            var tx = EvmTransaction.builder()
                    .hash("0xtx_1")
                    .from("0xsender")
                    .to("0xreceiver")
                    .value("0xde0b6b3a7640000")
                    .input("0x")
                    .blockNumber(BLOCK_NUMBER_HEX)
                    .transactionIndex("0x0")
                    .blockHash(BLOCK_HASH)
                    .build();

            var block = EvmBlock.builder()
                    .number(BLOCK_NUMBER_HEX)
                    .hash(BLOCK_HASH)
                    .parentHash("0xparent_hash")
                    .timestamp(BLOCK_TIMESTAMP_HEX)
                    .transactions(List.of(tx))
                    .build();

            // when
            var result = parser.parseNativeTransfers(block);

            // then
            assertThat(result.getFirst().logIndex()).isEqualTo(-1);
        }
    }
}
