package com.stablebridge.indexer.application.properties;

import java.time.Duration;
import java.util.List;

/**
 * RPC connection settings for a single chain.
 *
 * <p>Multiple URLs support round-robin failover when one provider is down or rate-limited.
 *
 * @param urls             list of RPC endpoint URLs (failover order)
 * @param batchSize        maximum number of JSON-RPC requests per batch POST (default {@code 50})
 * @param useBlockReceipts whether to use {@code eth_getBlockReceipts} instead of per-tx receipt fetching (default {@code false})
 * @param timeout          HTTP request timeout per call
 * @param maxRetries       maximum retry attempts before marking a request as failed
 * @param rateLimitRps     maximum requests per second to the RPC provider
 * @param rateLimitBurst   burst capacity for the rate limiter
 * @param username         optional RPC basic-auth username (used by Bitcoin Core)
 * @param password         optional RPC basic-auth password (used by Bitcoin Core)
 */
public record RpcProperties(
        List<String> urls,
        int batchSize,
        boolean useBlockReceipts,
        Duration timeout,
        int maxRetries,
        int rateLimitRps,
        int rateLimitBurst,
        String username,
        String password
) {

    public RpcProperties {
        if (urls == null) {
            urls = List.of();
        }
        if (batchSize <= 0) {
            batchSize = 50;
        }
        if (timeout == null) {
            timeout = Duration.ofSeconds(10);
        }
        if (maxRetries <= 0) {
            maxRetries = 3;
        }
        if (rateLimitRps <= 0) {
            rateLimitRps = 25;
        }
        if (rateLimitBurst <= 0) {
            rateLimitBurst = 50;
        }
    }
}
