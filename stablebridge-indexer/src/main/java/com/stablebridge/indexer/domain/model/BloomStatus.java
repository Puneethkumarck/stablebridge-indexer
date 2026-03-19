package com.stablebridge.indexer.domain.model;

import lombok.Builder;

import java.util.List;

/**
 * Domain model representing the current bloom filter configuration and status.
 *
 * @param backend            bloom filter backend in use ({@code "redis"} or {@code "memory"})
 * @param expectedInsertions expected number of wallet addresses the bloom filter is sized for
 * @param errorRate          target false positive rate (e.g., {@code 0.001} = 0.1%)
 * @param networkTypes       list of network types covered by the bloom filter
 */
@Builder
public record BloomStatus(
        String backend,
        long expectedInsertions,
        double errorRate,
        List<NetworkType> networkTypes) {}
