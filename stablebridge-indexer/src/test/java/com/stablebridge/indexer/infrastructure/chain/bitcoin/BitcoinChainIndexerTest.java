package com.stablebridge.indexer.infrastructure.chain.bitcoin;

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

import static com.stablebridge.indexer.domain.model.ChainId.BITCOIN_CHAIN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("BitcoinChainIndexer")
class BitcoinChainIndexerTest {

    private static final long BLOCK_HEIGHT = 840_000L;
    private static final String BLOCK_HASH =
            "0000000000000000000320283a032748cef8227873ff4872689bf23f1cda83a5";
    private static final String PARENT_HASH =
            "0000000000000000000111111a032748cef8227873ff4872689bf23f1cda83a5";
    private static final long BLOCK_TIME = 1_713_571_767L;
    private static final Instant BLOCK_TIMESTAMP = Instant.ofEpochSecond(BLOCK_TIME);
    private static final int CONFIRMATIONS = 100;
    private static final int MIN_CONFIRMATIONS = 6;

    private static final String TX_HASH = "tx_transfer_001";
    private static final String FROM_ADDRESS = "1SenderAddressXyz";
    private static final String TO_ADDRESS = "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa";

    @Mock
    private BitcoinRpcClient rpcClient;

    @Mock
    private BitcoinTransferParser transferParser;

    @Mock
    private BitcoinConfirmationTracker confirmationTracker;

    @Nested
    @DisplayName("indexBlock")
    class IndexBlock {

