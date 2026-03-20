package com.stablebridge.indexer.domain.model;

import lombok.Builder;

@Builder
public record ChainConfiguration(
        String chainName,
        NetworkType networkType,
        boolean enabled) {}
