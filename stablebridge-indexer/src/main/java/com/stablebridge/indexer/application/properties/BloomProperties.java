package com.stablebridge.indexer.application.properties;

/**
 * Configuration for the Bloom filter used in the address-matching hot path.
 *
 * <p>The bloom filter provides sub-millisecond address lookups. Matches are always
 * confirmed against the database before publishing events (Decision 4: Bloom + DB confirm).
 *
 * @param backend            bloom filter backend — {@code "redis"} for RedisBloom (production)
 *                           or {@code "memory"} for Guava-based in-memory (local dev)
 * @param expectedInsertions expected number of wallet addresses to be inserted
 * @param errorRate          target false positive rate (default {@code 0.001} = 0.1%)
 */
public record BloomProperties(
        String backend,
        long expectedInsertions,
        double errorRate
) {

    public BloomProperties {
        if (backend == null || backend.isBlank()) {
            backend = "redis";
        }
        if (expectedInsertions <= 0) {
            expectedInsertions = 1_000_000L;
        }
        if (errorRate <= 0.0) {
            errorRate = 0.001;
        }
    }
}
