package com.stablebridge.indexer.api;

import lombok.Builder;

import java.util.List;

@Builder
public record BloomStatusResponse(
        String backend,
        long expectedInsertions,
        double errorRate,
        List<String> networkTypes) {}
