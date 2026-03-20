package com.stablebridge.indexer.testutil;

import com.stablebridge.indexer.api.WalletAddressRequest;
import com.stablebridge.indexer.api.WalletAddressResponse;
import com.stablebridge.indexer.domain.model.NetworkType;
import com.stablebridge.indexer.domain.model.WalletAddress;

import java.time.Instant;

/**
 * Test fixtures for wallet address domain objects and API DTOs.
 *
 * <p>Each method returns a builder (or built object) with sensible defaults representing
 * an active EVM wallet address. Callers can override individual fields as needed.
 */
public final class WalletAddressFixtures {

    public static final Long DEFAULT_ID = 1L;
    public static final String DEFAULT_ADDRESS =
            "0xabcdef1234567890abcdef1234567890abcdef12";
    public static final String DEFAULT_LABEL = "merchant-deposit-wallet";
    public static final Instant DEFAULT_CREATED_AT = Instant.parse("2026-03-19T09:00:00Z");
    public static final Instant DEFAULT_UPDATED_AT = Instant.parse("2026-03-19T09:00:00Z");

    private WalletAddressFixtures() {
        // fixture class
    }

    /**
     * Returns a {@link WalletAddress} builder pre-populated with sensible defaults:
     * active EVM wallet with a realistic Ethereum address.
     *
     * <p>Usage:
     * <pre>{@code
     * WalletAddress wallet = aWalletAddress().address("0xcustom...").build();
     * }</pre>
     */
    public static WalletAddress.WalletAddressBuilder aWalletAddress() {
        return WalletAddress.builder()
                .id(DEFAULT_ID)
                .address(DEFAULT_ADDRESS)
                .networkType(NetworkType.EVM)
                .label(DEFAULT_LABEL)
                .active(true)
                .createdAt(DEFAULT_CREATED_AT)
                .updatedAt(DEFAULT_UPDATED_AT);
    }

    /**
     * Returns a {@link WalletAddressRequest} with sensible defaults:
     * EVM network type with a realistic Ethereum address.
     *
     * <p>Since {@code WalletAddressRequest} is a plain record without a builder,
     * this method returns a fully constructed instance.
     *
     * <p>Usage:
     * <pre>{@code
     * WalletAddressRequest request = aWalletAddressRequest();
     * }</pre>
     */
    public static WalletAddressRequest aWalletAddressRequest() {
        return new WalletAddressRequest(
                DEFAULT_ADDRESS,
                com.stablebridge.indexer.api.NetworkType.EVM,
                DEFAULT_LABEL
        );
    }

    /**
     * Returns a {@link WalletAddressResponse} with sensible defaults matching the
     * default {@link WalletAddress}.
     *
     * <p>Since {@code WalletAddressResponse} is a plain record without a builder,
     * this method returns a fully constructed instance.
     *
     * <p>Usage:
     * <pre>{@code
     * WalletAddressResponse response = aWalletAddressResponse();
     * }</pre>
     */
    public static WalletAddressResponse aWalletAddressResponse() {
        return new WalletAddressResponse(
                DEFAULT_ID,
                DEFAULT_ADDRESS,
                com.stablebridge.indexer.api.NetworkType.EVM,
                DEFAULT_LABEL,
                true,
                DEFAULT_CREATED_AT,
                DEFAULT_UPDATED_AT
        );
    }
}
