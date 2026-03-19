package com.stablebridge.indexer.infrastructure.progress;

import com.stablebridge.indexer.domain.model.ChainId;
import com.stablebridge.indexer.domain.port.BlockProgressStore;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.OptionalLong;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Redis-backed implementation of {@link BlockProgressStore}.
 *
 * <p>Uses a Redis Hash ({@code indexer:progress}) for O(1) lookups of the last processed block
 * per chain, and Redis Sorted Sets ({@code indexer:failed:<chainId>}) for ordered tracking of
 * failed blocks that need to be retried by the rescan worker.
 *
 * <p>Redis is the sole source of truth for block progress (Architecture Decision 5).
 * If Redis is unavailable on restart, workers simply re-index from the last known position —
 * all downstream processing is idempotent.
 */
@Component
@RequiredArgsConstructor
public class RedisBlockProgressStore implements BlockProgressStore {

    static final String PROGRESS_KEY = "indexer:progress";
    static final String FAILED_KEY_PREFIX = "indexer:failed:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public OptionalLong getLastProcessedBlock(ChainId chainId) {
        Object value = redisTemplate.opsForHash().get(PROGRESS_KEY, chainId.name());
        if (value == null) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(Long.parseLong(value.toString()));
    }

    @Override
    public void saveLastProcessedBlock(ChainId chainId, long blockNumber) {
        redisTemplate.opsForHash().put(PROGRESS_KEY, chainId.name(), String.valueOf(blockNumber));
    }

    @Override
    public void addFailedBlock(ChainId chainId, long blockNumber) {
        redisTemplate.opsForZSet().add(failedKey(chainId), String.valueOf(blockNumber), blockNumber);
    }

    @Override
    public Set<Long> getFailedBlocks(ChainId chainId) {
        Set<String> members = redisTemplate.opsForZSet().range(failedKey(chainId), 0, -1);
        if (members == null) {
            return Set.of();
        }
        return members.stream()
                .map(Long::parseLong)
                .collect(Collectors.toSet());
    }

    @Override
    public void removeFailedBlock(ChainId chainId, long blockNumber) {
        redisTemplate.opsForZSet().remove(failedKey(chainId), String.valueOf(blockNumber));
    }

    private String failedKey(ChainId chainId) {
        return FAILED_KEY_PREFIX + chainId.name();
    }
}
