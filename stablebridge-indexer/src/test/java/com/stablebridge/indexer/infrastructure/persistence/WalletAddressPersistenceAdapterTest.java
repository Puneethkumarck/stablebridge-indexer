package com.stablebridge.indexer.infrastructure.persistence;

import com.stablebridge.indexer.domain.model.NetworkType;
import com.stablebridge.indexer.domain.model.WalletAddress;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static com.stablebridge.indexer.testutil.WalletAddressFixtures.DEFAULT_ADDRESS;
import static com.stablebridge.indexer.testutil.WalletAddressFixtures.DEFAULT_CREATED_AT;
import static com.stablebridge.indexer.testutil.WalletAddressFixtures.DEFAULT_ID;
import static com.stablebridge.indexer.testutil.WalletAddressFixtures.DEFAULT_LABEL;
import static com.stablebridge.indexer.testutil.WalletAddressFixtures.DEFAULT_UPDATED_AT;
import static com.stablebridge.indexer.testutil.WalletAddressFixtures.aWalletAddress;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class WalletAddressPersistenceAdapterTest {

    @Mock
    private JpaWalletAddressRepository jpaRepository;

    @Mock
    private WalletAddressMapper mapper;

    @InjectMocks
    private WalletAddressPersistenceAdapter adapter;

    @Test
    @DisplayName("save maps to entity, persists, and maps back to domain")
    void save_persists_and_returns_domain() {
        WalletAddress domain = aWalletAddress().build();
        WalletAddressEntity entity = anEntity();
        WalletAddressEntity savedEntity = anEntity();
        WalletAddress savedDomain = aWalletAddress().build();

        given(mapper.toEntity(domain)).willReturn(entity);
        given(jpaRepository.save(entity)).willReturn(savedEntity);
        given(mapper.toDomain(savedEntity)).willReturn(savedDomain);

        WalletAddress actual = adapter.save(domain);

        assertThat(actual)
                .usingRecursiveComparison()
                .isEqualTo(savedDomain);
        then(mapper).should().toEntity(domain);
        then(jpaRepository).should().save(entity);
        then(mapper).should().toDomain(savedEntity);
    }

    @Test
    @DisplayName("findByAddressAndNetworkType delegates and maps result")
    void findByAddressAndNetworkType_returns_mapped_domain() {
        WalletAddressEntity entity = anEntity();
        WalletAddress domain = aWalletAddress().build();

        given(jpaRepository.findByAddressAndNetworkType(DEFAULT_ADDRESS, NetworkType.EVM))
                .willReturn(Optional.of(entity));
        given(mapper.toDomain(entity)).willReturn(domain);

        Optional<WalletAddress> actual = adapter.findByAddressAndNetworkType(DEFAULT_ADDRESS, NetworkType.EVM);

        WalletAddress expected = aWalletAddress().build();
        assertThat(actual)
                .isPresent()
                .hasValueSatisfying(value ->
                        assertThat(value)
                                .usingRecursiveComparison()
                                .isEqualTo(expected));
        then(jpaRepository).should().findByAddressAndNetworkType(DEFAULT_ADDRESS, NetworkType.EVM);
    }

    @Test
    @DisplayName("findAllByNetworkType delegates and maps list")
    void findAllByNetworkType_returns_mapped_list() {
        WalletAddressEntity entity = anEntity();
        WalletAddress domain = aWalletAddress().build();

        given(jpaRepository.findAllByNetworkType(NetworkType.EVM)).willReturn(List.of(entity));
        given(mapper.toDomain(entity)).willReturn(domain);

        List<WalletAddress> actual = adapter.findAllByNetworkType(NetworkType.EVM);

        WalletAddress expected = aWalletAddress().build();
        assertThat(actual)
                .hasSize(1)
                .first()
                .usingRecursiveComparison()
                .isEqualTo(expected);
        then(jpaRepository).should().findAllByNetworkType(NetworkType.EVM);
    }

    @Test
    @DisplayName("existsByAddressAndNetworkType delegates directly")
    void existsByAddressAndNetworkType_delegates() {
        given(jpaRepository.existsByAddressAndNetworkType(DEFAULT_ADDRESS, NetworkType.EVM))
                .willReturn(true);

        boolean actual = adapter.existsByAddressAndNetworkType(DEFAULT_ADDRESS, NetworkType.EVM);

        assertThat(actual).isTrue();
        then(jpaRepository).should().existsByAddressAndNetworkType(DEFAULT_ADDRESS, NetworkType.EVM);
    }

    @Test
    @DisplayName("deleteByAddressAndNetworkType delegates to JPA repository")
    void deleteByAddressAndNetworkType_delegates() {
        adapter.deleteByAddressAndNetworkType(DEFAULT_ADDRESS, NetworkType.EVM);

        then(jpaRepository).should().deleteByAddressAndNetworkType(DEFAULT_ADDRESS, NetworkType.EVM);
    }

    private static WalletAddressEntity anEntity() {
        return WalletAddressEntity.builder()
                .id(DEFAULT_ID)
                .address(DEFAULT_ADDRESS)
                .networkType(NetworkType.EVM)
                .label(DEFAULT_LABEL)
                .active(true)
                .createdAt(DEFAULT_CREATED_AT)
                .updatedAt(DEFAULT_UPDATED_AT)
                .build();
    }
}
