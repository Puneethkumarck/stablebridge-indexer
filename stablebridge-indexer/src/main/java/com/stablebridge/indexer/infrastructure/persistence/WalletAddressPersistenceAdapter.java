package com.stablebridge.indexer.infrastructure.persistence;

import com.stablebridge.indexer.domain.model.NetworkType;
import com.stablebridge.indexer.domain.model.WalletAddress;
import com.stablebridge.indexer.domain.port.WalletAddressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class WalletAddressPersistenceAdapter implements WalletAddressRepository {

    private final JpaWalletAddressRepository jpaRepository;
    private final WalletAddressMapper mapper;

    @Override
    public WalletAddress save(WalletAddress walletAddress) {
        WalletAddressEntity entity = mapper.toEntity(walletAddress);
        WalletAddressEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<WalletAddress> findByAddressAndNetworkType(String address, NetworkType networkType) {
        return jpaRepository.findByAddressAndNetworkType(address, networkType)
                .map(mapper::toDomain);
    }

    @Override
    public List<WalletAddress> findAllByNetworkType(NetworkType networkType) {
        return jpaRepository.findAllByNetworkType(networkType).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public boolean existsByAddressAndNetworkType(String address, NetworkType networkType) {
        return jpaRepository.existsByAddressAndNetworkType(address, networkType);
    }

    @Override
    @Transactional
    public void deleteByAddressAndNetworkType(String address, NetworkType networkType) {
        jpaRepository.deleteByAddressAndNetworkType(address, networkType);
    }
}
