package com.stablebridge.indexer.testutil;

import com.stablebridge.indexer.domain.event.TransferDetectedEvent;
import com.stablebridge.indexer.domain.model.BlockResult;
import com.stablebridge.indexer.domain.model.ChainId;
import com.stablebridge.indexer.domain.model.IndexedBlock;
import com.stablebridge.indexer.domain.model.Transfer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static com.stablebridge.indexer.domain.model.TransferDirection.INCOMING;

/**
 * Test fixtures for transfer-related domain objects.
 *
 * <p>Each method returns a builder (or built object) with sensible defaults representing
 * a USDC transfer on Ethereum mainnet. Callers can override individual fields as needed.
 */
public final class TransferFixtures {

    public static final String DEFAULT_TX_HASH =
            "0xabc123def456789012345678901234567890abcdef1234567890abcdef123456";
    public static final String DEFAULT_FROM_ADDRESS =
            "0x1234567890abcdef1234567890abcdef12345678";
    public static final String DEFAULT_TO_ADDRESS =
            "0xabcdef1234567890abcdef1234567890abcdef12";
    public static final String DEFAULT_USDC_CONTRACT =
            "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48";
    public static final String DEFAULT_RAW_AMOUNT = "1000000";
    public static final BigDecimal DEFAULT_AMOUNT = new BigDecimal("1.000000");
    public static final int DEFAULT_DECIMALS = 6;
    public static final String DEFAULT_TOKEN_SYMBOL = "USDC";
    public static final long DEFAULT_BLOCK_NUMBER = 19_500_000L;
    public static final String DEFAULT_BLOCK_HASH =
            "0xblockhash1234567890abcdef1234567890abcdef1234567890abcdef12345678";
    public static final String DEFAULT_PARENT_HASH =
            "0xparenthash234567890abcdef1234567890abcdef1234567890abcdef12345678";
    public static final int DEFAULT_TRANSACTION_INDEX = 42;
    public static final int DEFAULT_LOG_INDEX = 7;
    public static final int DEFAULT_TRANSACTION_COUNT = 150;

    private TransferFixtures() {
        // fixture class
    }

    /**
     * Returns a {@link Transfer} builder pre-populated with sensible defaults:
     * USDC token transfer on Ethereum mainnet.
     *
     * <p>Usage:
     * <pre>{@code
     * Transfer transfer = aTransfer().toAddress("0xcustom...").build();
     * }</pre>
     */
    public static Transfer.TransferBuilder aTransfer() {
        return Transfer.builder()
                .txHash(DEFAULT_TX_HASH)
                .fromAddress(DEFAULT_FROM_ADDRESS)
                .toAddress(DEFAULT_TO_ADDRESS)
                .rawAmount(DEFAULT_RAW_AMOUNT)
                .amount(DEFAULT_AMOUNT)
                .decimals(DEFAULT_DECIMALS)
                .tokenSymbol(DEFAULT_TOKEN_SYMBOL)
                .tokenContractAddress(DEFAULT_USDC_CONTRACT)
                .blockNumber(DEFAULT_BLOCK_NUMBER)
                .blockHash(DEFAULT_BLOCK_HASH)
                .transactionIndex(DEFAULT_TRANSACTION_INDEX)
                .logIndex(DEFAULT_LOG_INDEX)
                .chainId(ChainId.ETHEREUM)
                .timestamp(Instant.parse("2026-03-19T10:15:30Z"))
                .nativeTransfer(false);
    }

    /**
     * Returns a {@link TransferDetectedEvent} builder pre-populated with a default
     * {@link Transfer} and detection timestamp.
     *
     * <p>Usage:
     * <pre>{@code
     * TransferDetectedEvent event = aTransferDetectedEvent().build();
     * }</pre>
     */
    public static TransferDetectedEvent.TransferDetectedEventBuilder aTransferDetectedEvent() {
        return TransferDetectedEvent.builder()
                .transfer(aTransfer().build())
                .direction(INCOMING)
                .detectedAt(Instant.parse("2026-03-19T10:15:31Z"));
    }

    /**
     * Returns an {@link IndexedBlock} builder pre-populated with sensible defaults
     * for an Ethereum mainnet block.
     *
     * <p>Usage:
     * <pre>{@code
     * IndexedBlock block = anIndexedBlock().blockNumber(19_500_001L).build();
     * }</pre>
     */
    public static IndexedBlock.IndexedBlockBuilder anIndexedBlock() {
        return IndexedBlock.builder()
                .blockNumber(DEFAULT_BLOCK_NUMBER)
                .blockHash(DEFAULT_BLOCK_HASH)
                .parentHash(DEFAULT_PARENT_HASH)
                .timestamp(Instant.parse("2026-03-19T10:15:30Z"))
                .chainId(ChainId.ETHEREUM)
                .transactionCount(DEFAULT_TRANSACTION_COUNT);
    }

    /**
     * Returns a {@link BlockResult} builder pre-populated with a default
     * {@link IndexedBlock} and a single default {@link Transfer}.
     *
     * <p>Usage:
     * <pre>{@code
     * BlockResult result = aBlockResult().build();
     * }</pre>
     */
    public static BlockResult.BlockResultBuilder aBlockResult() {
        return BlockResult.builder()
                .indexedBlock(anIndexedBlock().build())
                .transfers(List.of(aTransfer().build()));
    }
}
