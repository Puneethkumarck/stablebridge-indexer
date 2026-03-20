package com.stablebridge.indexer.domain.port;

import com.stablebridge.indexer.domain.model.NetworkType;

/**
 * Port for address filtering using a probabilistic filter (bloom) backed by DB confirmation.
 *
 * <p>The bloom filter provides sub-millisecond address matching with a configurable false
 * positive rate (target: 0.001 / 0.1%). A positive bloom result must always be confirmed
 * against the database via {@link #contains(String, NetworkType)} before publishing
 * transfer events — bloom-only matching is never sufficient for financial correctness.
 *
 * <p>Filters are scoped per {@link NetworkType}: one bloom filter covers all chains
 * of the same type (e.g., a single EVM filter for Ethereum, Base, Polygon).
 */
public interface AddressFilter {

    /**
     * Checks the bloom filter for a possible match. A {@code true} result means the
     * address <em>might</em> be watched (false positives are possible). A {@code false}
     * result means the address is definitely not watched.
     *
     * @param address     the wallet address to check
     * @param networkType the network type scope for the bloom filter
     * @return {@code true} if the address might be present; {@code false} if definitely absent
     */
    boolean mightContain(String address, NetworkType networkType);

    /**
     * Confirms whether the address is actually watched by checking the persistent store
     * (database). This eliminates bloom filter false positives.
     *
     * @param address     the wallet address to confirm
     * @param networkType the network type scope
     * @return {@code true} if the address is confirmed in the database
     */
    boolean contains(String address, NetworkType networkType);

    /**
     * Adds an address to the filter (both bloom filter and persistent store).
     *
     * @param address     the wallet address to add
     * @param networkType the network type scope
     */
    void add(String address, NetworkType networkType);

    /**
     * Removes an address from the filter.
     *
     * <p>Note: bloom filters do not support removal. This removes the address from the
     * persistent store. A bloom filter rebuild may be required to fully remove the address
     * from the probabilistic filter.
     *
     * @param address     the wallet address to remove
     * @param networkType the network type scope
     */
    void remove(String address, NetworkType networkType);
}
