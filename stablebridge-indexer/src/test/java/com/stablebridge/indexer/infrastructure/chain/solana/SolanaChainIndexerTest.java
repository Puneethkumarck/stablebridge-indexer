package com.stablebridge.indexer.infrastructure.chain.solana;

import com.stablebridge.indexer.domain.model.BlockResult;
import com.stablebridge.indexer.domain.model.IndexedBlock;
import com.stablebridge.indexer.domain.model.Transfer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static com.stablebridge.indexer.domain.model.ChainId.SOLANA_CHAIN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("SolanaChainIndexer")
class SolanaChainIndexerTest {

    private static final long SLOT = 250_000_000L;
    private static final String BLOCK_HASH = "5eykt4UsFv8P8njmGtA2Eoo96Cr3X9NR8EFwLjv8LHGB";
    private static final String PARENT_HASH = "4uQeVj5tqViQh7yWWGStvkEG1Zmhx6uasJtWCJziofM";
    private static final long BLOCK_TIME = 1_700_000_000L;
    private static final Instant BLOCK_TIMESTAMP = Instant.ofEpochSecond(BLOCK_TIME);
    private static final String TX_SIGNATURE = "5VERv8NMhFjCtR1n3kzB5Vq1qHhT9XPNkm5n3k1AxYbFc2K";
    private static final String FROM_ADDRESS = "9WzDXwBbmkg8ZTbNMqUxvQRAyrZzDsGYdLVL9zYtAWWM";
    private static final String TO_ADDRESS = "7nYqdvPsaBfkE3M3b7GQqG5LxDSdGkRBkrJQvDN3FPU6";

    @Mock
    private SolanaRpcClient rpcClient;

    @Mock
    private SolanaNativeTransferParser nativeTransferParser;

    @Mock
    private SolanaSpITransferParser splTransferParser;

    @Nested
    @DisplayName("indexBlock")
    class IndexBlock {

