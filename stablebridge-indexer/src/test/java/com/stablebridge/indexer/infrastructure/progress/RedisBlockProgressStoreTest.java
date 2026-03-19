package com.stablebridge.indexer.infrastructure.progress;

import com.stablebridge.indexer.domain.model.ChainId;
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

import java.util.OptionalLong;
import java.util.Set;

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
            given(hashOperations.get(PROGRESS_KEY, ChainId.ETHEREUM.name()))
                    .willReturn("12345678");

            // when
            OptionalLong result = store.getLastProcessedBlock(ChainId.ETHEREUM);

            // then
            assertThat(result).isEqualTo(OptionalLong.of(12345678L));
        }

        @Test
        @DisplayName("should return empty when no progress exists for chain")
        void shouldReturnEmptyWhenNoProgressExists() {
            // given
            given(redisTemplate.opsForHash()).willReturn(hashOperations);
            given(hashOperations.get(PROGRESS_KEY, ChainId.POLYGON.name()))
                    .willReturn(null);

            // when
            OptionalLong result = store.getLastProcessedBlock(ChainId.POLYGON);

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
            store.saveLastProcessedBlock(ChainId.ARBITRUM, 99887766L);

            // then
            then(hashOperations).should().put(PROGRESS_KEY, ChainId.ARBITRUM.name(), "99887766");
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
            store.addFailedBlock(ChainId.BASE, 55555L);

            // then
            then(zSetOperations).should().add(
                    FAILED_KEY_PREFIX + ChainId.BASE.name(),
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
            given(zSetOperations.range(FAILED_KEY_PREFIX + ChainId.OPTIMISM.name(), 0, -1))
                    .willReturn(Set.of("100", "200", "300"));

            // when
            Set<Long> result = store.getFailedBlocks(ChainId.OPTIMISM);

            // then
            assertThat(result).isEqualTo(Set.of(100L, 200L, 300L));
        }

        @Test
        @DisplayName("should return empty set when no failed blocks exist")
        void shouldReturnEmptySetWhenNoFailedBlocks() {
            // given
            given(redisTemplate.opsForZSet()).willReturn(zSetOperations);
            given(zSetOperations.range(FAILED_KEY_PREFIX + ChainId.BSC.name(), 0, -1))
                    .willReturn(null);

            // when
            Set<Long> result = store.getFailedBlocks(ChainId.BSC);

            // then
            assertThat(result).isEqualTo(Set.of());
        }

        @Test
        @DisplayName("should return empty set when sorted set is empty")
        void shouldReturnEmptySetWhenSortedSetIsEmpty() {
            // given
            given(redisTemplate.opsForZSet()).willReturn(zSetOperations);
            given(zSetOperations.range(FAILED_KEY_PREFIX + ChainId.AVALANCHE.name(), 0, -1))
                    .willReturn(Set.of());

            // when
            Set<Long> result = store.getFailedBlocks(ChainId.AVALANCHE);

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
            store.removeFailedBlock(ChainId.ETHEREUM, 42000L);

            // then
            then(zSetOperations).should().remove(
                    FAILED_KEY_PREFIX + ChainId.ETHEREUM.name(),
                    "42000"
            );
        }
    }
}
