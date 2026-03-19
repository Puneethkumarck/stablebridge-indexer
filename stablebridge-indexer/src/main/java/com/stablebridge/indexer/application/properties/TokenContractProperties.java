package com.stablebridge.indexer.application.properties;

/**
 * Configuration for a single whitelisted token contract on a chain.
 *
 * <p>Only transfers involving contracts listed here are indexed — all other
 * ERC-20 transfers are silently skipped (Decision 12: Token Contract Allowlisting).
 *
 * @param address  the on-chain contract address (e.g., {@code "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48"})
 * @param symbol   human-readable token symbol (e.g., {@code "USDC"})
 * @param decimals token decimal precision (e.g., {@code 6} for USDC, {@code 18} for DAI)
 */
public record TokenContractProperties(
        String address,
        String symbol,
        int decimals
) {
}