        @Test
        @DisplayName("fetches block hash, then block, parses transfers and builds BlockResult")
        void fetchesBlockHashThenBlockAndParsesTransfers() {
            // given
            var indexer = createIndexer();
            var block = aBlockWithTransactions();
            var transfer = aBtcTransfer();

            given(rpcClient.getBlockHash(BLOCK_HEIGHT)).willReturn(BLOCK_HASH);
            given(rpcClient.getBlock(BLOCK_HASH)).willReturn(block);
            given(transferParser.parseTransfers(block)).willReturn(List.of(transfer));

            var expectedBlock = IndexedBlock.builder()
                    .blockNumber(BLOCK_HEIGHT)
                    .blockHash(BLOCK_HASH)
                    .parentHash(PARENT_HASH)
                    .timestamp(BLOCK_TIMESTAMP)
                    .chainId(BITCOIN_CHAIN)
                    .transactionCount(2)
                    .build();
            var expected = BlockResult.builder()
                    .indexedBlock(expectedBlock)
                    .transfers(List.of(transfer))
                    .build();

            // when
            var result = indexer.indexBlock(BLOCK_HEIGHT);

            // then
            assertThat(result)
                    .usingRecursiveComparison()
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("returns empty transfers for block with no transactions")
        void returnsEmptyTransfersForEmptyBlock() {
            // given
            var indexer = createIndexer();
            var block = anEmptyBlock();

            given(rpcClient.getBlockHash(BLOCK_HEIGHT)).willReturn(BLOCK_HASH);
            given(rpcClient.getBlock(BLOCK_HASH)).willReturn(block);
            given(transferParser.parseTransfers(block)).willReturn(List.of());

            var expectedBlock = IndexedBlock.builder()
                    .blockNumber(BLOCK_HEIGHT)
                    .blockHash(BLOCK_HASH)
                    .parentHash(PARENT_HASH)
                    .timestamp(BLOCK_TIMESTAMP)
                    .chainId(BITCOIN_CHAIN)
                    .transactionCount(0)
                    .build();
            var expected = BlockResult.builder()
                    .indexedBlock(expectedBlock)
                    .transfers(List.of())
                    .build();

            // when
            var result = indexer.indexBlock(BLOCK_HEIGHT);

            // then
            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("handles block with null transactions list")
        void handlesBlockWithNullTransactions() {
            // given
            var indexer = createIndexer();
            var block = aBlockWithNullTransactions();

            given(rpcClient.getBlockHash(BLOCK_HEIGHT)).willReturn(BLOCK_HASH);
            given(rpcClient.getBlock(BLOCK_HASH)).willReturn(block);
            given(transferParser.parseTransfers(block)).willReturn(List.of());

            var expectedBlock = IndexedBlock.builder()
                    .blockNumber(BLOCK_HEIGHT)
                    .blockHash(BLOCK_HASH)
                    .parentHash(PARENT_HASH)
                    .timestamp(BLOCK_TIMESTAMP)
                    .chainId(BITCOIN_CHAIN)
                    .transactionCount(0)
                    .build();
            var expected = BlockResult.builder()
                    .indexedBlock(expectedBlock)
                    .transfers(List.of())
                    .build();

            // when
            var result = indexer.indexBlock(BLOCK_HEIGHT);

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
        @DisplayName("delegates to confirmationTracker")
        void delegatesToConfirmationTracker() {
            // given
            var indexer = createIndexer();
            var confirmedBlock = BLOCK_HEIGHT - MIN_CONFIRMATIONS;
            given(confirmationTracker.getLatestConfirmedBlockNumber()).willReturn(confirmedBlock);

            // when
            var result = indexer.getLatestFinalizedBlockNumber();

            // then
            assertThat(result).isEqualTo(confirmedBlock);
        }
    }

    @Nested
    @DisplayName("getChainId")
    class GetChainId {

        @Test
        @DisplayName("returns BITCOIN_CHAIN")
        void returnsBitcoinChain() {
            // given
            var indexer = createIndexer();

            // when
            var result = indexer.getChainId();

            // then
            assertThat(result).isEqualTo(BITCOIN_CHAIN);
        }
    }

    // -- Factory methods --

    private BitcoinChainIndexer createIndexer() {
        return new BitcoinChainIndexer(rpcClient, BITCOIN_CHAIN, transferParser, confirmationTracker);
    }

    // -- ACL DTO factory methods (package-private types cannot be in testFixtures) --

    private static BtcBlock aBlockWithTransactions() {
        return BtcBlock.builder()
                .hash(BLOCK_HASH)
                .previousblockhash(PARENT_HASH)
                .height(BLOCK_HEIGHT)
                .time(BLOCK_TIME)
                .confirmations(CONFIRMATIONS)
                .tx(List.of(
                        BtcTransaction.builder().txid("tx_001").vin(List.of()).vout(List.of()).build(),
                        BtcTransaction.builder().txid("tx_002").vin(List.of()).vout(List.of()).build()))
                .build();
    }

    private static BtcBlock anEmptyBlock() {
        return BtcBlock.builder()
                .hash(BLOCK_HASH)
                .previousblockhash(PARENT_HASH)
                .height(BLOCK_HEIGHT)
                .time(BLOCK_TIME)
                .confirmations(CONFIRMATIONS)
                .tx(List.of())
                .build();
    }

    private static BtcBlock aBlockWithNullTransactions() {
        return BtcBlock.builder()
                .hash(BLOCK_HASH)
                .previousblockhash(PARENT_HASH)
                .height(BLOCK_HEIGHT)
                .time(BLOCK_TIME)
                .confirmations(CONFIRMATIONS)
                .tx(null)
                .build();
    }

    private static Transfer aBtcTransfer() {
        return Transfer.builder()
                .txHash(TX_HASH)
                .fromAddress(FROM_ADDRESS)
                .toAddress(TO_ADDRESS)
                .rawAmount("50000000")
                .amount(new BigDecimal("0.50000000"))
                .decimals(8)
                .tokenSymbol("BTC")
                .tokenContractAddress(null)
                .blockNumber(BLOCK_HEIGHT)
                .blockHash(BLOCK_HASH)
                .transactionIndex(0)
                .logIndex(0)
                .chainId(BITCOIN_CHAIN)
                .timestamp(BLOCK_TIMESTAMP)
                .nativeTransfer(true)
                .build();
    }
}
