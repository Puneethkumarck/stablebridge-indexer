package com.stablebridge.indexer.infrastructure.chain.evm;

import com.stablebridge.indexer.domain.model.ChainId;
import com.stablebridge.indexer.domain.model.Transfer;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
class EvmErc20TransferParser {

    static final String TRANSFER_EVENT_SIGNATURE =
            "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef";

    private static final int EXPECTED_TOPICS_COUNT = 3;

    private final ChainId chainId;
    private final Map<String, EvmTokenConfig> tokenContractsMap;

    EvmErc20TransferParser(ChainId chainId, List<EvmTokenConfig> tokenContracts) {
        this.chainId = chainId;
        this.tokenContractsMap = tokenContracts.stream()
                .collect(Collectors.toMap(
                        tc -> tc.address().toLowerCase(Locale.ROOT),
                        tc -> tc));
    }

    List<Transfer> parseErc20Transfers(List<EvmReceipt> receipts, EvmBlock block) {
        return receipts.stream()
                .filter(EvmReceipt::isSuccessful)
                .flatMap(receipt -> receipt.logs().stream())
                .filter(this::isErc20TransferLog)
                .filter(this::isWhitelistedToken)
                .map(evmLog -> toTransfer(evmLog, block))
                .toList();
    }

    private boolean isErc20TransferLog(EvmLog evmLog) {
        return evmLog.topics() != null
                && evmLog.topics().size() >= EXPECTED_TOPICS_COUNT
                && TRANSFER_EVENT_SIGNATURE.equals(evmLog.topics().getFirst());
    }

    private boolean isWhitelistedToken(EvmLog evmLog) {
        return tokenContractsMap.containsKey(evmLog.address().toLowerCase(Locale.ROOT));
    }

    private Transfer toTransfer(EvmLog evmLog, EvmBlock block) {
        var tokenConfig = tokenContractsMap.get(evmLog.address().toLowerCase(Locale.ROOT));
        var rawAmountBigInt = parseAmountFromHex(evmLog.data());
        var rawAmount = rawAmountBigInt.toString();
        var amount = new BigDecimal(rawAmountBigInt)
                .divide(BigDecimal.TEN.pow(tokenConfig.decimals()), MathContext.DECIMAL128);

        log.debug("Parsed ERC-20 transfer: token={} from={} to={} rawAmount={} on chain={}",
                tokenConfig.symbol(),
                extractAddress(evmLog.topics().get(1)),
                extractAddress(evmLog.topics().get(2)),
                rawAmount,
                chainId);

        return Transfer.builder()
                .txHash(evmLog.transactionHash())
                .fromAddress(extractAddress(evmLog.topics().get(1)))
                .toAddress(extractAddress(evmLog.topics().get(2)))
                .rawAmount(rawAmount)
                .amount(amount)
                .decimals(tokenConfig.decimals())
                .tokenSymbol(tokenConfig.symbol())
                .tokenContractAddress(evmLog.address())
                .blockNumber(block.blockNumber())
                .blockHash(block.hash())
                .transactionIndex(evmLog.txIndex())
                .logIndex(evmLog.logIdx())
                .chainId(chainId)
                .timestamp(block.blockTimestamp())
                .nativeTransfer(false)
                .build();
    }

    private static String extractAddress(String paddedAddress) {
        return "0x" + paddedAddress.substring(paddedAddress.length() - 40);
    }

    private static BigInteger parseAmountFromHex(String hexData) {
        if (hexData == null || "0x".equals(hexData) || "0x0".equals(hexData)) {
            return BigInteger.ZERO;
        }
        var stripped = hexData.startsWith("0x") ? hexData.substring(2) : hexData;
        if (stripped.isEmpty()) {
            return BigInteger.ZERO;
        }
        return new BigInteger(stripped, 16);
    }
}
