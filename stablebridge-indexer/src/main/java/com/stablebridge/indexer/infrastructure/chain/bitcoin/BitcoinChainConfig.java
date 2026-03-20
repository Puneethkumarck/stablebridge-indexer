package com.stablebridge.indexer.infrastructure.chain.bitcoin;

import lombok.Builder;

import java.time.Duration;

@Builder(toBuilder = true)
public record BitcoinChainConfig(
        String networkId,
        String rpcUrl,
        String rpcUsername,
        String rpcPassword,
        Duration rpcTimeout,
        int minConfirmations) {}
