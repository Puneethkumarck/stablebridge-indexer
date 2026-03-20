package com.stablebridge.indexer.domain.service;

import com.stablebridge.indexer.domain.model.NetworkType;
import com.stablebridge.indexer.domain.model.WalletAddress;
import com.stablebridge.indexer.domain.port.AddressFilter;
import com.stablebridge.indexer.domain.port.WalletAddressRepository;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletCommandHandler {

    private final WalletAddressRepository walletAddressRepository;
    private final AddressFilter addressFilter;

    public WalletAddress addWallet(String address, NetworkType networkType, String label) {
        var walletAddress = WalletAddress.builder()
                .address(address)
                .networkType(networkType)
                .label(label)
                .active(true)
                .build();

        var saved = walletAddressRepository.save(walletAddress);
        addressFilter.add(address, networkType);

        log.info("Added wallet address — address={}, networkType={}, label={}", address, networkType, label);
        return saved;
    }

    public List<WalletAddress> addWalletsBatch(List<WalletAddressTuple> requests) {
        var results = new ArrayList<WalletAddress>(requests.size());
        for (WalletAddressTuple request : requests) {
            results.add(addWallet(request.address(), request.networkType(), request.label()));
        }
        log.info("Added {} wallet addresses in batch", results.size());
        return results;
    }

    public List<WalletAddress> listWallets(NetworkType networkType) {
        return walletAddressRepository.findAllByNetworkType(networkType);
    }

    public void removeWallet(String address, NetworkType networkType) {
        walletAddressRepository.deleteByAddressAndNetworkType(address, networkType);
        addressFilter.remove(address, networkType);

        log.info("Removed wallet address — address={}, networkType={}", address, networkType);
    }

    public void rebuildBloom() {
        var totalCount = 0;
        for (NetworkType networkType : NetworkType.values()) {
            var addresses = walletAddressRepository.findAllByNetworkType(networkType);
            for (WalletAddress walletAddress : addresses) {
                addressFilter.add(walletAddress.address(), networkType);
            }
            totalCount += addresses.size();
            log.info("Rebuilt bloom filter for networkType={} — {} addresses loaded",
                    networkType, addresses.size());
        }
        log.info("Bloom filter rebuild complete — {} total addresses loaded across all network types", totalCount);
    }

    @Builder
    public record WalletAddressTuple(String address, NetworkType networkType, String label) {}
}
