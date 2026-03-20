package com.stablebridge.indexer.infrastructure.progress;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;

import static com.stablebridge.indexer.domain.model.ChainId.ARBITRUM;
import static com.stablebridge.indexer.domain.model.ChainId.AVALANCHE;
import static com.stablebridge.indexer.domain.model.ChainId.BASE;
import static com.stablebridge.indexer.domain.model.ChainId.BSC;
import static com.stablebridge.indexer.domain.model.ChainId.ETHEREUM;
import static com.stablebridge.indexer.domain.model.ChainId.OPTIMISM;
import static com.stablebridge.indexer.domain.model.ChainId.POLYGON;
import static com.stablebridge.indexer.infrastructure.progress.RedisBlockProgressStore.CATCHUP_KEY_PREFIX;
import static com.stablebridge.indexer.infrastructure.progress.RedisBlockProgressStore.FAILED_KEY_PREFIX;
import static com.stablebridge.indexer.infrastructure.progress.RedisBlockProgressStore.PROGRESS_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("RedisBlockProgressStore")
class RedisBlockProgressStoreTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @InjectMocks
    private RedisBlockProgressStore store;

    @Nested
    @DisplayName("getLastProcessedBlock")
    class GetLastProcessedBlock {

        @Test
        @DisplayName("should return block number when progress exists for chain")
        void shouldReturnBlockNumberWhenProgressExists() {
            // given
            given(redisTemplate.opsForHash()).willReturn(hashOperations);
            given(hashOperations.get(PROGRESS_KEY, ETHEREUM.name()))
                    .willReturn("12345678");

            // when
            var result = store.getLastProcessedBlock(ETHEREUM);

            // then
            assertThat(result).isEqualTo(OptionalLong.of(12345678L));
        }

        @Test
        @DisplayName("should return empty when no progress exists for chain")
        void shouldReturnEmptyWhenNoProgressExists() {
            // given
            given(redisTemplate.opsForHash()).willReturn(hashOperations);
            given(hashOperations.get(PROGRESS_KEY, POLYGON.name()))
                    .willReturn(null);

            // when
            var result = store.getLastProcessedBlock(POLYGON);

            // then
            assertThat(result).isEqualTo(OptionalLong.empty());
        }
    }

    @Nested
    @DisplayName("saveLastProcessedBlock")
    class SaveLastProcessedBlock {

        @Test
        @DisplayName("should save block number to Redis hash")
        void shouldSaveBlockNumberToRedisHash() {
            // given
            given(redisTemplate.opsForHash()).willReturn(hashOperations);

            // when
            store.saveLastProcessedBlock(ARBITRUM, 99887766L);

            // then
            then(hashOperations).should().put(PROGRESS_KEY, ARBITRUM.name(), "99887766");
        }
    }

    @Nested
    @DisplayName("addFailedBlock")
    class AddFailedBlock {

        @Test
        @DisplayName("should add block number to sorted set with block number as score")
        void shouldAddBlockNumberToSortedSet() {
            // given
            given(redisTemplate.opsForZSet()).willReturn(zSetOperations);

            // when
            store.addFailedBlock(BASE, 55555L);

            // then
            then(zSetOperations).should().add(
                    FAILED_KEY_PREFIX + BASE.name(),
                    "55555",
                    55555.0
            );
        }
    }

    @Nested
    @DisplayName("getFailedBlocks")
    class GetFailedBlocks {

        @Test
        @DisplayName("should return failed block numbers from sorted set")
        void shouldReturnFailedBlockNumbers() {
            // given
            given(redisTemplate.opsForZSet()).willReturn(zSetOperations);
            given(zSetOperations.range(FAILED_KEY_PREFIX + OPTIMISM.name(), 0, -1))
                    .willReturn(Set.of("100", "200", "300"));

            // when
            var result = store.getFailedBlocks(OPTIMISM);

            // then
            assertThat(result).isEqualTo(Set.of(100L, 200L, 300L));
        }

        @Test
        @DisplayName("should return empty set when no failed blocks exist")
        void shouldReturnEmptySetWhenNoFailedBlocks() {
            // given
            given(redisTemplate.opsForZSet()).willReturn(zSetOperations);
            given(zSetOperations.range(FAILED_KEY_PREFIX + BSC.name(), 0, -1))
                    .willReturn(null);

            // when
            var result = store.getFailedBlocks(BSC);

            // then
            assertThat(result).isEqualTo(Set.of());
        }

        @Test
        @DisplayName("should return empty set when sorted set is empty")
        void shouldReturnEmptySetWhenSortedSetIsEmpty() {
            // given
            given(redisTemplate.opsForZSet()).willReturn(zSetOperations);
            given(zSetOperations.range(FAILED_KEY_PREFIX + AVALANCHE.name(), 0, -1))
                    .willReturn(Set.of());

            // when
            var result = store.getFailedBlocks(AVALANCHE);

            // then
            assertThat(result).isEqualTo(Set.of());
        }
    }

    @Nested
    @DisplayName("removeFailedBlock")
    class RemoveFailedBlock {

        @Test
        @DisplayName("should remove block number from sorted set")
        void shouldRemoveBlockNumberFromSortedSet() {
            // given
            given(redisTemplate.opsForZSet()).willReturn(zSetOperations);

            // when
            store.removeFailedBlock(ETHEREUM, 42000L);

            // then
            then(zSetOperations).should().remove(
                    FAILED_KEY_PREFIX + ETHEREUM.name(),
                    "42000"
            );
        }
    }

    @Nested
    @DisplayName("saveCatchupRange")
    class SaveCatchupRange {

        @Test
        @DisplayName("should save catchup range to Redis hash")
        void shouldSaveCatchupRangeToRedisHash() {
            // given
            given(redisTemplate.opsForHash()).willReturn(hashOperations);

            // when
            store.saveCatchupRange(POLYGON, 1000L, 2000L);

            // then
            then(hashOperations).should().put(
                    CATCHUP_KEY_PREFIX + POLYGON.name(),
                    "1000",
                    "2000"
            );
        }
    }

    @Nested
    @DisplayName("getCatchupRanges")
    class GetCatchupRanges {

        @Test
        @DisplayName("should return catchup ranges from Redis hash")
        void shouldReturnCatchupRangesFromRedisHash() {
            // given
            given(redisTemplate.opsForHash()).willReturn(hashOperations);
            given(hashOperations.entries(CATCHUP_KEY_PREFIX + ARBITRUM.name()))
                    .willReturn(Map.of("500", "600", "700", "800"));

            // when
            var result = store.getCatchupRanges(ARBITRUM);

            // then
            assertThat(result).isEqualTo(Map.of(500L, 600L, 700L, 800L));
        }

        @Test
        @DisplayName("should return empty map when no catchup ranges exist")
        void shouldReturnEmptyMapWhenNoCatchupRangesExist() {
            // given
            given(redisTemplate.opsForHash()).willReturn(hashOperations);
            given(hashOperations.entries(CATCHUP_KEY_PREFIX + BASE.name()))
                    .willReturn(Map.of());

            // when
            var result = store.getCatchupRanges(BASE);

            // then
            assertThat(result).isEqualTo(Map.of());
        }
    }

    @Nested
    @DisplayName("removeCatchupRange")
    class RemoveCatchupRange {

        @Test
        @DisplayName("should remove catchup range from Redis hash")
        void shouldRemoveCatchupRangeFromRedisHash() {
            // given
            given(redisTemplate.opsForHash()).willReturn(hashOperations);

            // when
            store.removeCatchupRange(AVALANCHE, 3000L);

            // then
            then(hashOperations).should().delete(
                    CATCHUP_KEY_PREFIX + AVALANCHE.name(),
                    "3000"
            );
        }
    }
}
