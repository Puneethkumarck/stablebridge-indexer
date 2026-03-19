package com.stablebridge.indexer.application.properties;

/**
 * Strategy for determining when a block is considered final.
 *
 * <ul>
 *   <li>{@link #FINALIZED} — uses the chain's native finality tag (e.g., {@code "finalized"} RPC tag on Ethereum)</li>
 *   <li>{@link #CONFIRMATIONS} — considers a block final after {@code minConfirmations} blocks have been built on top</li>
 * </ul>
 */
public enum ConfirmationStrategy {
    FINALIZED,
    CONFIRMATIONS
}
