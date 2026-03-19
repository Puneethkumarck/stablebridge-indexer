package com.stablebridge.indexer.application.properties;

/**
 * API authentication configuration.
 *
 * <p>The indexer uses a simple API key for internal service-to-service authentication
 * (Decision 16). The key is validated via the {@code X-API-Key} header on all
 * {@code /api/v1/**} endpoints.
 *
 * @param key the API key value — sourced from the {@code INDEXER_API_KEY} environment variable
 */
public record ApiProperties(
        String key
) {
}
