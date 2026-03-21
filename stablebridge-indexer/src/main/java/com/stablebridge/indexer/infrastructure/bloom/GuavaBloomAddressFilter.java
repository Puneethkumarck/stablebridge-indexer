package com.stablebridge.indexer.infrastructure.bloom;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import com.stablebridge.indexer.domain.model.NetworkType;
import com.stablebridge.indexer.domain.port.AddressFilter;
import com.stablebridge.indexer.domain.port.WalletAddressRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory Guava-based implementation of {@link AddressFilter} for local development
 * and environments where RedisBloom is not available.
 *
 * <p>Uses {@link BloomFilter} from Google Guava with per-{@link NetworkType} filters
 * stored in a {@link ConcurrentHashMap}. The bloom filter provides sub-millisecond
 * probabilistic lookups; positive results must always be confirmed against the database
 * via {@link WalletAddressRepository#existsByAddressAndNetworkType(String, NetworkType)}.
 *
 * <p>Activated when {@code indexer.bloom.backend=memory} via
 * {@code GuavaBloomAutoConfiguration} in the application layer.
 *
 * <p>Limitations:
 * <ul>
 *   <li>Bloom filters do not support element removal. {@link #remove(String, NetworkType)}
 *       logs a warning and is a no-op on the bloom filter itself.</li>
 *   <li>Filter state is lost on application restart (in-memory only).</li>
 * </ul>
 */
@Slf4j
@RequiredArgsConstructor
public class GuavaBloomAddressFilter implements AddressFilter {

    private final long expectedInsertions;
    private final double errorRate;
    private final WalletAddressRepository walletAddressRepository;
    private final MeterRegistry meterRegistry;

    private final ConcurrentHashMap<NetworkType, BloomFilter<String>> filters = new ConcurrentHashMap<>();

    @PostConstruct
    void initialize() {
        for (NetworkType networkType : NetworkType.values()) {
            var filter = filters.computeIfAbsent(networkType, this::createBloomFilter);
            Gauge.builder("indexer.bloom.size", filter, BloomFilter::approximateElementCount)
                    .tag("networkType", networkType.name())
                    .register(meterRegistry);
        }
        log.info("Initialized Guava bloom filters for all network types — expectedInsertions={}, errorRate={}",
                expectedInsertions, errorRate);
    }

    @Override
    public boolean mightContain(String address, NetworkType networkType) {
        return getOrCreateFilter(networkType).mightContain(normalizeAddress(address, networkType));
    }

    @Override
    public boolean contains(String address, NetworkType networkType) {
        return walletAddressRepository.existsByAddressAndNetworkType(
                normalizeAddress(address, networkType), networkType);
    }

    @Override
    public void add(String address, NetworkType networkType) {
        getOrCreateFilter(networkType).put(normalizeAddress(address, networkType));
        log.debug("Added address to Guava bloom filter — networkType={}, address={}", networkType, address);
    }

    @Override
    public void remove(String address, NetworkType networkType) {
        log.warn("Bloom filters do not support removal — address will remain in bloom filter until rebuild. "
                + "networkType={}, address={}", networkType, address);
    }

    private BloomFilter<String> getOrCreateFilter(NetworkType networkType) {
        return filters.computeIfAbsent(networkType, this::createBloomFilter);
    }

    private static String normalizeAddress(String address, NetworkType networkType) {
        return networkType == NetworkType.EVM ? address.toLowerCase() : address;
    }

    @SuppressWarnings("UnstableApiUsage")
    private BloomFilter<String> createBloomFilter(NetworkType networkType) {
        return BloomFilter.create(
                Funnels.stringFunnel(StandardCharsets.UTF_8),
                expectedInsertions,
                errorRate
        );
    }
}
