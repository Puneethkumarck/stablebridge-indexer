package com.stablebridge.indexer.infrastructure.persistence;

import com.stablebridge.indexer.domain.model.NetworkType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JpaWalletAddressRepository extends JpaRepository<WalletAddressEntity, Long> {

    Optional<WalletAddressEntity> findByAddressAndNetworkType(String address, NetworkType networkType);

    List<WalletAddressEntity> findAllByNetworkType(NetworkType networkType);

    boolean existsByAddressAndNetworkType(String address, NetworkType networkType);

    void deleteByAddressAndNetworkType(String address, NetworkType networkType);
}
