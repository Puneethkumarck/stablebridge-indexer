package com.stablebridge.indexer.infrastructure.chain.evm;

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
import java.util.List;

import static com.stablebridge.indexer.domain.model.ChainId.ETHEREUM;
import static com.stablebridge.indexer.domain.model.ChainId.POLYGON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("EvmChainIndexer")
class EvmChainIndexerTest {

    private static final long BLOCK_NUMBER = 19_500_000L;
    private static final String BLOCK_NUMBER_HEX = HexUtils.longToHex(BLOCK_NUMBER);
    private static final String BLOCK_HASH =
            "0xblockhash1234567890abcdef1234567890abcdef1234567890abcdef12345678";
    private static final String PARENT_HASH =
            "0xparenthash234567890abcdef1234567890abcdef1234567890abcdef12345678";
    private static final String BLOCK_TIMESTAMP_HEX = "0x65b3e8c0";
    private static final String TX_HASH_1 =
            "0xabc123def456789012345678901234567890abcdef1234567890abcdef123456";
    private static final String TX_HASH_2 =
            "0xdef456789012345678901234567890abcdef1234567890abcdef123456789abc";
    private static final String FROM_ADDRESS = "0x1234567890abcdef1234567890abcdef12345678";
    private static final String TO_ADDRESS = "0xabcdef1234567890abcdef1234567890abcdef12";
    private static final int MIN_CONFIRMATIONS = 12;

    @Mock
    private ResilientEvmRpcClient rpcClient;

    @Mock
    private EvmNativeTransferParser nativeTransferParser;

    @Mock
    private EvmErc20TransferParser erc20TransferParser;

    @Nested
    @DisplayName("indexBlock")
    class IndexBlock {

