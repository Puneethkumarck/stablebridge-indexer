package com.stablebridge.indexer.application.config;

import com.stablebridge.indexer.domain.model.NetworkType;
import com.stablebridge.indexer.domain.port.AddressFilter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.health.contributor.Health;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static com.stablebridge.indexer.application.config.BloomFilterHealthIndicator.BLOOM_SIZE_METRIC;
import static com.stablebridge.indexer.application.config.BloomFilterHealthIndicator.PROBE_ADDRESS;
import static com.stablebridge.indexer.domain.model.NetworkType.EVM;
import static com.stablebridge.indexer.domain.model.NetworkType.SOLANA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("BloomFilterHealthIndicator")
class BloomFilterHealthIndicatorTest {

    @Mock
    private AddressFilter addressFilter;

    private SimpleMeterRegistry meterRegistry;

    private BloomFilterHealthIndicator healthIndicator;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        healthIndicator = new BloomFilterHealthIndicator(addressFilter, meterRegistry);
    }

    @Test
    @DisplayName("reports UP when all bloom filters are available")
    void reportsUpWhenAllFiltersAvailable() {
        // given
        for (var networkType : NetworkType.values()) {
            given(addressFilter.mightContain(PROBE_ADDRESS, networkType)).willReturn(false);
        }

        var gaugeSizes = Map.of(EVM, 150L, SOLANA, 50L);
        gaugeSizes.forEach((nt, size) -> registerBloomSizeGauge(nt.name(), size));

        var expectedFilters = new LinkedHashMap<String, Map<String, Object>>();
        for (var networkType : NetworkType.values()) {
            var size = gaugeSizes.getOrDefault(networkType, 0L);
            expectedFilters.put(networkType.name(), filterDetail(true, size));
        }

        var expected = Health.up()
                .withDetail("filters", expectedFilters)
                .build();

        // when
        var actual = healthIndicator.health();

        // then
        assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    @DisplayName("reports DOWN when a bloom filter is unavailable")
    void reportsDownWhenFilterUnavailable() {
        // given
        var unavailable = Set.of(SOLANA);
        for (var networkType : NetworkType.values()) {
            if (unavailable.contains(networkType)) {
                given(addressFilter.mightContain(PROBE_ADDRESS, networkType))
                        .willThrow(new RuntimeException("Redis connection refused"));
            } else {
                given(addressFilter.mightContain(PROBE_ADDRESS, networkType)).willReturn(false);
            }
        }

        var gaugeSizes = Map.of(EVM, 100L);
        gaugeSizes.forEach((nt, size) -> registerBloomSizeGauge(nt.name(), size));

        var expectedFilters = new LinkedHashMap<String, Map<String, Object>>();
        for (var networkType : NetworkType.values()) {
            var available = !unavailable.contains(networkType);
            var size = available ? gaugeSizes.getOrDefault(networkType, 0L) : 0L;
            expectedFilters.put(networkType.name(), filterDetail(available, size));
        }

        var expected = Health.down()
                .withDetail("filters", expectedFilters)
                .build();

        // when
        var actual = healthIndicator.health();

        // then
        assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    @DisplayName("reports entry count as zero when gauge is not registered")
    void reportsZeroEntryCountWhenNoGauge() {
        // given
        for (var networkType : NetworkType.values()) {
            given(addressFilter.mightContain(PROBE_ADDRESS, networkType)).willReturn(false);
        }

        var expectedFilters = new LinkedHashMap<String, Map<String, Object>>();
        for (var networkType : NetworkType.values()) {
            expectedFilters.put(networkType.name(), filterDetail(true, 0L));
        }

        var expected = Health.up()
                .withDetail("filters", expectedFilters)
                .build();

        // when
        var actual = healthIndicator.health();

        // then
        assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    @DisplayName("reports DOWN when all bloom filters are unavailable")
    void reportsDownWhenAllFiltersUnavailable() {
        // given
        for (var networkType : NetworkType.values()) {
            given(addressFilter.mightContain(PROBE_ADDRESS, networkType))
                    .willThrow(new RuntimeException("Redis down"));
        }

        var expectedFilters = new LinkedHashMap<String, Map<String, Object>>();
        for (var networkType : NetworkType.values()) {
            expectedFilters.put(networkType.name(), filterDetail(false, 0L));
        }

        var expected = Health.down()
                .withDetail("filters", expectedFilters)
                .build();

        // when
        var actual = healthIndicator.health();

        // then
        assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    }

    private void registerBloomSizeGauge(String networkType, long value) {
        var counter = new AtomicLong(value);
        Gauge.builder(BLOOM_SIZE_METRIC, counter, AtomicLong::get)
                .tag("networkType", networkType)
                .register(meterRegistry);
    }

    private static Map<String, Object> filterDetail(boolean available, long approximateEntryCount) {
        var detail = new LinkedHashMap<String, Object>();
        detail.put("available", available);
        detail.put("approximateEntryCount", approximateEntryCount);
        return detail;
    }
}
