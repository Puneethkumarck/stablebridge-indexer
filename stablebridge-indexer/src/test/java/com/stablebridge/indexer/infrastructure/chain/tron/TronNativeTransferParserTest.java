package com.stablebridge.indexer.infrastructure.chain.tron;

import com.stablebridge.indexer.domain.model.Transfer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static com.stablebridge.indexer.domain.model.ChainId.TRON_CHAIN;
import static com.stablebridge.indexer.infrastructure.chain.tron.TronFixtures.SOME_AMOUNT_SUN;
import static com.stablebridge.indexer.infrastructure.chain.tron.TronFixtures.SOME_BLOCK_ID;
import static com.stablebridge.indexer.infrastructure.chain.tron.TronFixtures.SOME_BLOCK_NUMBER;
import static com.stablebridge.indexer.infrastructure.chain.tron.TronFixtures.SOME_BLOCK_TIMESTAMP;
import static com.stablebridge.indexer.infrastructure.chain.tron.TronFixtures.SOME_FROM_BASE58;
import static com.stablebridge.indexer.infrastructure.chain.tron.TronFixtures.SOME_FROM_HEX;
import static com.stablebridge.indexer.infrastructure.chain.tron.TronFixtures.SOME_TO_BASE58;
import static com.stablebridge.indexer.infrastructure.chain.tron.TronFixtures.SOME_TO_HEX;
import static com.stablebridge.indexer.infrastructure.chain.tron.TronFixtures.SOME_TX_HASH;
import static com.stablebridge.indexer.infrastructure.chain.tron.TronFixtures.aNonTransferContractTransaction;
import static com.stablebridge.indexer.infrastructure.chain.tron.TronFixtures.aTronBlock;
import static com.stablebridge.indexer.infrastructure.chain.tron.TronFixtures.aTronTransaction;
import static com.stablebridge.indexer.infrastructure.chain.tron.TronFixtures.aTronTransferContractTransaction;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TronNativeTransferParser")
class TronNativeTransferParserTest {

    private static final int TRX_DECIMALS = 6;

    private final TronNativeTransferParser parser = new TronNativeTransferParser(TRON_CHAIN);

    @Nested
    @DisplayName("parseNativeTransfers")
    class ParseNativeTransfers {