        @Test
        @DisplayName("fetches block and per-transaction receipts when block receipts not supported")
        void fetchesBlockAndPerTransactionReceipts() {
            // given
            var indexer = createIndexer(false, true);
            var block = aBlockWithTransactions();
            var receipts = List.of(aSuccessfulReceipt(TX_HASH_1), aSuccessfulReceipt(TX_HASH_2));
            var erc20Transfer = anErc20Transfer();

            given(rpcClient.getBlockByNumber(BLOCK_NUMBER)).willReturn(block);
            given(rpcClient.supportsBlockReceipts()).willReturn(false);
            given(rpcClient.getTransactionReceipts(List.of(TX_HASH_1, TX_HASH_2))).willReturn(receipts);
            given(erc20TransferParser.parseErc20Transfers(receipts, block))
                    .willReturn(List.of(erc20Transfer));

            var expectedBlock = IndexedBlock.builder()
                    .blockNumber(BLOCK_NUMBER)
                    .blockHash(BLOCK_HASH)
                    .parentHash(PARENT_HASH)
                    .timestamp(HexUtils.hexToInstant(BLOCK_TIMESTAMP_HEX))
                    .chainId(ETHEREUM)
                    .transactionCount(2)
                    .build();
            var expected = BlockResult.builder()
                    .indexedBlock(expectedBlock)
                    .transfers(List.of(erc20Transfer))
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
        @DisplayName("uses getBlockReceipts when block receipts are supported")
        void usesBlockReceiptsWhenSupported() {
            // given
            var indexer = createIndexer(false, true);
            var block = aBlockWithTransactions();
            var receipts = List.of(aSuccessfulReceipt(TX_HASH_1), aSuccessfulReceipt(TX_HASH_2));
            var erc20Transfer = anErc20Transfer();

            given(rpcClient.getBlockByNumber(BLOCK_NUMBER)).willReturn(block);
            given(rpcClient.supportsBlockReceipts()).willReturn(true);
            given(rpcClient.getBlockReceipts(BLOCK_NUMBER)).willReturn(receipts);
            given(erc20TransferParser.parseErc20Transfers(receipts, block))
                    .willReturn(List.of(erc20Transfer));

            // when
            var result = indexer.indexBlock(BLOCK_NUMBER);

            // then
            then(rpcClient).should(never()).getTransactionReceipts(List.of(TX_HASH_1, TX_HASH_2));
            assertThat(result.transfers()).hasSize(1);
        }

        @Test
        @DisplayName("combines ERC-20 and native transfers when native indexing is enabled")
        void combinesErc20AndNativeTransfers() {
            // given
            var indexer = createIndexer(true, true);
            var block = aBlockWithTransactions();
            var receipts = List.of(aSuccessfulReceipt(TX_HASH_1), aSuccessfulReceipt(TX_HASH_2));
            var erc20Transfer = anErc20Transfer();
            var nativeTransfer = aNativeTransfer(TX_HASH_1);

            given(rpcClient.getBlockByNumber(BLOCK_NUMBER)).willReturn(block);
            given(rpcClient.supportsBlockReceipts()).willReturn(false);
            given(rpcClient.getTransactionReceipts(List.of(TX_HASH_1, TX_HASH_2))).willReturn(receipts);
            given(erc20TransferParser.parseErc20Transfers(receipts, block))
                    .willReturn(List.of(erc20Transfer));
            given(nativeTransferParser.parseNativeTransfers(block))
                    .willReturn(List.of(nativeTransfer));

            var expectedBlock = IndexedBlock.builder()
                    .blockNumber(BLOCK_NUMBER)
                    .blockHash(BLOCK_HASH)
                    .parentHash(PARENT_HASH)
                    .timestamp(HexUtils.hexToInstant(BLOCK_TIMESTAMP_HEX))
                    .chainId(ETHEREUM)
                    .transactionCount(2)
                    .build();
            var expected = BlockResult.builder()
                    .indexedBlock(expectedBlock)
                    .transfers(List.of(erc20Transfer, nativeTransfer))
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
        @DisplayName("does not parse native transfers when native indexing is disabled")
        void doesNotParseNativeTransfersWhenDisabled() {
            // given
            var indexer = createIndexer(false, true);
            var block = aBlockWithTransactions();
            var receipts = List.of(aSuccessfulReceipt(TX_HASH_1));

            given(rpcClient.getBlockByNumber(BLOCK_NUMBER)).willReturn(block);
            given(rpcClient.supportsBlockReceipts()).willReturn(false);
            given(rpcClient.getTransactionReceipts(List.of(TX_HASH_1, TX_HASH_2))).willReturn(receipts);
            given(erc20TransferParser.parseErc20Transfers(receipts, block)).willReturn(List.of());

            // when
            indexer.indexBlock(BLOCK_NUMBER);

            // then
            then(nativeTransferParser).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("filters native transfers by successful receipt")
        void filtersNativeTransfersBySuccessfulReceipt() {
            // given
            var indexer = createIndexer(true, true);
            var block = aBlockWithTransactions();
            var successfulReceipt = aSuccessfulReceipt(TX_HASH_1);
            var failedReceipt = aFailedReceipt(TX_HASH_2);
            var receipts = List.of(successfulReceipt, failedReceipt);

            var successfulNativeTransfer = aNativeTransfer(TX_HASH_1);
            var failedNativeTransfer = aNativeTransfer(TX_HASH_2);

            given(rpcClient.getBlockByNumber(BLOCK_NUMBER)).willReturn(block);
            given(rpcClient.supportsBlockReceipts()).willReturn(false);
            given(rpcClient.getTransactionReceipts(List.of(TX_HASH_1, TX_HASH_2))).willReturn(receipts);
            given(erc20TransferParser.parseErc20Transfers(receipts, block)).willReturn(List.of());
            given(nativeTransferParser.parseNativeTransfers(block))
                    .willReturn(List.of(successfulNativeTransfer, failedNativeTransfer));

            var expectedBlock = IndexedBlock.builder()
                    .blockNumber(BLOCK_NUMBER)
                    .blockHash(BLOCK_HASH)
                    .parentHash(PARENT_HASH)
                    .timestamp(HexUtils.hexToInstant(BLOCK_TIMESTAMP_HEX))
                    .chainId(ETHEREUM)
                    .transactionCount(2)
                    .build();
            var expected = BlockResult.builder()
                    .indexedBlock(expectedBlock)
                    .transfers(List.of(successfulNativeTransfer))
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
        @DisplayName("returns empty transfers for block with no transactions")
        void returnsEmptyTransfersForEmptyBlock() {
            // given
            var indexer = createIndexer(false, true);
            var block = anEmptyBlock();
            var receipts = List.<EvmReceipt>of();

            given(rpcClient.getBlockByNumber(BLOCK_NUMBER)).willReturn(block);
            given(rpcClient.supportsBlockReceipts()).willReturn(false);
            given(rpcClient.getTransactionReceipts(List.of())).willReturn(receipts);
            given(erc20TransferParser.parseErc20Transfers(receipts, block)).willReturn(List.of());

            var expectedBlock = IndexedBlock.builder()
                    .blockNumber(BLOCK_NUMBER)
                    .blockHash(BLOCK_HASH)
                    .parentHash(PARENT_HASH)
                    .timestamp(HexUtils.hexToInstant(BLOCK_TIMESTAMP_HEX))
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

        @Test
        @DisplayName("handles block with null transactions list")
        void handlesBlockWithNullTransactions() {
            // given
            var indexer = createIndexer(false, true);
            var block = aBlockWithNullTransactions();
            var receipts = List.<EvmReceipt>of();

            given(rpcClient.getBlockByNumber(BLOCK_NUMBER)).willReturn(block);
            given(rpcClient.supportsBlockReceipts()).willReturn(false);
            given(rpcClient.getTransactionReceipts(List.of())).willReturn(receipts);
            given(erc20TransferParser.parseErc20Transfers(receipts, block)).willReturn(List.of());

            var expectedBlock = IndexedBlock.builder()
                    .blockNumber(BLOCK_NUMBER)
                    .blockHash(BLOCK_HASH)
                    .parentHash(PARENT_HASH)
                    .timestamp(HexUtils.hexToInstant(BLOCK_TIMESTAMP_HEX))
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
        @DisplayName("returns latest block number for FINALIZED strategy")
        void returnsLatestBlockNumberForFinalizedStrategy() {
            // given
            var indexer = new EvmChainIndexer(
                    rpcClient, ETHEREUM, true, 0, false,
                    nativeTransferParser, erc20TransferParser);
            given(rpcClient.getLatestBlockNumber()).willReturn(BLOCK_NUMBER);

            // when
            var result = indexer.getLatestFinalizedBlockNumber();

            // then
            assertThat(result).isEqualTo(BLOCK_NUMBER);
        }

        @Test
        @DisplayName("subtracts minConfirmations for CONFIRMATIONS strategy")
        void subtractsMinConfirmationsForConfirmationsStrategy() {
            // given
            var indexer = new EvmChainIndexer(
                    rpcClient, POLYGON, false, MIN_CONFIRMATIONS, false,
                    nativeTransferParser, erc20TransferParser);
            given(rpcClient.getLatestBlockNumber()).willReturn(BLOCK_NUMBER);

            // when
            var result = indexer.getLatestFinalizedBlockNumber();

            // then
            assertThat(result).isEqualTo(BLOCK_NUMBER - MIN_CONFIRMATIONS);
        }
    }

    @Nested
    @DisplayName("getChainId")
    class GetChainId {

        @Test
        @DisplayName("returns configured chain ID")
        void returnsConfiguredChainId() {
            // given
            var indexer = createIndexer(false, true);

            // when
            var result = indexer.getChainId();

            // then
            assertThat(result).isEqualTo(ETHEREUM);
        }

        @Test
        @DisplayName("returns different chain ID when configured differently")
        void returnsDifferentChainIdWhenConfiguredDifferently() {
            // given
            var indexer = new EvmChainIndexer(
                    rpcClient, POLYGON, true, 0, false,
                    nativeTransferParser, erc20TransferParser);

            // when
            var result = indexer.getChainId();

            // then
            assertThat(result).isEqualTo(POLYGON);
        }
    }

    // -- Factory methods for EvmChainIndexer --

    private EvmChainIndexer createIndexer(boolean indexNativeTransfers, boolean useFinalizedTag) {
        var confirmations = useFinalizedTag ? 0 : MIN_CONFIRMATIONS;
        return new EvmChainIndexer(
                rpcClient, ETHEREUM, useFinalizedTag, confirmations, indexNativeTransfers,
                nativeTransferParser, erc20TransferParser);
    }

    // -- ACL DTO factory methods (package-private types cannot be in testFixtures) --

    private static EvmBlock aBlockWithTransactions() {
        return EvmBlock.builder()
                .number(BLOCK_NUMBER_HEX)
                .hash(BLOCK_HASH)
                .parentHash(PARENT_HASH)
                .timestamp(BLOCK_TIMESTAMP_HEX)
                .transactions(List.of(
                        aTransaction(TX_HASH_1, "0x0"),
                        aTransaction(TX_HASH_2, "0x1")))
                .build();
    }

    private static EvmBlock anEmptyBlock() {
        return EvmBlock.builder()
                .number(BLOCK_NUMBER_HEX)
                .hash(BLOCK_HASH)
                .parentHash(PARENT_HASH)
                .timestamp(BLOCK_TIMESTAMP_HEX)
                .transactions(List.of())
                .build();
    }

    private static EvmBlock aBlockWithNullTransactions() {
        return EvmBlock.builder()
                .number(BLOCK_NUMBER_HEX)
                .hash(BLOCK_HASH)
                .parentHash(PARENT_HASH)
                .timestamp(BLOCK_TIMESTAMP_HEX)
                .transactions(null)
                .build();
    }

    private static EvmTransaction aTransaction(String txHash, String transactionIndex) {
        return EvmTransaction.builder()
                .hash(txHash)
                .from(FROM_ADDRESS)
                .to(TO_ADDRESS)
                .value("0x1bc16d674ec80000")
                .input("0x")
                .blockNumber(BLOCK_NUMBER_HEX)
                .transactionIndex(transactionIndex)
                .blockHash(BLOCK_HASH)
                .build();
    }

    private static EvmReceipt aSuccessfulReceipt(String txHash) {
        return EvmReceipt.builder()
                .transactionHash(txHash)
                .transactionIndex("0x0")
                .blockNumber(BLOCK_NUMBER_HEX)
                .blockHash(BLOCK_HASH)
                .from(FROM_ADDRESS)
                .to(TO_ADDRESS)
                .status("0x1")
                .logs(List.of())
                .build();
    }

    private static EvmReceipt aFailedReceipt(String txHash) {
        return EvmReceipt.builder()
                .transactionHash(txHash)
                .transactionIndex("0x1")
                .blockNumber(BLOCK_NUMBER_HEX)
                .blockHash(BLOCK_HASH)
                .from(FROM_ADDRESS)
                .to(TO_ADDRESS)
                .status("0x0")
                .logs(List.of())
                .build();
    }

    private static Transfer anErc20Transfer() {
        return Transfer.builder()
                .txHash(TX_HASH_1)
                .fromAddress(FROM_ADDRESS)
                .toAddress(TO_ADDRESS)
                .rawAmount("1000000")
                .amount(new BigDecimal("1.000000"))
                .decimals(6)
                .tokenSymbol("USDC")
                .tokenContractAddress("0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48")
                .blockNumber(BLOCK_NUMBER)
                .blockHash(BLOCK_HASH)
                .transactionIndex(0)
                .logIndex(0)
                .chainId(ETHEREUM)
                .timestamp(HexUtils.hexToInstant(BLOCK_TIMESTAMP_HEX))
                .nativeTransfer(false)
                .build();
    }

    private static Transfer aNativeTransfer(String txHash) {
        return Transfer.builder()
                .txHash(txHash)
                .fromAddress(FROM_ADDRESS)
                .toAddress(TO_ADDRESS)
                .rawAmount("2000000000000000000")
                .amount(new BigDecimal("2.000000000000000000"))
                .decimals(18)
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
    }
}
