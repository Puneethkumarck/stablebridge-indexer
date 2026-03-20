package com.stablebridge.indexer.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record WalletAddressRequest(
        @NotBlank String address,
        @NotNull NetworkType networkType,
        String label) {}
