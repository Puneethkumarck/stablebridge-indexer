package com.stablebridge.indexer.infrastructure.bloom;

import com.stablebridge.indexer.domain.model.NetworkType;
import com.stablebridge.indexer.domain.port.AddressFilter;
import com.stablebridge.indexer.domain.port.WalletAddressRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;

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
 */
@Slf4j
@RequiredArgsConstructor
public class RedisBloomAddressFilter implements AddressFilter {

    static final String KEY_PREFIX = "indexer:bloom:";

    private final StringRedisTemplate redisTemplate;
    private final long expectedInsertions;
    private final double errorRate;
    private final WalletAddressRepository walletAddressRepository;

    @PostConstruct
    void initializeFilters() {
        for (NetworkType networkType : NetworkType.values()) {
            initializeFilter(networkType);
        }
        log.info("Initialized Redis bloom filters for all network types — expectedInsertions={}, errorRate={}",
                expectedInsertions, errorRate);
    }

    @Override
    public boolean mightContain(String address, NetworkType networkType) {
        String key = bloomKey(networkType);
        Boolean result = redisTemplate.execute((RedisCallback<Boolean>) connection -> {
            Object rawResult = connection.commands().execute(
                    "BF.EXISTS",
                    key.getBytes(StandardCharsets.UTF_8),
                    address.getBytes(StandardCharsets.UTF_8)
            );
            return parseBooleanReply(rawResult);
        });
        return Boolean.TRUE.equals(result);
    }

    @Override
    public boolean contains(String address, NetworkType networkType) {
        return walletAddressRepository.existsByAddressAndNetworkType(address, networkType);
    }

    @Override
    public void add(String address, NetworkType networkType) {
        String key = bloomKey(networkType);
        redisTemplate.execute((RedisCallback<Boolean>) connection -> {
            Object rawResult = connection.commands().execute(
                    "BF.ADD",
                    key.getBytes(StandardCharsets.UTF_8),
                    address.getBytes(StandardCharsets.UTF_8)
            );
            return parseBooleanReply(rawResult);
        });
        log.debug("Added address to Redis bloom filter — networkType={}, address={}", networkType, address);
    }

    @Override
    public void remove(String address, NetworkType networkType) {
        log.warn("Bloom filters do not support removal — address will remain in bloom filter until rebuild. "
                + "networkType={}, address={}", networkType, address);
    }

    private void initializeFilter(NetworkType networkType) {
        String key = bloomKey(networkType);
        try {
            redisTemplate.execute((RedisCallback<Object>) connection ->
                    connection.commands().execute(
                            "BF.RESERVE",
                            key.getBytes(StandardCharsets.UTF_8),
                            String.valueOf(errorRate).getBytes(StandardCharsets.UTF_8),
                            String.valueOf(expectedInsertions).getBytes(StandardCharsets.UTF_8)
                    )
            );
            log.info("Reserved bloom filter — key={}, errorRate={}, expectedInsertions={}",
                    key, errorRate, expectedInsertions);
        } catch (Exception e) {
            log.debug("Bloom filter already exists or could not be reserved — key={}, reason={}",
                    key, e.getMessage());
        }
    }

    private String bloomKey(NetworkType networkType) {
        return KEY_PREFIX + networkType.name();
    }

    private Boolean parseBooleanReply(Object rawResult) {
        if (rawResult == null) {
            return false;
        }
        if (rawResult instanceof Long longValue) {
            return longValue == 1L;
        }
        if (rawResult instanceof byte[] bytes) {
            if (bytes.length == 1) {
                return bytes[0] == '1' || bytes[0] == 1;
            }
            String reply = new String(bytes, StandardCharsets.UTF_8);
            return "1".equals(reply);
        }
        return "1".equals(rawResult.toString());
    }
}
