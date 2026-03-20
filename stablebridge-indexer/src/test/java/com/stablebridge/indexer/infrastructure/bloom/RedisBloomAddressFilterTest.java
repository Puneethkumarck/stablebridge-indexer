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
import org.springframework.data.redis.connection.RedisCommands;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;

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
    private static final byte[] BLOOM_KEY_BYTES = BLOOM_KEY.getBytes(StandardCharsets.UTF_8);
    private static final byte[] ADDRESS_BYTES = TEST_ADDRESS.getBytes(StandardCharsets.UTF_8);
    private static final long EXPECTED_INSERTIONS = 1_000_000L;
    private static final double ERROR_RATE = 0.001;

    @Mock
    private RedisConnectionFactory connectionFactory;

    @Mock
    private RedisConnection redisConnection;

    @Mock
    private RedisCommands redisCommands;

    @Mock
    private WalletAddressRepository walletAddressRepository;

    private StringRedisTemplate redisTemplate;
    private MeterRegistry meterRegistry;
    private RedisBloomAddressFilter filter;

    @BeforeEach
    void setUp() {
        lenient().when(connectionFactory.getConnection()).thenReturn(redisConnection);
        lenient().when(redisConnection.commands()).thenReturn(redisCommands);
        meterRegistry = new SimpleMeterRegistry();
        redisTemplate = new StringRedisTemplate();
        redisTemplate.setConnectionFactory(connectionFactory);
        redisTemplate.afterPropertiesSet();
        filter = new RedisBloomAddressFilter(
                redisTemplate, EXPECTED_INSERTIONS, ERROR_RATE, walletAddressRepository, meterRegistry);
    }

    @Nested
    @DisplayName("mightContain")
    class MightContain {

        @Test
        @DisplayName("returns true when BF.EXISTS returns 1")
        void returnsTrueWhenBloomFilterContainsAddress() {
            given(redisCommands.execute("BF.EXISTS", BLOOM_KEY_BYTES, ADDRESS_BYTES))
                    .willReturn(1L);

            boolean result = filter.mightContain(TEST_ADDRESS, TEST_NETWORK);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("returns false when BF.EXISTS returns 0")
        void returnsFalseWhenBloomFilterDoesNotContainAddress() {
            given(redisCommands.execute("BF.EXISTS", BLOOM_KEY_BYTES, ADDRESS_BYTES))
                    .willReturn(0L);

            boolean result = filter.mightContain(TEST_ADDRESS, TEST_NETWORK);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("returns false when BF.EXISTS returns null")
        void returnsFalseWhenBloomFilterReturnsNull() {
            given(redisCommands.execute("BF.EXISTS", BLOOM_KEY_BYTES, ADDRESS_BYTES))
                    .willReturn(null);

            boolean result = filter.mightContain(TEST_ADDRESS, TEST_NETWORK);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("sends BF.EXISTS command with correct key and address")
        void sendsCorrectCommand() {
            given(redisCommands.execute("BF.EXISTS", BLOOM_KEY_BYTES, ADDRESS_BYTES))
                    .willReturn(1L);

            filter.mightContain(TEST_ADDRESS, TEST_NETWORK);

            then(redisCommands).should().execute("BF.EXISTS", BLOOM_KEY_BYTES, ADDRESS_BYTES);
        }

        @Test
        @DisplayName("returns true when BF.EXISTS returns byte array with value 1")
        void returnsTrueWhenBloomFilterReturnsByteArray() {
            given(redisCommands.execute("BF.EXISTS", BLOOM_KEY_BYTES, ADDRESS_BYTES))
                    .willReturn(new byte[]{1});

            boolean result = filter.mightContain(TEST_ADDRESS, TEST_NETWORK);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("uses correct key for SOLANA network type")
        void usesCorrectKeyForSolanaNetworkType() {
            String solanaKey = "indexer:bloom:SOLANA";
            byte[] solanaKeyBytes = solanaKey.getBytes(StandardCharsets.UTF_8);
            given(redisCommands.execute("BF.EXISTS", solanaKeyBytes, ADDRESS_BYTES))
                    .willReturn(1L);

            boolean result = filter.mightContain(TEST_ADDRESS, SOLANA);

            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("contains")
    class Contains {

        @Test
        @DisplayName("delegates to WalletAddressRepository and returns true when address exists")
        void delegatesToRepositoryWhenExists() {
            given(walletAddressRepository.existsByAddressAndNetworkType(TEST_ADDRESS, TEST_NETWORK))
                    .willReturn(true);

            boolean result = filter.contains(TEST_ADDRESS, TEST_NETWORK);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("delegates to WalletAddressRepository and returns false when address does not exist")
        void delegatesToRepositoryWhenNotExists() {
            given(walletAddressRepository.existsByAddressAndNetworkType(TEST_ADDRESS, TEST_NETWORK))
                    .willReturn(false);

            boolean result = filter.contains(TEST_ADDRESS, TEST_NETWORK);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("verifies DB confirmation call with correct parameters")
        void verifiesDbConfirmationCall() {
            given(walletAddressRepository.existsByAddressAndNetworkType(TEST_ADDRESS, TEST_NETWORK))
                    .willReturn(true);

            filter.contains(TEST_ADDRESS, TEST_NETWORK);

            then(walletAddressRepository).should()
                    .existsByAddressAndNetworkType(TEST_ADDRESS, TEST_NETWORK);
        }
    }

    @Nested
    @DisplayName("add")
    class Add {

        @Test
        @DisplayName("sends BF.ADD command with correct key and address")
        void sendsBfAddCommand() {
            // given
            given(redisCommands.execute("BF.ADD", BLOOM_KEY_BYTES, ADDRESS_BYTES))
                    .willReturn(1L);

            // when
            filter.add(TEST_ADDRESS, TEST_NETWORK);

            // then
            then(redisCommands).should().execute("BF.ADD", BLOOM_KEY_BYTES, ADDRESS_BYTES);
        }

        @Test
        @DisplayName("increments bloom size gauge when address is added")
        void incrementsBloomSizeGaugeWhenAddressIsAdded() {
            // given
            filter.initializeFilters();
            given(redisCommands.execute("BF.ADD", BLOOM_KEY_BYTES, ADDRESS_BYTES))
                    .willReturn(1L);

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
        @DisplayName("is a no-op and does not interact with Redis")
        void isNoOpAndDoesNotInteractWithRedis() {
            filter.remove(TEST_ADDRESS, TEST_NETWORK);

            then(redisCommands).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("does not interact with wallet address repository")
        void doesNotInteractWithWalletAddressRepository() {
            filter.remove(TEST_ADDRESS, TEST_NETWORK);

            then(walletAddressRepository).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("initializeFilters")
    class InitializeFilters {

        @Test
        @DisplayName("sends BF.RESERVE command for each network type during initialization")
        void sendsBfReserveForAllNetworkTypes() {
            byte[] errorRateBytes = String.valueOf(ERROR_RATE).getBytes(StandardCharsets.UTF_8);
            byte[] expectedInsertionsBytes = String.valueOf(EXPECTED_INSERTIONS).getBytes(StandardCharsets.UTF_8);

            for (NetworkType networkType : NetworkType.values()) {
                String key = "indexer:bloom:" + networkType.name();
                byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
                given(redisCommands.execute("BF.RESERVE", keyBytes, errorRateBytes, expectedInsertionsBytes))
                        .willReturn("OK".getBytes(StandardCharsets.UTF_8));
            }

            filter.initializeFilters();

            for (NetworkType networkType : NetworkType.values()) {
                String key = "indexer:bloom:" + networkType.name();
                byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
                then(redisCommands).should().execute(
                        "BF.RESERVE", keyBytes, errorRateBytes, expectedInsertionsBytes);
            }
        }

        @Test
        @DisplayName("handles existing bloom filter gracefully without throwing")
        void handlesExistingFilterGracefully() {
            byte[] errorRateBytes = String.valueOf(ERROR_RATE).getBytes(StandardCharsets.UTF_8);
            byte[] expectedInsertionsBytes = String.valueOf(EXPECTED_INSERTIONS).getBytes(StandardCharsets.UTF_8);

            for (NetworkType networkType : NetworkType.values()) {
                String key = "indexer:bloom:" + networkType.name();
                byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
                given(redisCommands.execute("BF.RESERVE", keyBytes, errorRateBytes, expectedInsertionsBytes))
                        .willThrow(new RuntimeException("ERR item exists"));
            }

            filter.initializeFilters();

            // No exception should propagate — initialization handles existing filters gracefully
            then(redisCommands).shouldHaveNoMoreInteractions();
        }
    }
}
