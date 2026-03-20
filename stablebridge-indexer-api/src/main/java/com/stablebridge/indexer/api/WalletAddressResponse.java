package com.stablebridge.indexer.api;

import java.time.Instant;

public record WalletAddressResponse(
        Long id,
        String address,
        NetworkType networkType,
        String label,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {}
