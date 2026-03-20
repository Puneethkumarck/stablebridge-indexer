package com.stablebridge.indexer.application.controller;

import com.stablebridge.indexer.api.WalletAddressResponse;
import com.stablebridge.indexer.domain.model.NetworkType;
import com.stablebridge.indexer.domain.model.WalletAddress;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface WalletControllerMapper {

    NetworkType toDomain(com.stablebridge.indexer.api.NetworkType networkType);

    com.stablebridge.indexer.api.NetworkType toApi(NetworkType networkType);

    WalletAddressResponse toResponse(WalletAddress walletAddress);
}
