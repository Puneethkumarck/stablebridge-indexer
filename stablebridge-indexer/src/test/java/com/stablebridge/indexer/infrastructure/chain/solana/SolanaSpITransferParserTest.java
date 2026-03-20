package com.stablebridge.indexer.infrastructure.chain.solana;

import com.stablebridge.indexer.domain.model.Transfer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.List;

import static com.stablebridge.indexer.domain.model.ChainId.SOLANA_CHAIN;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SolanaSpITransferParser")
class SolanaSpITransferParserTest {

    private static final String USDC_MINT = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v";
    private static final String USDT_MINT = "Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB";
    private static final String UNKNOWN_MINT = "UnknownMint111111111111111111111111111111111";

    private static final String SENDER_OWNER = "SenderWallet11111111111111111111111111111111";
    private static final String RECEIVER_OWNER = "ReceiverWallet1111111111111111111111111111111";

    private static final String TX_SIGNATURE = "5UfDuX7WXYZgutRvMqhVMeT4Jv5bMwXjHqNqPLfrJBKiJmCznZxyrbPfjPBnNG7yqBnFCNJMFkaWVPKDBFzibKeW";
    private static final String BLOCK_HASH = "GJk72iFhPvhqb4E2Y2Kfbk3GpTMrbp3JQi3AjqpNiKhE";
    private static final long BLOCK_TIME = 1710900000L;
    private static final long SLOT = 254_000_000L;

    private static final SolanaTokenConfig USDC_CONFIG =
            new SolanaTokenConfig(USDC_MINT, "USDC", 6);
    private static final SolanaTokenConfig USDT_CONFIG =
            new SolanaTokenConfig(USDT_MINT, "USDT", 6);

    private SolanaSpITransferParser parser;

    @BeforeEach
    void setUp() {
        parser = new SolanaSpITransferParser(SOLANA_CHAIN, List.of(USDC_CONFIG, USDT_CONFIG));
    }

    @Nested
    @DisplayName("single USDC-SPL transfer")
    class SingleUsdcSplTransfer {

        @Test
        @DisplayName("parses single USDC-SPL transfer with correct amount conversion")
        void parsesSingleUsdcSplTransfer() {
            // given
            var block = aBlockWithSingleTransfer(
                    USDC_MINT, SENDER_OWNER, "10000000", RECEIVER_OWNER, "0", "10000000");

            var expected = Transfer.builder()
                    .txHash(TX_SIGNATURE)
                    .fromAddress(SENDER_OWNER)
                    .toAddress(RECEIVER_OWNER)
                    .rawAmount("10000000")
                    .amount(new BigDecimal("10000000")
                            .divide(BigDecimal.TEN.pow(6), MathContext.DECIMAL128))
                    .decimals(6)
                    .tokenSymbol("USDC")
                    .tokenContractAddress(USDC_MINT)
                    .blockNumber(SLOT)
                    .blockHash(BLOCK_HASH)
                    .transactionIndex(0)
                    .logIndex(-1)
                    .chainId(SOLANA_CHAIN)
                    .timestamp(Instant.ofEpochSecond(BLOCK_TIME))
                    .nativeTransfer(false)
                    .build();

            // when
            var result = parser.parseSplTransfers(block, SLOT);

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
        @DisplayName("skips transfers for unknown mints not in whitelist")
        void skipsUnknownMints() {
            // given
            var block = aBlockWithSingleTransfer(
                    UNKNOWN_MINT, SENDER_OWNER, "5000000", RECEIVER_OWNER, "0", "5000000");

            // when
            var result = parser.parseSplTransfers(block, SLOT);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns only whitelisted mint transfers from mixed transaction")
        void returnsOnlyWhitelistedMints() {
            // given
            var preTokenBalances = List.of(
                    aTokenBalance(0, USDC_MINT, SENDER_OWNER, "10000000", 6),
                    aTokenBalance(1, USDC_MINT, RECEIVER_OWNER, "0", 6),
                    aTokenBalance(2, UNKNOWN_MINT, SENDER_OWNER, "99999", 8),
                    aTokenBalance(3, UNKNOWN_MINT, RECEIVER_OWNER, "0", 8));

            var postTokenBalances = List.of(
                    aTokenBalance(0, USDC_MINT, SENDER_OWNER, "0", 6),
                    aTokenBalance(1, USDC_MINT, RECEIVER_OWNER, "10000000", 6),
                    aTokenBalance(2, UNKNOWN_MINT, SENDER_OWNER, "0", 8),
                    aTokenBalance(3, UNKNOWN_MINT, RECEIVER_OWNER, "99999", 8));

            var block = aBlockWithTokenBalances(preTokenBalances, postTokenBalances);

            // when
            var result = parser.parseSplTransfers(block, SLOT);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().tokenSymbol()).isEqualTo("USDC");
        }
    }

    @Nested
    @DisplayName("case-insensitive mint matching")
    class CaseInsensitiveMintMatching {