        @Test
        @DisplayName("parses single native TRX transfer with correct amount conversion")
        void parsesSingleNativeTransfer() {
            // given
            var block = aTronBlock()
                    .transactions(List.of(aTronTransferContractTransaction().build()))
                    .build();

            // when
            var result = parser.parseNativeTransfers(block);

            // then
            var expected = Transfer.builder()
                    .txHash(SOME_TX_HASH)
                    .fromAddress(SOME_FROM_HEX)
                    .toAddress(SOME_TO_HEX)
                    .rawAmount(String.valueOf(SOME_AMOUNT_SUN))
                    .amount(new BigDecimal("1.000000"))
                    .decimals(TRX_DECIMALS)
                    .tokenSymbol("TRX")
                    .tokenContractAddress(null)
                    .blockNumber(SOME_BLOCK_NUMBER)
                    .blockHash(SOME_BLOCK_ID)
                    .transactionIndex(0)
                    .logIndex(-1)
                    .chainId(TRON_CHAIN)
                    .timestamp(SOME_BLOCK_TIMESTAMP)
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
            var tx1 = aTronTransaction(SOME_FROM_BASE58, SOME_TO_BASE58, 1_000_000L)
                    .txID("0xtx_1")
                    .build();
            var tx2 = aTronTransaction(SOME_FROM_BASE58, SOME_TO_BASE58, 2_500_000L)
                    .txID("0xtx_2")
                    .build();
            var block = aTronBlock()
                    .transactions(List.of(tx1, tx2))
                    .build();

            // when
            var result = parser.parseNativeTransfers(block);

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).txHash()).isEqualTo("0xtx_1");
            assertThat(result.get(0).rawAmount()).isEqualTo("1000000");
            assertThat(result.get(0).transactionIndex()).isEqualTo(0);
            assertThat(result.get(1).txHash()).isEqualTo("0xtx_2");
            assertThat(result.get(1).rawAmount()).isEqualTo("2500000");
            assertThat(result.get(1).transactionIndex()).isEqualTo(1);
        }

        @Test
        @DisplayName("skips non-TransferContract transactions")
        void skipsNonTransferContractTransactions() {
            // given
            var triggerSmartContract = aNonTransferContractTransaction()
                    .txID("0xtx_trigger")
                    .build();
            var nativeTransfer = aTronTransferContractTransaction()
                    .txID("0xtx_native")
                    .build();
            var block = aTronBlock()
                    .transactions(List.of(triggerSmartContract, nativeTransfer))
                    .build();

            // when
            var result = parser.parseNativeTransfers(block);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().txHash()).isEqualTo("0xtx_native");
        }

        @Test
        @DisplayName("skips transactions with null owner address")
        void skipsTransactionsWithNullOwnerAddress() {
            // given
            var tx = aTronTransaction(null, SOME_TO_BASE58, SOME_AMOUNT_SUN).build();
            var block = aTronBlock()
                    .transactions(List.of(tx))
                    .build();

            // when
            var result = parser.parseNativeTransfers(block);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("skips transactions with null to address")
        void skipsTransactionsWithNullToAddress() {
            // given
            var tx = aTronTransaction(SOME_FROM_BASE58, null, SOME_AMOUNT_SUN).build();
            var block = aTronBlock()
                    .transactions(List.of(tx))
                    .build();

            // when
            var result = parser.parseNativeTransfers(block);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("skips transactions with zero amount")
        void skipsZeroAmountTransactions() {
            // given
            var zeroTx = aTronTransaction(SOME_FROM_BASE58, SOME_TO_BASE58, 0L)
                    .txID("0xtx_zero")
                    .build();
            var nonZeroTx = aTronTransferContractTransaction()
                    .txID("0xtx_nonzero")
                    .build();
            var block = aTronBlock()
                    .transactions(List.of(zeroTx, nonZeroTx))
                    .build();

            // when
            var result = parser.parseNativeTransfers(block);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().txHash()).isEqualTo("0xtx_nonzero");
        }

        @Test
        @DisplayName("returns empty list for empty transactions")
        void returnsEmptyForEmptyTransactions() {
            // given
            var block = aTronBlock()
                    .transactions(List.of())
                    .build();

            // when
            var result = parser.parseNativeTransfers(block);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty list for null transactions")
        void returnsEmptyForNullTransactions() {
            // given
            var block = aTronBlock()
                    .transactions(null)
                    .build();

            // when
            var result = parser.parseNativeTransfers(block);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("transfer properties")
    class TransferProperties {

        @Test
        @DisplayName("sets nativeTransfer flag to true")
        void setsNativeTransferFlag() {
            // given
            var block = aTronBlock().build();

            // when
            var result = parser.parseNativeTransfers(block);

            // then
            assertThat(result.getFirst().nativeTransfer()).isTrue();
        }

        @Test
        @DisplayName("sets tokenContractAddress to null for native transfers")
        void setsTokenContractAddressToNull() {
            // given
            var block = aTronBlock().build();

            // when
            var result = parser.parseNativeTransfers(block);

            // then
            assertThat(result.getFirst().tokenContractAddress()).isNull();
        }

        @Test
        @DisplayName("sets logIndex to -1 for native transfers")
        void setsLogIndexToNegativeOne() {
            // given
            var block = aTronBlock().build();

            // when
            var result = parser.parseNativeTransfers(block);

            // then
            assertThat(result.getFirst().logIndex()).isEqualTo(-1);
        }

        @Test
        @DisplayName("sets chainId from constructor")
        void setsChainIdFromConstructor() {
            // given
            var block = aTronBlock().build();

            // when
            var result = parser.parseNativeTransfers(block);

            // then
            assertThat(result.getFirst().chainId()).isEqualTo(TRON_CHAIN);
        }
    }
}
