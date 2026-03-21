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

import java.util.List;

import static com.stablebridge.indexer.domain.model.NetworkType.EVM;
import static com.stablebridge.indexer.domain.model.NetworkType.SOLANA;
import static com.stablebridge.indexer.infrastructure.bloom.RedisBloomAddressFilter.BF_ADD_SCRIPT;
import static com.stablebridge.indexer.infrastructure.bloom.RedisBloomAddressFilter.BF_EXISTS_SCRIPT;
import static com.stablebridge.indexer.infrastructure.bloom.RedisBloomAddressFilter.BF_RESERVE_SCRIPT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("RedisBloomAddressFilter")
class RedisBloomAddressFilterTest {

    private static final String TEST_ADDRESS = "0x742d35Cc6634C0532925a3b844Bc9e7595f2bD18";
    private static final String TEST_ADDRESS_LOWER = TEST_ADDRESS.toLowerCase();
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
            given(redisTemplate.execute(BF_EXISTS_SCRIPT, List.of(BLOOM_KEY), TEST_ADDRESS_LOWER))
                    .willReturn(1L);

            // when
            var result = filter.mightContain(TEST_ADDRESS, TEST_NETWORK);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("returns false when BF.EXISTS returns 0")
        void returnsFalseWhenBloomFilterDoesNotContainAddress() {
            // given
            given(redisTemplate.execute(BF_EXISTS_SCRIPT, List.of(BLOOM_KEY), TEST_ADDRESS_LOWER))
                    .willReturn(0L);

            // when
            var result = filter.mightContain(TEST_ADDRESS, TEST_NETWORK);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("returns false when BF.EXISTS returns null")
        void returnsFalseWhenBloomFilterReturnsNull() {
            // given
            given(redisTemplate.execute(BF_EXISTS_SCRIPT, List.of(BLOOM_KEY), TEST_ADDRESS_LOWER))
                    .willReturn(null);

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
            given(redisTemplate.execute(BF_EXISTS_SCRIPT, List.of(solanaKey), TEST_ADDRESS_LOWER))
                    .willReturn(1L);

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
            given(walletAddressRepository.existsByAddressAndNetworkType(TEST_ADDRESS_LOWER, TEST_NETWORK))
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
            given(walletAddressRepository.existsByAddressAndNetworkType(TEST_ADDRESS_LOWER, TEST_NETWORK))
                    .willReturn(false);

            // when
            var result = filter.contains(TEST_ADDRESS, TEST_NETWORK);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("verifies DB confirmation call with correct parameters")
        void verifiesDbConfirmationCall() {
            // given
            given(walletAddressRepository.existsByAddressAndNetworkType(TEST_ADDRESS_LOWER, TEST_NETWORK))
                    .willReturn(true);

            // when
            filter.contains(TEST_ADDRESS, TEST_NETWORK);

            // then
            then(walletAddressRepository).should()
                    .existsByAddressAndNetworkType(TEST_ADDRESS_LOWER, TEST_NETWORK);
        }
    }

    @Nested
    @DisplayName("add")
    class Add {

        @Test
        @DisplayName("executes BF.ADD Lua script with correct key and address")
        void executesBfAddScript() {
            // given / when
            filter.add(TEST_ADDRESS, TEST_NETWORK);

            // then
            then(redisTemplate).should().execute(BF_ADD_SCRIPT, List.of(BLOOM_KEY), TEST_ADDRESS_LOWER);
        }

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

    @Nested
    @DisplayName("initializeFilters")
    class InitializeFilters {

        @Test
        @DisplayName("sends BF.RESERVE for each network type during initialization")
        void sendsBfReserveForAllNetworkTypes() {
            // given / when
            filter.initializeFilters();

            // then
            then(redisTemplate).should().execute(BF_RESERVE_SCRIPT, List.of("indexer:bloom:EVM"),
                    String.valueOf(ERROR_RATE), String.valueOf(EXPECTED_INSERTIONS));
            then(redisTemplate).should().execute(BF_RESERVE_SCRIPT, List.of("indexer:bloom:SOLANA"),
                    String.valueOf(ERROR_RATE), String.valueOf(EXPECTED_INSERTIONS));
            then(redisTemplate).should().execute(BF_RESERVE_SCRIPT, List.of("indexer:bloom:BITCOIN"),
                    String.valueOf(ERROR_RATE), String.valueOf(EXPECTED_INSERTIONS));
        }

        @Test
        @DisplayName("handles existing bloom filter gracefully without throwing")
        void handlesExistingFilterGracefully() {
            // given
            given(redisTemplate.execute(BF_RESERVE_SCRIPT, List.of("indexer:bloom:EVM"),
                    String.valueOf(ERROR_RATE), String.valueOf(EXPECTED_INSERTIONS)))
                    .willThrow(new RuntimeException("ERR item exists"));
            given(redisTemplate.execute(BF_RESERVE_SCRIPT, List.of("indexer:bloom:SOLANA"),
                    String.valueOf(ERROR_RATE), String.valueOf(EXPECTED_INSERTIONS)))
                    .willThrow(new RuntimeException("ERR item exists"));
            given(redisTemplate.execute(BF_RESERVE_SCRIPT, List.of("indexer:bloom:BITCOIN"),
                    String.valueOf(ERROR_RATE), String.valueOf(EXPECTED_INSERTIONS)))
                    .willThrow(new RuntimeException("ERR item exists"));

            // when / then — no exception propagates
            assertThatCode(() -> filter.initializeFilters()).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("registers bloom size gauge for each network type")
        void registersBloomSizeGaugeForEachNetworkType() {
            // given / when
            filter.initializeFilters();

            // then
            for (var networkType : NetworkType.values()) {
                var gauge = meterRegistry.find("indexer.bloom.size")
                        .tag("networkType", networkType.name())
                        .gauge();
                assertThat(gauge).isNotNull();
            }
        }
    }
}
