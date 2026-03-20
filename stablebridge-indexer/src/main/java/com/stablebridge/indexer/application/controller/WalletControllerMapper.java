package com.stablebridge.indexer.application.controller;

import com.stablebridge.indexer.api.WalletAddressResponse;
import com.stablebridge.indexer.domain.model.WalletAddress;
import org.mapstruct.Mapper;

/**
 * MapStruct mapper for converting between API-layer DTOs and domain objects
 * at the controller boundary.
 *
 * <p>Handles the mapping between:
 * <ul>
 *   <li>{@link com.stablebridge.indexer.api.NetworkType} (API enum) and
 *       {@link com.stablebridge.indexer.domain.model.NetworkType} (domain enum)</li>
 *   <li>{@link WalletAddress} (domain) and {@link WalletAddressResponse} (API DTO)</li>
 * </ul>
 */
@Mapper(componentModel = "spring")
public interface WalletControllerMapper {

    /**
     * Converts an API-layer network type to a domain network type.
     *
     * @param networkType the API network type
     * @return the domain network type
     */
    com.stablebridge.indexer.domain.model.NetworkType toDomain(
            com.stablebridge.indexer.api.NetworkType networkType);

    /**
     * Converts a domain network type to an API-layer network type.
     *
     * @param networkType the domain network type
     * @return the API network type
     */
    com.stablebridge.indexer.api.NetworkType toApi(
            com.stablebridge.indexer.domain.model.NetworkType networkType);

    /**
     * Converts a domain wallet address to an API response DTO.
     *
     * @param walletAddress the domain wallet address
     * @return the API response DTO
     */
    WalletAddressResponse toResponse(WalletAddress walletAddress);
}
