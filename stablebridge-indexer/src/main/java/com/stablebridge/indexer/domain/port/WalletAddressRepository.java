package com.stablebridge.indexer.domain.port;

import com.stablebridge.indexer.domain.model.NetworkType;
import com.stablebridge.indexer.domain.model.WalletAddress;

import java.util.List;
import java.util.Optional;

/**
 * Domain port for wallet address persistence.
 *
 * <p>This is a domain-level repository interface — not a Spring Data repository.
 * Infrastructure adapters implement this port using JPA or any other persistence mechanism.
 *
 * <p>Wallet addresses are scoped per {@link NetworkType}: registering an address with
 * {@code EVM} watches it across all EVM chains (Ethereum, Base, Polygon, etc.).
 */
public interface WalletAddressRepository {

    /**
     * Saves or updates a wallet address.
     *
     * @param walletAddress the wallet address to save
     * @return the saved wallet address (with generated fields populated)
     */
    WalletAddress save(WalletAddress walletAddress);

    /**
     * Finds a wallet address by its address string and network type.
     *
     * @param address     the wallet address string
     * @param networkType the network type scope
     * @return the wallet address if found
     */
    Optional<WalletAddress> findByAddressAndNetworkType(String address, NetworkType networkType);

    /**
     * Returns all wallet addresses for the given network type.
     *
     * @param networkType the network type to filter by
     * @return all wallet addresses of the given network type
     */
    List<WalletAddress> findAllByNetworkType(NetworkType networkType);

    /**
     * Checks whether a wallet address exists for the given address and network type.
     *
     * <p>This is the DB confirmation step in the bloom + DB confirm pattern.
     * Must be called after a positive bloom filter result to eliminate false positives
     * before publishing transfer events.
     *
     * @param address     the wallet address string to check
     * @param networkType the network type scope
     * @return {@code true} if the address exists in the database
     */
    boolean existsByAddressAndNetworkType(String address, NetworkType networkType);

    /**
     * Deletes a wallet address by its address string and network type.
     *
     * @param address     the wallet address string to delete
     * @param networkType the network type scope
     */
    void deleteByAddressAndNetworkType(String address, NetworkType networkType);
}
