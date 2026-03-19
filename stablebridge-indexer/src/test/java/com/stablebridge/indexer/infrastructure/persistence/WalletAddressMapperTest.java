package com.stablebridge.indexer.infrastructure.persistence;

import com.stablebridge.indexer.domain.model.NetworkType;
import com.stablebridge.indexer.domain.model.WalletAddress;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.stablebridge.indexer.testutil.WalletAddressFixtures.DEFAULT_ADDRESS;
import static com.stablebridge.indexer.testutil.WalletAddressFixtures.DEFAULT_CREATED_AT;
import static com.stablebridge.indexer.testutil.WalletAddressFixtures.DEFAULT_ID;
import static com.stablebridge.indexer.testutil.WalletAddressFixtures.DEFAULT_LABEL;
import static com.stablebridge.indexer.testutil.WalletAddressFixtures.DEFAULT_UPDATED_AT;
import static com.stablebridge.indexer.testutil.WalletAddressFixtures.aWalletAddress;
import static org.assertj.core.api.Assertions.assertThat;

class WalletAddressMapperTest {

    private final WalletAddressMapper mapper = new WalletAddressMapperImpl();

    @Test
    @DisplayName("toDomain maps entity fields to domain record")
    void toDomain_maps_entity_to_domain() {
        WalletAddressEntity entity = WalletAddressEntity.builder()
                .id(DEFAULT_ID)
                .address(DEFAULT_ADDRESS)
                .networkType(NetworkType.EVM)
                .label(DEFAULT_LABEL)
                .active(true)
                .createdAt(DEFAULT_CREATED_AT)
                .updatedAt(DEFAULT_UPDATED_AT)
                .build();

        WalletAddress actual = mapper.toDomain(entity);

        WalletAddress expected = aWalletAddress().build();
        assertThat(actual)
                .usingRecursiveComparison()
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("toEntity maps domain record to entity fields")
    void toEntity_maps_domain_to_entity() {
        WalletAddress domain = aWalletAddress().build();

        WalletAddressEntity actual = mapper.toEntity(domain);

        WalletAddressEntity expected = WalletAddressEntity.builder()
                .id(DEFAULT_ID)
                .address(DEFAULT_ADDRESS)
                .networkType(NetworkType.EVM)
                .label(DEFAULT_LABEL)
                .active(true)
                .createdAt(DEFAULT_CREATED_AT)
                .updatedAt(DEFAULT_UPDATED_AT)
                .build();
        assertThat(actual)
                .usingRecursiveComparison()
                .isEqualTo(expected);
    }
}
