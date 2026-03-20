package com.stablebridge.indexer.infrastructure.chain.evm;

import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
class RpcUrlManager {

    static final int MAX_CONSECUTIVE_FAILURES = 3;
    static final Duration HEALTH_PROBE_INTERVAL = Duration.ofSeconds(60);

    private final List<String> urls;
    private final Map<String, UrlHealth> healthMap;
    private final AtomicInteger roundRobinIndex = new AtomicInteger(0);
    private final Clock clock;

    RpcUrlManager(List<String> urls) {
        this(urls, Clock.systemUTC());
    }

    RpcUrlManager(List<String> urls, Clock clock) {
        if (urls == null || urls.isEmpty()) {
            throw new IllegalArgumentException("At least one RPC URL must be provided");
        }
        this.urls = List.copyOf(urls);
        this.clock = clock;
        this.healthMap = urls.stream()
                .collect(Collectors.toConcurrentMap(url -> url, url -> new UrlHealth()));
    }

    String getNextHealthyUrl() {
        var totalUrls = urls.size();
        var startIndex = roundRobinIndex.getAndUpdate(i -> (i + 1) % totalUrls);

        return IntStream.range(0, totalUrls)
                .map(offset -> (startIndex + offset) % totalUrls)
                .mapToObj(urls::get)
                .filter(this::isAvailable)
                .findFirst()
                .orElseThrow(() -> {
                    log.error("All {} RPC URLs are unhealthy and not eligible for health probe", totalUrls);
                    return EvmRpcException.allUrlsUnhealthy(totalUrls);
                });
    }

    void markSuccess(String url) {
        var health = healthMap.get(url);
        if (health == null) {
            return;
        }
        var previousFailures = health.consecutiveFailures.getAndSet(0);
        if (!health.healthy.getAndSet(true)) {
            log.info("RPC URL restored to healthy: url={}, previousFailures={}", url, previousFailures);
        }
    }

    void markFailure(String url) {
        var health = healthMap.get(url);
        if (health == null) {
            return;
        }
        var failures = health.consecutiveFailures.incrementAndGet();
        health.lastFailureTime.set(clock.instant());

        if (failures >= MAX_CONSECUTIVE_FAILURES && health.healthy.getAndSet(false)) {
            log.warn("RPC URL marked unhealthy after {} consecutive failures: url={}", failures, url);
        }
    }

    boolean hasHealthyUrls() {
        return healthMap.values().stream().anyMatch(h -> h.healthy.get());
    }

    int healthyUrlCount() {
        return (int) healthMap.values().stream().filter(h -> h.healthy.get()).count();
    }

    int totalUrlCount() {
        return urls.size();
    }

    private boolean isAvailable(String url) {
        var health = healthMap.get(url);
        if (health.healthy.get()) {
            return true;
        }
        return isEligibleForHealthProbe(health);
    }

    private boolean isEligibleForHealthProbe(UrlHealth health) {
        var lastFailure = health.lastFailureTime.get();
        if (lastFailure == null) {
            return true;
        }
        var elapsed = Duration.between(lastFailure, clock.instant());
        return elapsed.compareTo(HEALTH_PROBE_INTERVAL) >= 0;
    }

    private static final class UrlHealth {
        final AtomicInteger consecutiveFailures = new AtomicInteger(0);
        final AtomicReference<Instant> lastFailureTime = new AtomicReference<>();
        final AtomicBoolean healthy = new AtomicBoolean(true);
    }
}
