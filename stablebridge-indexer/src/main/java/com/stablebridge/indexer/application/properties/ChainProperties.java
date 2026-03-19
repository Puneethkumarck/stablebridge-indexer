package com.stablebridge.indexer.application.properties;

import java.time.Duration;
import java.util.List;

/**
 * Configuration for a single blockchain chain.
 *
 * <p>Each entry under {@code indexer.chains.<name>} maps to one instance. The chain name
 * (map key) acts as the {@code networkId} throughout the system (e.g., {@code "ethereum_mainnet"}).
 *
 * @param enabled                whether this chain is actively indexed
 * @param type                   chain family ({@code "evm"}, {@code "solana"}, {@code "bitcoin"})
 * @param confirmationStrategy   finality strategy — {@link ConfirmationStrategy#FINALIZED} uses the
 *                               chain's native finality tag; {@link ConfirmationStrategy#CONFIRMATIONS}
 *                               waits for {@code minConfirmations} blocks
 * @param minConfirmations       number of confirmations required when using
 *                               {@link ConfirmationStrategy#CONFIRMATIONS} (default {@code 0})
 * @param indexNativeTransfers   whether to index native asset transfers such as ETH, MATIC (default {@code false})
 * @param nativeDecimals         decimal precision for the chain's native asset (e.g., {@code 18} for ETH)
 * @param startBlock             block number to begin indexing from ({@code 0} means latest finalized)
 * @param pollInterval           how often the regular worker polls for new finalized blocks
 * @param batchSize              number of blocks to fetch per polling cycle during catchup
 * @param rpc                    RPC connection settings
 * @param tokenContracts         whitelisted token contracts to index on this chain
 */
public record ChainProperties(
        boolean enabled,
        String type,
        ConfirmationStrategy confirmationStrategy,
        int minConfirmations,
        boolean indexNativeTransfers,
        int nativeDecimals,
        long startBlock,
        Duration pollInterval,
        int batchSize,
        RpcProperties rpc,
        List<TokenContractProperties> tokenContracts
) {

    public ChainProperties {
        if (confirmationStrategy == null) {
            confirmationStrategy = ConfirmationStrategy.FINALIZED;
        }
        if (pollInterval == null) {
            pollInterval = Duration.ofSeconds(12);
        }
        if (batchSize <= 0) {
            batchSize = 10;
        }
        if (nativeDecimals <= 0) {
            nativeDecimals = 18;
        }
        if (tokenContracts == null) {
            tokenContracts = List.of();
        }
    }
}
