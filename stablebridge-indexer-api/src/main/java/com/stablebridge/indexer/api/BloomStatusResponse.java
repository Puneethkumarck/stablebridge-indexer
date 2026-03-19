package com.stablebridge.indexer.api;

import java.util.List;

/**
 * API response DTO for bloom filter status information.
 *
 * @param backend            bloom filter backend in use ({@code "redis"} or {@code "memory"})
 * @param expectedInsertions expected number of wallet addresses the bloom filter is sized for
 * @param errorRate          target false positive rate (e.g., {@code 0.001} = 0.1%)
 * @param networkTypes       list of network types covered by the bloom filter
 */
public record BloomStatusResponse(
        String backend,
        long expectedInsertions,
        double errorRate,
        List<String> networkTypes) {}
