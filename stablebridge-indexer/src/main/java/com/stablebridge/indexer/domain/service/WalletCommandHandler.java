package com.stablebridge.indexer.domain.service;

import com.stablebridge.indexer.domain.model.NetworkType;
import com.stablebridge.indexer.domain.model.WalletAddress;
import com.stablebridge.indexer.domain.port.AddressFilter;
import com.stablebridge.indexer.domain.port.WalletAddressRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Domain service handling wallet address CRUD operations and bloom filter management.
 *
 * <p>Coordinates between the {@link WalletAddressRepository} (persistence) and
 * {@link AddressFilter} (bloom filter) to ensure both stores remain consistent.
 *
 * <p>On add: saves to DB first, then adds to bloom filter.
 * On remove: deletes from DB, then removes from bloom filter (which is a no-op for bloom;
 * a full rebuild is required to fully purge removed addresses from the probabilistic filter).
 * On rebuild: iterates all {@link NetworkType}s, loads addresses from DB, and adds each
 * to the bloom filter.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalletCommandHandler {

    private final WalletAddressRepository walletAddressRepository;
    private final AddressFilter addressFilter;

    /**
     * Adds a wallet address to the DB and bloom filter.
     *
     * @param address     the wallet address string
     * @param networkType the network type scope
     * @param label       optional label for the address
     * @return the saved wallet address with generated fields populated
     */
    public WalletAddress addWallet(String address, NetworkType networkType, String label) {
        WalletAddress walletAddress = WalletAddress.builder()
                .address(address)
                .networkType(networkType)
                .label(label)
                .active(true)
                .build();

        WalletAddress saved = walletAddressRepository.save(walletAddress);
        addressFilter.add(address, networkType);

        log.info("Added wallet address — address={}, networkType={}, label={}", address, networkType, label);
        return saved;
    }

    /**
     * Adds multiple wallet addresses in batch.
     *
     * @param requests the list of wallet address tuples (address, networkType, label)
     * @return the list of saved wallet addresses
     */
    public List<WalletAddress> addWalletsBatch(List<WalletAddressTuple> requests) {
        List<WalletAddress> results = new ArrayList<>(requests.size());
        for (WalletAddressTuple request : requests) {
            results.add(addWallet(request.address(), request.networkType(), request.label()));
        }
        log.info("Added {} wallet addresses in batch", results.size());
        return results;
    }

    /**
     * Lists all wallet addresses for the given network type.
     *
     * @param networkType the network type to filter by
     * @return all wallet addresses of the given network type
     */
    public List<WalletAddress> listWallets(NetworkType networkType) {
        return walletAddressRepository.findAllByNetworkType(networkType);
    }

    /**
     * Removes a wallet address from the DB and bloom filter.
     *
     * @param address     the wallet address string to remove
     * @param networkType the network type scope
     */
    public void removeWallet(String address, NetworkType networkType) {
        walletAddressRepository.deleteByAddressAndNetworkType(address, networkType);
        addressFilter.remove(address, networkType);

        log.info("Removed wallet address — address={}, networkType={}", address, networkType);
    }

    /**
     * Rebuilds all bloom filters from the database.
     *
     * <p>Iterates all {@link NetworkType}s, loads all addresses from DB, and adds
     * each to the bloom filter. This is needed after address removals since bloom
     * filters do not support element removal.
     */
    public void rebuildBloom() {
        int totalCount = 0;
        for (NetworkType networkType : NetworkType.values()) {
            List<WalletAddress> addresses = walletAddressRepository.findAllByNetworkType(networkType);
            for (WalletAddress walletAddress : addresses) {
                addressFilter.add(walletAddress.address(), networkType);
            }
            totalCount += addresses.size();
            log.info("Rebuilt bloom filter for networkType={} — {} addresses loaded",
                    networkType, addresses.size());
        }
        log.info("Bloom filter rebuild complete — {} total addresses loaded across all network types", totalCount);
    }

    /**
     * Tuple representing a wallet address request for batch operations.
     *
     * @param address     the wallet address string
     * @param networkType the network type scope
     * @param label       optional label
     */
    public record WalletAddressTuple(String address, NetworkType networkType, String label) {}
}