        @Test
        @DisplayName("matches mint address case-insensitively")
        void matchesMintCaseInsensitively() {
            // given
            var lowerCaseMint = USDC_MINT.toLowerCase();
            var block = aBlockWithSingleTransfer(
                    lowerCaseMint, SENDER_OWNER, "5000000", RECEIVER_OWNER, "0", "5000000");

            // when
            var result = parser.parseSplTransfers(block, SLOT);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().tokenSymbol()).isEqualTo("USDC");
        }
    }

    @Nested
    @DisplayName("failed transaction handling")
    class FailedTransactionHandling {

        @Test
        @DisplayName("skips failed transactions")
        void skipsFailedTransactions() {
            // given
            var meta = SolanaTransactionMeta.builder()
                    .err("InsufficientFunds")
                    .fee(5000L)
                    .preBalances(List.of(100000L))
                    .postBalances(List.of(95000L))
                    .preTokenBalances(List.of(
                            aTokenBalance(0, USDC_MINT, SENDER_OWNER, "10000000", 6),
                            aTokenBalance(1, USDC_MINT, RECEIVER_OWNER, "0", 6)))
                    .postTokenBalances(List.of(
                            aTokenBalance(0, USDC_MINT, SENDER_OWNER, "0", 6),
                            aTokenBalance(1, USDC_MINT, RECEIVER_OWNER, "10000000", 6)))
                    .build();

            var transaction = SolanaTransaction.builder()
                    .transaction(aTransactionBody())
                    .meta(meta)
                    .build();

            var block = SolanaBlock.builder()
                    .parentSlot(SLOT - 1)
                    .blockhash(BLOCK_HASH)
                    .previousBlockhash("PreviousBlockHash1111111111111111111111111111")
                    .blockTime(BLOCK_TIME)
                    .transactions(List.of(transaction))
                    .build();

            // when
            var result = parser.parseSplTransfers(block, SLOT);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("empty block handling")
    class EmptyBlockHandling {

        @Test
        @DisplayName("returns empty list for block with no transactions")
        void returnsEmptyForEmptyBlock() {
            // given
            var block = SolanaBlock.builder()
                    .parentSlot(SLOT - 1)
                    .blockhash(BLOCK_HASH)
                    .previousBlockhash("PreviousBlockHash1111111111111111111111111111")
                    .blockTime(BLOCK_TIME)
                    .transactions(List.of())
                    .build();

            // when
            var result = parser.parseSplTransfers(block, SLOT);

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
                    .previousBlockhash("PreviousBlockHash1111111111111111111111111111")
                    .blockTime(BLOCK_TIME)
                    .transactions(null)
                    .build();

            // when
            var result = parser.parseSplTransfers(block, SLOT);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("multiple transfers in one transaction")
    class MultipleTransfersInOneTransaction {

        @Test
        @DisplayName("detects multiple recipients in one transaction")
        void detectsMultipleRecipients() {
            // given
            var secondReceiver = "SecondReceiver11111111111111111111111111111111";

            var preTokenBalances = List.of(
                    aTokenBalance(0, USDC_MINT, SENDER_OWNER, "20000000", 6),
                    aTokenBalance(1, USDC_MINT, RECEIVER_OWNER, "0", 6),
                    aTokenBalance(2, USDC_MINT, secondReceiver, "0", 6));

            var postTokenBalances = List.of(
                    aTokenBalance(0, USDC_MINT, SENDER_OWNER, "0", 6),
                    aTokenBalance(1, USDC_MINT, RECEIVER_OWNER, "10000000", 6),
                    aTokenBalance(2, USDC_MINT, secondReceiver, "10000000", 6));

            var block = aBlockWithTokenBalances(preTokenBalances, postTokenBalances);

            // when
            var result = parser.parseSplTransfers(block, SLOT);

            // then
            assertThat(result).hasSize(2);
        }
    }

    @Nested
    @DisplayName("nativeTransfer flag and chainId")
    class NativeTransferFlagAndChainId {

        @Test
        @DisplayName("sets nativeTransfer to false for SPL token transfers")
        void setsNativeTransferToFalse() {
            // given
            var block = aBlockWithSingleTransfer(
                    USDC_MINT, SENDER_OWNER, "10000000", RECEIVER_OWNER, "0", "10000000");

            // when
            var result = parser.parseSplTransfers(block, SLOT);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().nativeTransfer()).isFalse();
        }

        @Test
        @DisplayName("assigns SOLANA_CHAIN as chainId")
        void assignsSolanaChainId() {
            // given
            var block = aBlockWithSingleTransfer(
                    USDC_MINT, SENDER_OWNER, "10000000", RECEIVER_OWNER, "0", "10000000");

            // when
            var result = parser.parseSplTransfers(block, SLOT);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().chainId()).isEqualTo(SOLANA_CHAIN);
        }
    }

    @Nested
    @DisplayName("new receiver with no pre-balance entry")
    class NewReceiverWithNoPreBalance {

        @Test
        @DisplayName("handles receiver that has no pre-balance entry")
        void handlesNewReceiverWithNoPreBalance() {
            // given
            var preTokenBalances = List.of(
                    aTokenBalance(0, USDC_MINT, SENDER_OWNER, "10000000", 6));

            var postTokenBalances = List.of(
                    aTokenBalance(0, USDC_MINT, SENDER_OWNER, "0", 6),
                    aTokenBalance(1, USDC_MINT, RECEIVER_OWNER, "10000000", 6));

            var block = aBlockWithTokenBalances(preTokenBalances, postTokenBalances);

            var expected = Transfer.builder()
                    .txHash(TX_SIGNATURE)
                    .fromAddress(SENDER_OWNER)
                    .toAddress(RECEIVER_OWNER)
                    .rawAmount("10000000")
                    .amount(new BigDecimal("10000000")
                            .divide(BigDecimal.TEN.pow(6), MathContext.DECIMAL128))
                    .decimals(6)
                    .tokenSymbol("USDC")
                    .tokenContractAddress(USDC_MINT)
                    .blockNumber(SLOT)
                    .blockHash(BLOCK_HASH)
                    .transactionIndex(0)
                    .logIndex(-1)
                    .chainId(SOLANA_CHAIN)
                    .timestamp(Instant.ofEpochSecond(BLOCK_TIME))
                    .nativeTransfer(false)
                    .build();

            // when
            var result = parser.parseSplTransfers(block, SLOT);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.getFirst())
                    .usingRecursiveComparison()
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("transaction with no token balance changes")
    class NoTokenBalanceChanges {

        @Test
        @DisplayName("returns empty when balances do not change")
        void returnsEmptyWhenBalancesUnchanged() {
            // given
            var preTokenBalances = List.of(
                    aTokenBalance(0, USDC_MINT, SENDER_OWNER, "10000000", 6));

            var postTokenBalances = List.of(
                    aTokenBalance(0, USDC_MINT, SENDER_OWNER, "10000000", 6));

            var block = aBlockWithTokenBalances(preTokenBalances, postTokenBalances);

            // when
            var result = parser.parseSplTransfers(block, SLOT);

            // then
            assertThat(result).isEmpty();
        }
    }

    // -- ACL DTO factory methods (package-private types cannot be in testFixtures) --

    private static SolanaBlock aBlockWithSingleTransfer(String mint,
                                                        String senderOwner,
                                                        String senderPreAmount,
                                                        String receiverOwner,
                                                        String receiverPreAmount,
                                                        String receiverPostAmount) {
        var preTokenBalances = List.of(
                aTokenBalance(0, mint, senderOwner, senderPreAmount, 6),
                aTokenBalance(1, mint, receiverOwner, receiverPreAmount, 6));

        var senderPostAmount = String.valueOf(
                Long.parseLong(senderPreAmount) - Long.parseLong(receiverPostAmount)
                        + Long.parseLong(receiverPreAmount));

        var postTokenBalances = List.of(
                aTokenBalance(0, mint, senderOwner, senderPostAmount, 6),
                aTokenBalance(1, mint, receiverOwner, receiverPostAmount, 6));

        return aBlockWithTokenBalances(preTokenBalances, postTokenBalances);
    }

    private static SolanaBlock aBlockWithTokenBalances(List<SolanaTokenBalance> preTokenBalances,
                                                       List<SolanaTokenBalance> postTokenBalances) {
        var meta = SolanaTransactionMeta.builder()
                .err(null)
                .fee(5000L)
                .preBalances(List.of(100000L))
                .postBalances(List.of(95000L))
                .preTokenBalances(preTokenBalances)
                .postTokenBalances(postTokenBalances)
                .build();

        var transaction = SolanaTransaction.builder()
                .transaction(aTransactionBody())
                .meta(meta)
                .build();

        return SolanaBlock.builder()
                .parentSlot(SLOT - 1)
                .blockhash(BLOCK_HASH)
                .previousBlockhash("PreviousBlockHash1111111111111111111111111111")
                .blockTime(BLOCK_TIME)
                .transactions(List.of(transaction))
                .build();
    }

    private static SolanaTransactionBody aTransactionBody() {
        return SolanaTransactionBody.builder()
                .signatures(List.of(TX_SIGNATURE))
                .message(SolanaTransactionMessage.builder()
                        .accountKeys(List.of(SENDER_OWNER, RECEIVER_OWNER))
                        .instructions(List.of())
                        .build())
                .build();
    }

    private static SolanaTokenBalance aTokenBalance(int accountIndex,
                                                    String mint,
                                                    String owner,
                                                    String amount,
                                                    int decimals) {
        return SolanaTokenBalance.builder()
                .accountIndex(accountIndex)
                .mint(mint)
                .owner(owner)
                .uiTokenAmount(SolanaTokenBalance.SolanaTokenAmount.builder()
                        .amount(amount)
                        .decimals(decimals)
                        .uiAmountString(amount)
                        .build())
                .build();
    }
}
