package com.stablebridge.indexer.infrastructure.persistence;

import com.stablebridge.indexer.domain.model.WalletAddress;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface WalletAddressMapper {

    WalletAddress toDomain(WalletAddressEntity entity);

    WalletAddressEntity toEntity(WalletAddress domain);
}
