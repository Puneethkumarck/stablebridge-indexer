package com.stablebridge.indexer.infrastructure.bloom;

import com.stablebridge.indexer.domain.model.NetworkType;
import com.stablebridge.indexer.domain.port.WalletAddressRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;

import static com.stablebridge.indexer.domain.model.NetworkType.EVM;
import static com.stablebridge.indexer.domain.model.NetworkType.SOLANA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@DisplayName("RedisBloomAddressFilter")
class RedisBloomAddressFilterTest {

    private static final String TEST_ADDRESS = "0x742d35Cc6634C0532925a3b844Bc9e7595f2bD18";
    private static final NetworkType TEST_NETWORK = EVM;
    private static final String BLOOM_KEY = "indexer:bloom:EVM";
    private static final long EXPECTED_INSERTIONS = 1_000_000L;
    private static final double ERROR_RATE = 0.001;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private WalletAddressRepository walletAddressRepository;

    private MeterRegistry meterRegistry;
    private RedisBloomAddressFilter filter;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        filter = new RedisBloomAddressFilter(
                redisTemplate, EXPECTED_INSERTIONS, ERROR_RATE, walletAddressRepository, meterRegistry);
    }

    @Nested
    @DisplayName("mightContain")
    class MightContain {

        @Test
        @DisplayName("returns true when BF.EXISTS returns 1")
        void returnsTrueWhenBloomFilterContainsAddress() {
            // given
            lenient().when(redisTemplate.execute(
                    org.mockito.ArgumentMatchers.<DefaultRedisScript<Long>>any(),
                    org.mockito.ArgumentMatchers.eq(List.of(BLOOM_KEY)),
                    org.mockito.ArgumentMatchers.eq(TEST_ADDRESS)
            )).thenReturn(1L);

            // when
            var result = filter.mightContain(TEST_ADDRESS, TEST_NETWORK);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("returns false when BF.EXISTS returns 0")
        void returnsFalseWhenBloomFilterDoesNotContainAddress() {
            // given
            lenient().when(redisTemplate.execute(
                    org.mockito.ArgumentMatchers.<DefaultRedisScript<Long>>any(),
                    org.mockito.ArgumentMatchers.eq(List.of(BLOOM_KEY)),
                    org.mockito.ArgumentMatchers.eq(TEST_ADDRESS)
            )).thenReturn(0L);

            // when
            var result = filter.mightContain(TEST_ADDRESS, TEST_NETWORK);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("returns false when BF.EXISTS returns null")
        void returnsFalseWhenBloomFilterReturnsNull() {
            // given
            lenient().when(redisTemplate.execute(
                    org.mockito.ArgumentMatchers.<DefaultRedisScript<Long>>any(),
                    org.mockito.ArgumentMatchers.eq(List.of(BLOOM_KEY)),
                    org.mockito.ArgumentMatchers.eq(TEST_ADDRESS)
            )).thenReturn(null);

            // when
            var result = filter.mightContain(TEST_ADDRESS, TEST_NETWORK);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("uses correct key for SOLANA network type")
        void usesCorrectKeyForSolanaNetworkType() {
            // given
            var solanaKey = "indexer:bloom:SOLANA";
            lenient().when(redisTemplate.execute(
                    org.mockito.ArgumentMatchers.<DefaultRedisScript<Long>>any(),
                    org.mockito.ArgumentMatchers.eq(List.of(solanaKey)),
                    org.mockito.ArgumentMatchers.eq(TEST_ADDRESS)
            )).thenReturn(1L);

            // when
            var result = filter.mightContain(TEST_ADDRESS, SOLANA);

            // then
            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("contains")
    class Contains {

        @Test
        @DisplayName("delegates to WalletAddressRepository and returns true when address exists")
        void delegatesToRepositoryWhenExists() {
            // given
            given(walletAddressRepository.existsByAddressAndNetworkType(TEST_ADDRESS, TEST_NETWORK))
                    .willReturn(true);

            // when
            var result = filter.contains(TEST_ADDRESS, TEST_NETWORK);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("delegates to WalletAddressRepository and returns false when address does not exist")
        void delegatesToRepositoryWhenNotExists() {
            // given
            given(walletAddressRepository.existsByAddressAndNetworkType(TEST_ADDRESS, TEST_NETWORK))
                    .willReturn(false);

            // when
            var result = filter.contains(TEST_ADDRESS, TEST_NETWORK);

            // then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("add")
    class Add {

        @Test
        @DisplayName("increments bloom size gauge when address is added")
        void incrementsBloomSizeGaugeWhenAddressIsAdded() {
            // given
            filter.initializeFilters();

            // when
            filter.add(TEST_ADDRESS, TEST_NETWORK);

            // then
            var gauge = meterRegistry.find("indexer.bloom.size")
                    .tag("networkType", "EVM")
                    .gauge();
            assertThat(gauge).isNotNull();
            assertThat(gauge.value()).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("remove")
    class Remove {

        @Test
        @DisplayName("is a no-op and does not interact with wallet address repository")
        void isNoOpAndDoesNotInteractWithRepository() {
            // given / when
            filter.remove(TEST_ADDRESS, TEST_NETWORK);

            // then
            then(walletAddressRepository).shouldHaveNoInteractions();
        }
    }
}
