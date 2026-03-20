package com.stablebridge.indexer.infrastructure.chain.solana;

import com.stablebridge.indexer.domain.model.Transfer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static com.stablebridge.indexer.domain.model.ChainId.SOLANA_CHAIN;
import static com.stablebridge.indexer.infrastructure.chain.solana.SolanaNativeTransferParser.SYSTEM_PROGRAM_ID;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SolanaNativeTransferParser")
class SolanaNativeTransferParserTest {

    private static final long SLOT = 166_974_441L;
    private static final String BLOCK_HASH = "3Eq21vXNB5s86c62bVuUfTeaMif1N2kUqRPBmGRJhyTA";
    private static final long BLOCK_TIME = 1_700_000_000L;
    private static final Instant BLOCK_TIMESTAMP = Instant.ofEpochSecond(BLOCK_TIME);
    private static final String SENDER = "9aE476sH92Vz7DMPyq5WLPkrKWivxeuTKEFKd2sZZcde";
    private static final String RECEIVER = "HN7cABqLq46Es1jh92dQQisAq662SmxELLLsHHe4YWrH";
    private static final String TX_SIGNATURE = "5VERv8NMvzbJMEkV8xnrLkEaWRtSz9CosKDYjCJjBRnbJLgp8uirBgmQpjKhoR4tjF3ZpRzrFmBV6UjKdiSZkQUW";
    private static final int SOL_DECIMALS = 9;

    private final SolanaNativeTransferParser parser = new SolanaNativeTransferParser(SOLANA_CHAIN);

    @Nested
    @DisplayName("parseNativeTransfers")
    class ParseNativeTransfers {

