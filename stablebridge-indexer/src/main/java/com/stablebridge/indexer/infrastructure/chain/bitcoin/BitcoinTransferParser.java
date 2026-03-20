package com.stablebridge.indexer.infrastructure.chain.bitcoin;

import com.stablebridge.indexer.domain.model.ChainId;
import com.stablebridge.indexer.domain.model.Transfer;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Slf4j
class BitcoinTransferParser {

    private static final int BTC_DECIMALS = 8;
    private static final BigDecimal SATOSHIS_PER_BTC = BigDecimal.TEN.pow(BTC_DECIMALS);
    private static final String BTC_SYMBOL = "BTC";
    private static final Set<String> SKIPPED_SCRIPT_TYPES = Set.of("nulldata", "nonstandard");

    private final ChainId chainId;

    BitcoinTransferParser(ChainId chainId) {
        this.chainId = chainId;
    }

    List<Transfer> parseTransfers(BtcBlock block) {
        if (block.tx() == null || block.tx().isEmpty()) {
            return List.of();
        }

        var blockHeight = block.height();
        var blockHash = block.hash();
        var blockTimestamp = block.blockTimestamp();

        return IntStream.range(0, block.tx().size())
                .boxed()
                .flatMap(txIndex -> {
                    var tx = block.tx().get(txIndex);

                    if (isCoinbaseTransaction(tx)) {
                        log.trace("Skipping coinbase transaction: txid={}", tx.txid());
                        return Stream.<Transfer>empty();
                    }

                    var fromAddress = resolveFromAddress(tx);

                    return tx.vout().stream()
                            .filter(this::hasResolvableAddress)
                            .flatMap(vout -> vout.extractAddresses().stream()
                                    .map(toAddress -> {
                                        var rawAmount = vout.value()
                                                .multiply(SATOSHIS_PER_BTC)
                                                .toBigInteger()
                                                .toString();

                                        return Transfer.builder()
                                                .txHash(tx.txid())
                                                .fromAddress(fromAddress)
                                                .toAddress(toAddress)
                                                .rawAmount(rawAmount)
                                                .amount(vout.value())
                                                .decimals(BTC_DECIMALS)
                                                .tokenSymbol(BTC_SYMBOL)
                                                .tokenContractAddress(null)
                                                .blockNumber(blockHeight)
                                                .blockHash(blockHash)
                                                .transactionIndex(txIndex)
                                                .logIndex(vout.n())
                                                .chainId(chainId)
                                                .timestamp(blockTimestamp)
                                                .nativeTransfer(true)
                                                .build();
                                    }));
                })
                .toList();
    }

    private boolean isCoinbaseTransaction(BtcTransaction tx) {
        if (tx.vin() == null || tx.vin().isEmpty()) {
            return false;
        }
        return tx.vin().getFirst().isCoinbase();
    }

    private String resolveFromAddress(BtcTransaction tx) {
        if (tx.vin() == null || tx.vin().isEmpty()) {
            return null;
        }
        return tx.vin().getFirst()
                .extractFirstAddress()
                .orElse(null);
    }

    private boolean hasResolvableAddress(BtcVout vout) {
        if (vout.scriptPubKey() == null) {
            return false;
        }
        var scriptType = vout.scriptPubKey().type();
        if (scriptType != null && SKIPPED_SCRIPT_TYPES.contains(scriptType)) {
            return false;
        }
        return !vout.extractAddresses().isEmpty();
    }
}