        @Test
        @DisplayName("parses SPL and native transfers, combines them into BlockResult")
        void parsesSplAndNativeTransfersCombinesThemIntoBlockResult() {
            // given
            var indexer = createIndexer(true);
            var block = aBlockWithTransactions();
            var splTransfer = aSplTransfer();
            var nativeTransfer = aNativeTransfer();

            given(rpcClient.getBlock(SLOT)).willReturn(block);
            given(splTransferParser.parseSplTransfers(block, SLOT)).willReturn(List.of(splTransfer));
            given(nativeTransferParser.parseNativeTransfers(block, SLOT)).willReturn(List.of(nativeTransfer));

            var expectedBlock = IndexedBlock.builder()
                    .blockNumber(SLOT)
                    .blockHash(BLOCK_HASH)
                    .parentHash(PARENT_HASH)
                    .timestamp(BLOCK_TIMESTAMP)
                    .chainId(SOLANA_CHAIN)
                    .transactionCount(1)
                    .build();
            var expected = BlockResult.builder()
                    .indexedBlock(expectedBlock)
                    .transfers(List.of(splTransfer, nativeTransfer))
                    .build();

            // when
            var result = indexer.indexBlock(SLOT);

            // then
            assertThat(result)
                    .usingRecursiveComparison()
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("skips native transfers when native indexing is disabled")
        void skipsNativeTransfersWhenNativeIndexingIsDisabled() {
            // given
            var indexer = createIndexer(false);
            var block = aBlockWithTransactions();
            var splTransfer = aSplTransfer();

            given(rpcClient.getBlock(SLOT)).willReturn(block);
            given(splTransferParser.parseSplTransfers(block, SLOT)).willReturn(List.of(splTransfer));

            var expectedBlock = IndexedBlock.builder()
                    .blockNumber(SLOT)
                    .blockHash(BLOCK_HASH)
                    .parentHash(PARENT_HASH)
                    .timestamp(BLOCK_TIMESTAMP)
                    .chainId(SOLANA_CHAIN)
                    .transactionCount(1)
                    .build();
            var expected = BlockResult.builder()
                    .indexedBlock(expectedBlock)
                    .transfers(List.of(splTransfer))
                    .build();

            // when
            var result = indexer.indexBlock(SLOT);

            // then
            assertThat(result)
                    .usingRecursiveComparison()
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .isEqualTo(expected);
            then(nativeTransferParser).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("handles empty block with no transactions")
        void handlesEmptyBlockWithNoTransactions() {
            // given
            var indexer = createIndexer(true);
            var block = anEmptyBlock();

            given(rpcClient.getBlock(SLOT)).willReturn(block);
            given(splTransferParser.parseSplTransfers(block, SLOT)).willReturn(List.of());
            given(nativeTransferParser.parseNativeTransfers(block, SLOT)).willReturn(List.of());

            var expectedBlock = IndexedBlock.builder()
                    .blockNumber(SLOT)
                    .blockHash(BLOCK_HASH)
                    .parentHash(PARENT_HASH)
                    .timestamp(BLOCK_TIMESTAMP)
                    .chainId(SOLANA_CHAIN)
                    .transactionCount(0)
                    .build();
            var expected = BlockResult.builder()
                    .indexedBlock(expectedBlock)
                    .transfers(List.of())
                    .build();

            // when
            var result = indexer.indexBlock(SLOT);

            // then
            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("handles block with null transactions list")
        void handlesBlockWithNullTransactions() {
            // given
            var indexer = createIndexer(false);
            var block = aBlockWithNullTransactions();

            given(rpcClient.getBlock(SLOT)).willReturn(block);
            given(splTransferParser.parseSplTransfers(block, SLOT)).willReturn(List.of());

            var expectedBlock = IndexedBlock.builder()
                    .blockNumber(SLOT)
                    .blockHash(BLOCK_HASH)
                    .parentHash(PARENT_HASH)
                    .timestamp(BLOCK_TIMESTAMP)
                    .chainId(SOLANA_CHAIN)
                    .transactionCount(0)
                    .build();
            var expected = BlockResult.builder()
                    .indexedBlock(expectedBlock)
                    .transfers(List.of())
                    .build();

            // when
            var result = indexer.indexBlock(SLOT);

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
        @DisplayName("delegates to rpcClient getLatestSlot")
        void delegatesToRpcClientGetLatestSlot() {
            // given
            var indexer = createIndexer(false);
            given(rpcClient.getLatestSlot()).willReturn(SLOT);

            // when
            var result = indexer.getLatestFinalizedBlockNumber();

            // then
            assertThat(result).isEqualTo(SLOT);
        }
    }

    @Nested
    @DisplayName("getChainId")
    class GetChainId {

        @Test
        @DisplayName("returns SOLANA_CHAIN")
        void returnsSolanaChain() {
            // given
            var indexer = createIndexer(false);

            // when
            var result = indexer.getChainId();

            // then
            assertThat(result).isEqualTo(SOLANA_CHAIN);
        }
    }

    // -- Factory methods --

    private SolanaChainIndexer createIndexer(boolean indexNativeTransfers) {
        return new SolanaChainIndexer(
                rpcClient, SOLANA_CHAIN, indexNativeTransfers,
                nativeTransferParser, splTransferParser);
    }

    private static SolanaBlock aBlockWithTransactions() {
        var message = SolanaTransactionMessage.builder()
                .accountKeys(List.of(FROM_ADDRESS, TO_ADDRESS))
                .instructions(List.of())
                .build();
        var body = SolanaTransactionBody.builder()
                .message(message)
                .signatures(List.of(TX_SIGNATURE))
                .build();
        var meta = SolanaTransactionMeta.builder()
                .preBalances(List.of(1_000_000_000L, 0L))
                .postBalances(List.of(500_000_000L, 500_000_000L))
                .build();
        var transaction = SolanaTransaction.builder()
                .transaction(body)
                .meta(meta)
                .build();

        return SolanaBlock.builder()
                .blockhash(BLOCK_HASH)
                .previousBlockhash(PARENT_HASH)
                .blockTime(BLOCK_TIME)
                .parentSlot(SLOT - 1)
                .transactions(List.of(transaction))
                .build();
    }

    private static SolanaBlock anEmptyBlock() {
        return SolanaBlock.builder()
                .blockhash(BLOCK_HASH)
                .previousBlockhash(PARENT_HASH)
                .blockTime(BLOCK_TIME)
                .parentSlot(SLOT - 1)
                .transactions(List.of())
                .build();
    }

    private static SolanaBlock aBlockWithNullTransactions() {
        return SolanaBlock.builder()
                .blockhash(BLOCK_HASH)
                .previousBlockhash(PARENT_HASH)
                .blockTime(BLOCK_TIME)
                .parentSlot(SLOT - 1)
                .transactions(null)
                .build();
    }

    private static Transfer aSplTransfer() {
        return Transfer.builder()
                .txHash(TX_SIGNATURE)
                .fromAddress(FROM_ADDRESS)
                .toAddress(TO_ADDRESS)
                .rawAmount("1000000")
                .amount(new BigDecimal("1.000000"))
                .decimals(6)
                .tokenSymbol("USDC")
                .tokenContractAddress("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v")
                .blockNumber(SLOT)
                .blockHash(BLOCK_HASH)
                .transactionIndex(0)
                .logIndex(-1)
                .chainId(SOLANA_CHAIN)
                .timestamp(BLOCK_TIMESTAMP)
                .nativeTransfer(false)
                .build();
    }

    private static Transfer aNativeTransfer() {
        return Transfer.builder()
                .txHash(TX_SIGNATURE)
                .fromAddress(FROM_ADDRESS)
                .toAddress(TO_ADDRESS)
                .rawAmount("500000000")
                .amount(new BigDecimal("0.500000000"))
                .decimals(9)
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
    }
}