        @Test
        @DisplayName("parses single native SOL transfer with correct amount conversion")
        void parsesSingleNativeTransfer() {
            // given
            var block = aBlockWith(List.of(
                    aSystemTransferTransaction(SENDER, RECEIVER, TX_SIGNATURE,
                            5_000L, 2_000_000_000L, 1_000_000_000L)));

            // when
            var result = parser.parseNativeTransfers(block, SLOT);

            // then
            var expected = Transfer.builder()
                    .txHash(TX_SIGNATURE)
                    .fromAddress(SENDER)
                    .toAddress(RECEIVER)
                    .rawAmount("1000000000")
                    .amount(new BigDecimal("1.000000000"))
                    .decimals(SOL_DECIMALS)
                    .tokenSymbol("SOL")
                    .tokenContractAddress(null)
                    .blockNumber(SLOT)
                    .blockHash(BLOCK_HASH)
                    .transactionIndex(0)
                    .logIndex(-1)
                    .chainId(SOLANA_CHAIN)
                    .timestamp(BLOCK_TIMESTAMP)
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
            var sender2 = "BPFLoaderUpgradeab1e11111111111111111111111";
            var receiver2 = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA";
            var sig2 = "2nBhEBYYvfaAe16UMNqRHre4YNSskvuYgx3M6E4JP1iRgBj7rU3DLkV2xKHJWvNWh2JgDQjS4bnPC9RZ3aSMKY9g";

            var block = aBlockWith(List.of(
                    aSystemTransferTransaction(SENDER, RECEIVER, TX_SIGNATURE,
                            5_000L, 2_000_000_000L, 1_000_000_000L),
                    aSystemTransferTransaction(sender2, receiver2, sig2,
                            5_000L, 500_000_000L, 500_000_000L)));

            // when
            var result = parser.parseNativeTransfers(block, SLOT);

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).rawAmount()).isEqualTo("1000000000");
            assertThat(result.get(1).rawAmount()).isEqualTo("500000000");
        }

        @Test
        @DisplayName("skips failed transactions")
        void skipsFailedTransactions() {
            // given
            var failedTx = aFailedSystemTransferTransaction(SENDER, RECEIVER, TX_SIGNATURE);
            var block = aBlockWith(List.of(failedTx));

            // when
            var result = parser.parseNativeTransfers(block, SLOT);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty list for empty block")
        void returnsEmptyForEmptyBlock() {
            // given
            var block = aBlockWith(List.of());

            // when
            var result = parser.parseNativeTransfers(block, SLOT);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty list for block with null transactions")
        void returnsEmptyForNullTransactions() {
            // given
            var block = SolanaBlock.builder()
                    .parentSlot(SLOT - 1)
                    .blockhash(BLOCK_HASH)
                    .previousBlockhash("prevhash")
                    .blockTime(BLOCK_TIME)
                    .transactions(null)
                    .build();

            // when
            var result = parser.parseNativeTransfers(block, SLOT);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("skips non-SystemProgram instructions")
        void skipsNonSystemProgramInstructions() {
            // given
            var tokenProgramInstruction = SolanaInstruction.builder()
                    .programId("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA")
                    .accounts(List.of(SENDER, RECEIVER))
                    .data("3Bxs4ThwQbE4vyj5")
                    .build();

            var tx = SolanaTransaction.builder()
                    .transaction(SolanaTransactionBody.builder()
                            .message(SolanaTransactionMessage.builder()
                                    .accountKeys(List.of(SENDER, RECEIVER,
                                            "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"))
                                    .instructions(List.of(tokenProgramInstruction))
                                    .build())
                            .signatures(List.of(TX_SIGNATURE))
                            .build())
                    .meta(SolanaTransactionMeta.builder()
                            .err(null)
                            .fee(5_000L)
                            .preBalances(List.of(2_000_000_000L, 0L, 0L))
                            .postBalances(List.of(1_000_000_000L, 1_000_000_000L, 0L))
                            .preTokenBalances(List.of())
                            .postTokenBalances(List.of())
                            .build())
                    .build();

            var block = aBlockWith(List.of(tx));

            // when
            var result = parser.parseNativeTransfers(block, SLOT);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("handles small lamport amounts with correct precision")
        void handlesSmallLamportAmounts() {
            // given — 1 lamport transfer
            var block = aBlockWith(List.of(
                    aSystemTransferTransaction(SENDER, RECEIVER, TX_SIGNATURE,
                            5_000L, 1_000_000_000L, 1L)));

            // when
            var result = parser.parseNativeTransfers(block, SLOT);

            // then
            var expected = Transfer.builder()
                    .txHash(TX_SIGNATURE)
                    .fromAddress(SENDER)
                    .toAddress(RECEIVER)
                    .rawAmount("1")
                    .amount(new BigDecimal("0.000000001"))
                    .decimals(SOL_DECIMALS)
                    .tokenSymbol("SOL")
                    .tokenContractAddress(null)
                    .blockNumber(SLOT)
                    .blockHash(BLOCK_HASH)
                    .transactionIndex(0)
                    .logIndex(-1)
                    .chainId(SOLANA_CHAIN)
                    .timestamp(BLOCK_TIMESTAMP)
                    .nativeTransfer(true)
                    .build();

            assertThat(result).hasSize(1);
            assertThat(result.getFirst())
                    .usingRecursiveComparison()
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("handles large SOL transfer amount correctly")
        void handlesLargeTransferAmount() {
            // given — 1000 SOL = 1_000_000_000_000 lamports
            var lamports = 1_000_000_000_000L;
            var block = aBlockWith(List.of(
                    aSystemTransferTransaction(SENDER, RECEIVER, TX_SIGNATURE,
                            5_000L, 2_000_000_000_000L, lamports)));

            // when
            var result = parser.parseNativeTransfers(block, SLOT);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().rawAmount()).isEqualTo("1000000000000");
            assertThat(result.getFirst().amount()).isEqualByComparingTo(new BigDecimal("1000"));
        }

        @Test
        @DisplayName("sets correct transaction index for each transaction in block")
        void setsCorrectTransactionIndex() {
            // given
            var sig2 = "2nBhEBYYvfaAe16UMNqRHre4YNSskvuYgx3M6E4JP1iRgBj7rU3DLkV2xKHJWvNWh2JgDQjS4bnPC9RZ3aSMKY9g";
            var block = aBlockWith(List.of(
                    aSystemTransferTransaction(SENDER, RECEIVER, TX_SIGNATURE,
                            5_000L, 2_000_000_000L, 1_000_000_000L),
                    aSystemTransferTransaction(SENDER, RECEIVER, sig2,
                            5_000L, 2_000_000_000L, 500_000_000L)));

            // when
            var result = parser.parseNativeTransfers(block, SLOT);

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).transactionIndex()).isZero();
            assertThat(result.get(1).transactionIndex()).isEqualTo(1);
        }

        @Test
        @DisplayName("skips instructions with insufficient accounts")
        void skipsInstructionsWithInsufficientAccounts() {
            // given — instruction with only 1 account (needs at least 2 for transfer)
            var instruction = SolanaInstruction.builder()
                    .programId(SYSTEM_PROGRAM_ID)
                    .accounts(List.of(SENDER))
                    .data("3Bxs4ThwQbE4vyj5")
                    .build();

            var tx = SolanaTransaction.builder()
                    .transaction(SolanaTransactionBody.builder()
                            .message(SolanaTransactionMessage.builder()
                                    .accountKeys(List.of(SENDER, SYSTEM_PROGRAM_ID))
                                    .instructions(List.of(instruction))
                                    .build())
                            .signatures(List.of(TX_SIGNATURE))
                            .build())
                    .meta(SolanaTransactionMeta.builder()
                            .err(null)
                            .fee(5_000L)
                            .preBalances(List.of(2_000_000_000L, 0L))
                            .postBalances(List.of(1_999_995_000L, 0L))
                            .preTokenBalances(List.of())
                            .postTokenBalances(List.of())
                            .build())
                    .build();

            var block = aBlockWith(List.of(tx));

            // when
            var result = parser.parseNativeTransfers(block, SLOT);

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
            var block = aBlockWith(List.of(
                    aSystemTransferTransaction(SENDER, RECEIVER, TX_SIGNATURE,
                            5_000L, 2_000_000_000L, 1_000_000_000L)));

            // when
            var result = parser.parseNativeTransfers(block, SLOT);

            // then
            assertThat(result.getFirst().nativeTransfer()).isTrue();
        }

        @Test
        @DisplayName("sets tokenContractAddress to null for native transfers")
        void setsTokenContractAddressToNull() {
            // given
            var block = aBlockWith(List.of(
                    aSystemTransferTransaction(SENDER, RECEIVER, TX_SIGNATURE,
                            5_000L, 2_000_000_000L, 1_000_000_000L)));

            // when
            var result = parser.parseNativeTransfers(block, SLOT);

            // then
            assertThat(result.getFirst().tokenContractAddress()).isNull();
        }

        @Test
        @DisplayName("sets logIndex to -1 for native transfers")
        void setsLogIndexToNegativeOne() {
            // given
            var block = aBlockWith(List.of(
                    aSystemTransferTransaction(SENDER, RECEIVER, TX_SIGNATURE,
                            5_000L, 2_000_000_000L, 1_000_000_000L)));

            // when
            var result = parser.parseNativeTransfers(block, SLOT);

            // then
            assertThat(result.getFirst().logIndex()).isEqualTo(-1);
        }

        @Test
        @DisplayName("uses SOLANA_CHAIN as chainId")
        void usesSolanaChainId() {
            // given
            var block = aBlockWith(List.of(
                    aSystemTransferTransaction(SENDER, RECEIVER, TX_SIGNATURE,
                            5_000L, 2_000_000_000L, 1_000_000_000L)));

            // when
            var result = parser.parseNativeTransfers(block, SLOT);

            // then
            assertThat(result.getFirst().chainId()).isEqualTo(SOLANA_CHAIN);
        }

        @Test
        @DisplayName("sets decimals to 9 for SOL")
        void setsDecimalsToNine() {
            // given
            var block = aBlockWith(List.of(
                    aSystemTransferTransaction(SENDER, RECEIVER, TX_SIGNATURE,
                            5_000L, 2_000_000_000L, 1_000_000_000L)));

            // when
            var result = parser.parseNativeTransfers(block, SLOT);

            // then
            assertThat(result.getFirst().decimals()).isEqualTo(9);
        }

        @Test
        @DisplayName("sets tokenSymbol to SOL")
        void setsTokenSymbolToSol() {
            // given
            var block = aBlockWith(List.of(
                    aSystemTransferTransaction(SENDER, RECEIVER, TX_SIGNATURE,
                            5_000L, 2_000_000_000L, 1_000_000_000L)));

            // when
            var result = parser.parseNativeTransfers(block, SLOT);

            // then
            assertThat(result.getFirst().tokenSymbol()).isEqualTo("SOL");
        }
    }

    // -- Test helpers (package-private ACL DTOs cannot go to testFixtures) --

    private SolanaBlock aBlockWith(List<SolanaTransaction> transactions) {
        return SolanaBlock.builder()
                .parentSlot(SLOT - 1)
                .blockhash(BLOCK_HASH)
                .previousBlockhash("mfcyqEXB3DnHXki6KjjmZck6YjmZLvpAByy2fj4nh6B")
                .blockTime(BLOCK_TIME)
                .transactions(transactions)
                .build();
    }

    private SolanaTransaction aSystemTransferTransaction(String from, String to,
                                                         String signature, long fee,
                                                         long senderPreBalance,
                                                         long transferLamports) {
        var instruction = SolanaInstruction.builder()
                .programId(SYSTEM_PROGRAM_ID)
                .accounts(List.of(from, to))
                .data("3Bxs4ThwQbE4vyj5")
                .build();

        var senderPostBalance = senderPreBalance - transferLamports - fee;
        var receiverPreBalance = 0L;
        var receiverPostBalance = transferLamports;

        return SolanaTransaction.builder()
                .transaction(SolanaTransactionBody.builder()
                        .message(SolanaTransactionMessage.builder()
                                .accountKeys(List.of(from, to, SYSTEM_PROGRAM_ID))
                                .instructions(List.of(instruction))
                                .build())
                        .signatures(List.of(signature))
                        .build())
                .meta(SolanaTransactionMeta.builder()
                        .err(null)
                        .fee(fee)
                        .preBalances(List.of(senderPreBalance, receiverPreBalance, 0L))
                        .postBalances(List.of(senderPostBalance, receiverPostBalance, 0L))
                        .preTokenBalances(List.of())
                        .postTokenBalances(List.of())
                        .build())
                .build();
    }

    private SolanaTransaction aFailedSystemTransferTransaction(String from, String to,
                                                               String signature) {
        var instruction = SolanaInstruction.builder()
                .programId(SYSTEM_PROGRAM_ID)
                .accounts(List.of(from, to))
                .data("3Bxs4ThwQbE4vyj5")
                .build();

        return SolanaTransaction.builder()
                .transaction(SolanaTransactionBody.builder()
                        .message(SolanaTransactionMessage.builder()
                                .accountKeys(List.of(from, to, SYSTEM_PROGRAM_ID))
                                .instructions(List.of(instruction))
                                .build())
                        .signatures(List.of(signature))
                        .build())
                .meta(SolanaTransactionMeta.builder()
                        .err("InstructionError")
                        .fee(5_000L)
                        .preBalances(List.of(2_000_000_000L, 0L, 0L))
                        .postBalances(List.of(1_999_995_000L, 0L, 0L))
                        .preTokenBalances(List.of())
                        .postTokenBalances(List.of())
                        .build())
                .build();
    }
}
