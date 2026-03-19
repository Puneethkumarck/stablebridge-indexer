package com.stablebridge.indexer.domain.model;

import lombok.Builder;

/**
 * Domain representation of a chain's configuration used for status reporting.
 *
 * <p>This is a domain-layer projection of the application-layer {@code ChainProperties},
 * carrying only the fields needed by domain services (hexagonal boundary).
 *
 * @param chainName   the network identifier (e.g., {@code "ethereum_mainnet"})
 * @param networkType the network type (EVM, SOLANA, BITCOIN)
 * @param enabled     whether this chain is enabled for indexing
 */
@Builder
public record ChainConfiguration(
        String chainName,
        NetworkType networkType,
        boolean enabled) {}
