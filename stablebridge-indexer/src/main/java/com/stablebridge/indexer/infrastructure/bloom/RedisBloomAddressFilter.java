package com.stablebridge.indexer.infrastructure.bloom;

import com.stablebridge.indexer.domain.model.NetworkType;
import com.stablebridge.indexer.domain.port.AddressFilter;
import com.stablebridge.indexer.domain.port.WalletAddressRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Redis Bloom filter implementation of {@link AddressFilter}.
 *
 * <p>Uses Redis Bloom module commands ({@code BF.ADD}, {@code BF.EXISTS}, {@code BF.RESERVE})
 * to provide sub-millisecond probabilistic address matching. Each {@link NetworkType} has its
 * own bloom filter key: {@code indexer:bloom:EVM}, {@code indexer:bloom:SOLANA},
 * {@code indexer:bloom:BITCOIN}.
 *
 * <p>The {@link #contains(String, NetworkType)} method delegates to
 * {@link WalletAddressRepository#existsByAddressAndNetworkType(String, NetworkType)} for
 * DB confirmation, ensuring zero false positives for financial correctness (Decision 4).
 *
 * <p>Bloom filters do not support removal — {@link #remove(String, NetworkType)} is a no-op
 * that logs a warning. A full bloom filter rebuild is required to remove addresses.
 *
 * <p>This class does not depend on application-layer configuration directly. The raw config
 * values ({@code expectedInsertions}, {@code errorRate}) are passed via constructor parameters,
 * with the application-layer {@code RedisBloomAutoConfiguration} bridging the gap.
 *
 * <p>Commands are executed via Lua scripts to ensure compatibility with Lettuce's response
 * handling, since Lettuce's generic {@code execute()} uses {@code ByteArrayOutput} which
 * does not support boolean/integer responses from Redis Bloom module commands.
 */
@Slf4j
@RequiredArgsConstructor
public class RedisBloomAddressFilter implements AddressFilter {

    static final String KEY_PREFIX = "indexer:bloom:";

    static final DefaultRedisScript<Long> BF_ADD_SCRIPT =
            new DefaultRedisScript<>("return redis.call('BF.ADD', KEYS[1], ARGV[1])", Long.class);

    static final DefaultRedisScript<Long> BF_EXISTS_SCRIPT =
            new DefaultRedisScript<>("return redis.call('BF.EXISTS', KEYS[1], ARGV[1])", Long.class);

    static final DefaultRedisScript<Long> BF_RESERVE_SCRIPT =
            new DefaultRedisScript<>(
                    "return redis.call('BF.RESERVE', KEYS[1], ARGV[1], ARGV[2])", Long.class);

    private final StringRedisTemplate redisTemplate;
    private final long expectedInsertions;
    private final double errorRate;
    private final WalletAddressRepository walletAddressRepository;
    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<NetworkType, AtomicLong> bloomSizes = new ConcurrentHashMap<>();

    @PostConstruct
    void initializeFilters() {
        for (var networkType : NetworkType.values()) {
            initializeFilter(networkType);
            var size = bloomSizes.computeIfAbsent(networkType, nt -> new AtomicLong(0));
            Gauge.builder("indexer.bloom.size", size, AtomicLong::get)
                    .tag("networkType", networkType.name())
                    .register(meterRegistry);
        }
        log.info("Initialized Redis bloom filters for all network types — expectedInsertions={}, errorRate={}",
                expectedInsertions, errorRate);
    }

    @Override
    public boolean mightContain(String address, NetworkType networkType) {
        var key = bloomKey(networkType);
        var normalizedAddress = normalizeAddress(address, networkType);
        var result = redisTemplate.execute(BF_EXISTS_SCRIPT, List.of(key), normalizedAddress);
        return result != null && result == 1L;
    }

    @Override
    public boolean contains(String address, NetworkType networkType) {
        return walletAddressRepository.existsByAddressAndNetworkType(
                normalizeAddress(address, networkType), networkType);
    }

    @Override
    public void add(String address, NetworkType networkType) {
        var key = bloomKey(networkType);
        var normalizedAddress = normalizeAddress(address, networkType);
        redisTemplate.execute(BF_ADD_SCRIPT, List.of(key), normalizedAddress);
        bloomSizes.computeIfAbsent(networkType, nt -> new AtomicLong(0)).incrementAndGet();
        log.debug("Added address to Redis bloom filter — networkType={}, address={}", networkType, address);
    }

    @Override
    public void remove(String address, NetworkType networkType) {
        log.warn("Bloom filters do not support removal — address will remain in bloom filter until rebuild. "
                + "networkType={}, address={}", networkType, address);
    }

    private void initializeFilter(NetworkType networkType) {
        var key = bloomKey(networkType);
        try {
            redisTemplate.execute(BF_RESERVE_SCRIPT, List.of(key),
                    String.valueOf(errorRate), String.valueOf(expectedInsertions));
            log.info("Reserved bloom filter — key={}, errorRate={}, expectedInsertions={}",
                    key, errorRate, expectedInsertions);
        } catch (Exception e) {
            log.debug("Bloom filter already exists or could not be reserved — key={}, reason={}",
                    key, e.getMessage());
        }
    }

    private static String normalizeAddress(String address, NetworkType networkType) {
        return networkType == NetworkType.EVM ? address.toLowerCase() : address;
    }

    private String bloomKey(NetworkType networkType) {
        return KEY_PREFIX + networkType.name();
    }
}
