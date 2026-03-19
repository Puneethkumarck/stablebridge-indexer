package com.stablebridge.indexer.application.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * Root configuration properties for the StableBridge Indexer, bound to the {@code indexer} prefix.
 *
 * <p>Aggregates all sub-configurations: per-chain settings, bloom filter tuning, and API auth.
 *
 * <pre>{@code
 * indexer:
 *   api:
 *     key: "${INDEXER_API_KEY}"
 *   bloom:
 *     backend: redis
 *     error-rate: 0.001
 *     expected-insertions: 1000000
 *   chains:
 *     ethereum_mainnet:
 *       enabled: true
 *       type: evm
 *       ...
 * }</pre>
 *
 * @param chains  map of chain configurations keyed by network id (e.g., {@code "ethereum_mainnet"})
 * @param bloom   bloom filter configuration
 * @param api     API authentication configuration
 */
@ConfigurationProperties(prefix = "indexer")
public record IndexerProperties(
        Map<String, ChainProperties> chains,
        BloomProperties bloom,
        ApiProperties api
) {

    public IndexerProperties {
        if (chains == null) {
            chains = Map.of();
        }
    }
}
