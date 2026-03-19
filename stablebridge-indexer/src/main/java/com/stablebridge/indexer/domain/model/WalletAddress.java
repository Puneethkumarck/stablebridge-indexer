package com.stablebridge.indexer.domain.model;

import lombok.Builder;

import java.time.Instant;

@Builder
public record WalletAddress(
        Long id,
        String address,
        NetworkType networkType,
        String label,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {}
