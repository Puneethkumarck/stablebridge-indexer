package com.stablebridge.indexer.infrastructure.chain.solana;

import lombok.Builder;

import java.time.Duration;
import java.util.List;

@Builder(toBuilder = true)
public record SolanaChainConfig(
        String networkId,
        String rpcUrl,
        Duration rpcTimeout,
        boolean indexNativeTransfers,
        int nativeDecimals,
        List<SolanaChainTokenConfig> tokenContracts) {}
