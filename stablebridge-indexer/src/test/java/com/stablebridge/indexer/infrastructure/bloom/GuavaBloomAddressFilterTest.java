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

import static com.stablebridge.indexer.domain.model.NetworkType.EVM;
import static com.stablebridge.indexer.domain.model.NetworkType.SOLANA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("GuavaBloomAddressFilter")
class GuavaBloomAddressFilterTest {

    private static final String ADDRESS = "0xabcdef1234567890abcdef1234567890abcdef12";
    private static final String UNKNOWN_ADDRESS = "0x0000000000000000000000000000000000000000";
    private static final NetworkType NETWORK_TYPE = EVM;
    private static final long EXPECTED_INSERTIONS = 1_000L;
    private static final double ERROR_RATE = 0.001;

    @Mock
    private WalletAddressRepository walletAddressRepository;

    private MeterRegistry meterRegistry;

    private GuavaBloomAddressFilter filter;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        filter = new GuavaBloomAddressFilter(
                EXPECTED_INSERTIONS, ERROR_RATE, walletAddressRepository, meterRegistry);
        filter.initialize();
    }

    @Nested
    @DisplayName("mightContain")
    class MightContain {

        @Test
        @DisplayName("returns false for address never added")
        void returnsFalseForUnknownAddress() {
            boolean result = filter.mightContain(UNKNOWN_ADDRESS, NETWORK_TYPE);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("returns true for address that was added")
        void returnsTrueForAddedAddress() {
            filter.add(ADDRESS, NETWORK_TYPE);

            boolean result = filter.mightContain(ADDRESS, NETWORK_TYPE);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("filters are scoped per network type")
        void filtersAreScopedPerNetworkType() {
            filter.add(ADDRESS, EVM);

            boolean result = filter.mightContain(ADDRESS, SOLANA);

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("contains")
    class Contains {

        @Test
        @DisplayName("delegates to WalletAddressRepository when address exists")
        void delegatesToRepositoryWhenExists() {
            given(walletAddressRepository.existsByAddressAndNetworkType(ADDRESS, NETWORK_TYPE))
                    .willReturn(true);

            boolean result = filter.contains(ADDRESS, NETWORK_TYPE);

            assertThat(result).isTrue();
            then(walletAddressRepository).should()
                    .existsByAddressAndNetworkType(ADDRESS, NETWORK_TYPE);
        }

        @Test
        @DisplayName("delegates to WalletAddressRepository when address does not exist")
        void delegatesToRepositoryWhenNotExists() {
            given(walletAddressRepository.existsByAddressAndNetworkType(UNKNOWN_ADDRESS, NETWORK_TYPE))
                    .willReturn(false);

            boolean result = filter.contains(UNKNOWN_ADDRESS, NETWORK_TYPE);

            assertThat(result).isFalse();
            then(walletAddressRepository).should()
                    .existsByAddressAndNetworkType(UNKNOWN_ADDRESS, NETWORK_TYPE);
        }
    }

    @Nested
    @DisplayName("add")
    class Add {

        @Test
        @DisplayName("makes address detectable by mightContain")
        void makesAddressDetectable() {
            filter.add(ADDRESS, NETWORK_TYPE);

            boolean result = filter.mightContain(ADDRESS, NETWORK_TYPE);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("supports adding to different network types independently")
        void supportsMultipleNetworkTypes() {
            // given / when
            filter.add(ADDRESS, EVM);
            filter.add(ADDRESS, SOLANA);

            // then
            assertThat(filter.mightContain(ADDRESS, EVM)).isTrue();
            assertThat(filter.mightContain(ADDRESS, SOLANA)).isTrue();
        }

        @Test
        @DisplayName("registers bloom size gauge reflecting approximate element count")
        void registersBloomSizeGaugeReflectingApproximateElementCount() {
            // given
            filter.add(ADDRESS, EVM);

            // when
            var gauge = meterRegistry.find("indexer.bloom.size")
                    .tag("networkType", "EVM")
                    .gauge();

            // then
            assertThat(gauge).isNotNull();
            assertThat(gauge.value()).isGreaterThan(0.0);
        }
    }

    @Nested
    @DisplayName("remove")
    class Remove {

        @Test
        @DisplayName("is a no-op — bloom filters do not support removal")
        void isNoOp() {
            filter.add(ADDRESS, NETWORK_TYPE);

            filter.remove(ADDRESS, NETWORK_TYPE);

            // Bloom filters cannot remove elements, so address is still detectable
            boolean result = filter.mightContain(ADDRESS, NETWORK_TYPE);
            assertThat(result).isTrue();
        }
    }
}
