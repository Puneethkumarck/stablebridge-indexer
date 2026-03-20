package com.stablebridge.indexer.api;

import lombok.Builder;

import java.time.Instant;

@Builder
public record WalletAddressResponse(
        Long id,
        String address,
        NetworkType networkType,
        String label,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {}
