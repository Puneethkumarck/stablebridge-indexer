package com.stablebridge.indexer.infrastructure.chain.solana;

import com.stablebridge.indexer.domain.model.ChainId;
import com.stablebridge.indexer.domain.model.Transfer;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
class SolanaSpITransferParser {

    private static final int NO_LOG_INDEX = -1;

    private final ChainId chainId;
    private final Map<String, SolanaTokenConfig> tokenContractsMap;

    SolanaSpITransferParser(ChainId chainId, List<SolanaTokenConfig> tokenContracts) {
        this.chainId = chainId;
        this.tokenContractsMap = tokenContracts.stream()
                .collect(Collectors.toMap(
                        tc -> tc.mintAddress().toLowerCase(Locale.ROOT),
                        tc -> tc));
    }

    List<Transfer> parseSplTransfers(SolanaBlock block, long slot) {
        if (block.transactions() == null || block.transactions().isEmpty()) {
            return List.of();
        }

        return IntStream.range(0, block.transactions().size())
                .boxed()
                .flatMap(txIndex -> parseTransactionTransfers(
                        block.transactions().get(txIndex), txIndex, block, slot).stream())
                .toList();
    }

    private List<Transfer> parseTransactionTransfers(SolanaTransaction transaction,
                                                     int transactionIndex,
                                                     SolanaBlock block,
                                                     long slot) {
        if (isFailedTransaction(transaction)) {
            return List.of();
        }

        var preBalances = safeTokenBalances(transaction.meta().preTokenBalances());
        var postBalances = safeTokenBalances(transaction.meta().postTokenBalances());

        var preByOwnerAndMint = preBalances.stream()
                .filter(b -> isWhitelistedMint(b.mint()))
                .collect(Collectors.toMap(
                        b -> balanceKey(b.owner(), b.mint()),
                        b -> b,
                        (a, b) -> a));

        var postByOwnerAndMint = postBalances.stream()
                .filter(b -> isWhitelistedMint(b.mint()))
                .collect(Collectors.toMap(
                        b -> balanceKey(b.owner(), b.mint()),
                        b -> b,
                        (a, b) -> a));

        var txHash = extractTxHash(transaction);

        return postByOwnerAndMint.entrySet().stream()
                .filter(entry -> {
                    var postAmount = new BigInteger(entry.getValue().uiTokenAmount().amount());
                    var preBalance = preByOwnerAndMint.get(entry.getKey());
                    var preAmount = preBalance != null
                            ? new BigInteger(preBalance.uiTokenAmount().amount())
                            : BigInteger.ZERO;
                    return postAmount.compareTo(preAmount) > 0;
                })
                .map(entry -> {
                    var postBalance = entry.getValue();
                    var preBalance = preByOwnerAndMint.get(entry.getKey());
                    return toTransfer(postBalance, preBalance, txHash,
                            transactionIndex, block, slot);
                })
                .toList();
    }

    private Transfer toTransfer(SolanaTokenBalance postBalance,
                                SolanaTokenBalance preBalance,
                                String txHash,
                                int transactionIndex,
                                SolanaBlock block,
                                long slot) {
        var mint = postBalance.mint();
        var tokenConfig = tokenContractsMap.get(mint.toLowerCase(Locale.ROOT));

        var postAmount = new BigInteger(postBalance.uiTokenAmount().amount());
        var preAmount = preBalance != null
                ? new BigInteger(preBalance.uiTokenAmount().amount())
                : BigInteger.ZERO;
        var rawAmountBigInt = postAmount.subtract(preAmount);
        var rawAmount = rawAmountBigInt.toString();
        var amount = new BigDecimal(rawAmountBigInt)
                .divide(BigDecimal.TEN.pow(tokenConfig.decimals()), MathContext.DECIMAL128);

        var fromAddress = findSender(block.transactions().get(transactionIndex), mint, preBalance);

        log.debug("Parsed SPL token transfer: token={} from={} to={} rawAmount={} on chain={}",
                tokenConfig.symbol(),
                fromAddress,
                postBalance.owner(),
                rawAmount,
                chainId);

        return Transfer.builder()
                .txHash(txHash)
                .fromAddress(fromAddress)
                .toAddress(postBalance.owner())
                .rawAmount(rawAmount)
                .amount(amount)
                .decimals(tokenConfig.decimals())
                .tokenSymbol(tokenConfig.symbol())
                .tokenContractAddress(mint)
                .blockNumber(slot)
                .blockHash(block.blockhash())
                .transactionIndex(transactionIndex)
                .logIndex(NO_LOG_INDEX)
                .chainId(chainId)
                .timestamp(block.blockTimestamp())
                .nativeTransfer(false)
                .build();
    }

    private String findSender(SolanaTransaction transaction, String mint,
                              SolanaTokenBalance receiverPreBalance) {
        var preBalances = safeTokenBalances(transaction.meta().preTokenBalances());
        var postBalances = safeTokenBalances(transaction.meta().postTokenBalances());

        return preBalances.stream()
                .filter(b -> mint.equalsIgnoreCase(b.mint()))
                .filter(b -> {
                    var preAmount = new BigInteger(b.uiTokenAmount().amount());
                    var postBalance = postBalances.stream()
                            .filter(pb -> b.owner().equals(pb.owner())
                                    && mint.equalsIgnoreCase(pb.mint()))
                            .findFirst();
                    var postAmount = postBalance
                            .map(pb -> new BigInteger(pb.uiTokenAmount().amount()))
                            .orElse(BigInteger.ZERO);
                    return postAmount.compareTo(preAmount) < 0;
                })
                .map(SolanaTokenBalance::owner)
                .findFirst()
                .orElse("unknown");
    }

    private boolean isFailedTransaction(SolanaTransaction transaction) {
        return transaction.meta() != null && transaction.meta().err() != null;
    }

    private boolean isWhitelistedMint(String mint) {
        return mint != null && tokenContractsMap.containsKey(mint.toLowerCase(Locale.ROOT));
    }

    private static String extractTxHash(SolanaTransaction transaction) {
        if (transaction.transaction() == null
                || transaction.transaction().signatures() == null
                || transaction.transaction().signatures().isEmpty()) {
            return "unknown";
        }
        return transaction.transaction().signatures().getFirst();
    }

    private static String balanceKey(String owner, String mint) {
        return owner + ":" + mint.toLowerCase(Locale.ROOT);
    }

    private static List<SolanaTokenBalance> safeTokenBalances(List<SolanaTokenBalance> balances) {
        return balances != null ? balances : Collections.emptyList();
    }
}
