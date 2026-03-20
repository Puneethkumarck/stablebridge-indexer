package com.stablebridge.indexer.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

@Builder
public record WalletAddressRequest(
        @NotBlank String address,
        @NotNull NetworkType networkType,
        String label) {}
