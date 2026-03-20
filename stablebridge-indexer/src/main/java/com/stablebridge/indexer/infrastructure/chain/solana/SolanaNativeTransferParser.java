package com.stablebridge.indexer.infrastructure.chain.solana;

import com.stablebridge.indexer.domain.model.ChainId;
import com.stablebridge.indexer.domain.model.Transfer;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

@Slf4j
class SolanaNativeTransferParser {

    static final String SYSTEM_PROGRAM_ID = "11111111111111111111111111111111";
    private static final String NATIVE_SYMBOL = "SOL";
    private static final int SOL_DECIMALS = 9;
    private static final int NATIVE_TRANSFER_LOG_INDEX = -1;
    private static final int SYSTEM_TRANSFER_FROM_INDEX = 0;
    private static final int SYSTEM_TRANSFER_TO_INDEX = 1;
    private static final int SYSTEM_TRANSFER_ACCOUNT_COUNT = 2;

    private final ChainId chainId;

    SolanaNativeTransferParser(ChainId chainId) {
        this.chainId = chainId;
    }

    List<Transfer> parseNativeTransfers(SolanaBlock block, long slot) {
        if (block.transactions() == null || block.transactions().isEmpty()) {
            return List.of();
        }

        var blockHash = block.blockhash();
        var blockTimestamp = block.blockTimestamp();

        return IntStream.range(0, block.transactions().size())
                .boxed()
                .flatMap(txIndex -> parseTransaction(
                        block.transactions().get(txIndex), txIndex, slot, blockHash, blockTimestamp)
                        .stream())
                .toList();
    }

    private List<Transfer> parseTransaction(SolanaTransaction tx, int txIndex,
                                            long slot, String blockHash, Instant blockTimestamp) {
        if (isFailedTransaction(tx)) {
            return List.of();
        }

        var message = tx.transaction().message();
        var accountKeys = message.accountKeys();
        var instructions = message.instructions();

        if (accountKeys == null || instructions == null) {
            return List.of();
        }

        var txHash = tx.transaction().signatures().getFirst();

        return instructions.stream()
                .filter(this::isSystemProgramTransfer)
                .map(instruction -> buildTransfer(
                        instruction, tx.meta(), accountKeys, txHash,
                        txIndex, slot, blockHash, blockTimestamp))
                .flatMap(List::stream)
                .toList();
    }

    private boolean isFailedTransaction(SolanaTransaction tx) {
        return tx.meta() != null && tx.meta().err() != null;
    }

    private boolean isSystemProgramTransfer(SolanaInstruction instruction) {
        return SYSTEM_PROGRAM_ID.equals(instruction.programId())
                && instruction.accounts() != null
                && instruction.accounts().size() >= SYSTEM_TRANSFER_ACCOUNT_COUNT;
    }

    private List<Transfer> buildTransfer(SolanaInstruction instruction,
                                         SolanaTransactionMeta meta,
                                         List<String> accountKeys,
                                         String txHash, int txIndex,
                                         long slot, String blockHash,
                                         Instant blockTimestamp) {
        var fromAddress = instruction.accounts().get(SYSTEM_TRANSFER_FROM_INDEX);
        var toAddress = instruction.accounts().get(SYSTEM_TRANSFER_TO_INDEX);

        var toAccountIndex = accountKeys.indexOf(toAddress);
        if (toAccountIndex < 0 || meta == null) {
            log.warn("Cannot resolve receiver account index for tx={}", txHash);
            return List.of();
        }

        var lamports = computeReceivedLamports(meta, toAccountIndex);
        if (lamports <= 0) {
            return List.of();
        }

        var rawAmount = String.valueOf(lamports);
        var amount = new BigDecimal(lamports)
                .divide(BigDecimal.TEN.pow(SOL_DECIMALS), SOL_DECIMALS, RoundingMode.HALF_UP);

        var transfer = Transfer.builder()
                .txHash(txHash)
                .fromAddress(fromAddress)
                .toAddress(toAddress)
                .rawAmount(rawAmount)
                .amount(amount)
                .decimals(SOL_DECIMALS)
                .tokenSymbol(NATIVE_SYMBOL)
                .tokenContractAddress(null)
                .blockNumber(slot)
                .blockHash(blockHash)
                .transactionIndex(txIndex)
                .logIndex(NATIVE_TRANSFER_LOG_INDEX)
                .chainId(chainId)
                .timestamp(blockTimestamp)
                .nativeTransfer(true)
                .build();

        return List.of(transfer);
    }

    private long computeReceivedLamports(SolanaTransactionMeta meta, int accountIndex) {
        var preBalances = meta.preBalances();
        var postBalances = meta.postBalances();

        if (preBalances == null || postBalances == null
                || accountIndex >= preBalances.size()
                || accountIndex >= postBalances.size()) {
            return 0;
        }

        return postBalances.get(accountIndex) - preBalances.get(accountIndex);
    }
}
