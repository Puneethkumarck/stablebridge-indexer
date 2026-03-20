package com.stablebridge.indexer.infrastructure.chain.evm;

import lombok.Builder;

import java.time.Duration;
import java.util.List;

@Builder(toBuilder = true)
public record EvmChainConfig(
        String networkId,
        String rpcUrl,
        int rpcBatchSize,
        boolean useBlockReceipts,
        Duration rpcTimeout,
        int maxRetries,
        int rateLimitRps,
        int rateLimitBurst,
        boolean useFinalizedTag,
        int minConfirmations,
        boolean indexNativeTransfers,
        int nativeDecimals,
        List<EvmChainTokenConfig> tokenContracts
) {}
