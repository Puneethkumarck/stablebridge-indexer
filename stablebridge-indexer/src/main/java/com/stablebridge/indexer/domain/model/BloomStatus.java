package com.stablebridge.indexer.domain.model;

import lombok.Builder;

import java.util.List;

@Builder
public record BloomStatus(
        String backend,
        long expectedInsertions,
        double errorRate,
        List<NetworkType> networkTypes) {}
