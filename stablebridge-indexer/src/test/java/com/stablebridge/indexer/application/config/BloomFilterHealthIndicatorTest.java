package com.stablebridge.indexer.application.config;

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
import java.util.concurrent.atomic.AtomicLong;

import static com.stablebridge.indexer.application.config.BloomFilterHealthIndicator.BLOOM_SIZE_METRIC;
import static com.stablebridge.indexer.application.config.BloomFilterHealthIndicator.PROBE_ADDRESS;
import static com.stablebridge.indexer.domain.model.NetworkType.BITCOIN;
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
        given(addressFilter.mightContain(PROBE_ADDRESS, EVM)).willReturn(false);
        given(addressFilter.mightContain(PROBE_ADDRESS, SOLANA)).willReturn(false);
        given(addressFilter.mightContain(PROBE_ADDRESS, BITCOIN)).willReturn(false);

        registerBloomSizeGauge(EVM.name(), 150);
        registerBloomSizeGauge(SOLANA.name(), 50);
        registerBloomSizeGauge(BITCOIN.name(), 0);

        var expected = Health.up()
                .withDetail("filters", buildExpectedFilters(
                        filterDetail(true, 150L),
                        filterDetail(true, 50L),
                        filterDetail(true, 0L)))
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
        given(addressFilter.mightContain(PROBE_ADDRESS, EVM)).willReturn(false);
        given(addressFilter.mightContain(PROBE_ADDRESS, SOLANA))
                .willThrow(new RuntimeException("Redis connection refused"));
        given(addressFilter.mightContain(PROBE_ADDRESS, BITCOIN)).willReturn(false);

        registerBloomSizeGauge(EVM.name(), 100);
        registerBloomSizeGauge(BITCOIN.name(), 0);

        var expected = Health.down()
                .withDetail("filters", buildExpectedFilters(
                        filterDetail(true, 100L),
                        filterDetail(false, 0L),
                        filterDetail(true, 0L)))
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
        given(addressFilter.mightContain(PROBE_ADDRESS, EVM)).willReturn(false);
        given(addressFilter.mightContain(PROBE_ADDRESS, SOLANA)).willReturn(false);
        given(addressFilter.mightContain(PROBE_ADDRESS, BITCOIN)).willReturn(false);

        var expected = Health.up()
                .withDetail("filters", buildExpectedFilters(
                        filterDetail(true, 0L),
                        filterDetail(true, 0L),
                        filterDetail(true, 0L)))
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
        given(addressFilter.mightContain(PROBE_ADDRESS, EVM))
                .willThrow(new RuntimeException("Redis down"));
        given(addressFilter.mightContain(PROBE_ADDRESS, SOLANA))
                .willThrow(new RuntimeException("Redis down"));
        given(addressFilter.mightContain(PROBE_ADDRESS, BITCOIN))
                .willThrow(new RuntimeException("Redis down"));

        var expected = Health.down()
                .withDetail("filters", buildExpectedFilters(
                        filterDetail(false, 0L),
                        filterDetail(false, 0L),
                        filterDetail(false, 0L)))
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

    private static Map<String, Map<String, Object>> buildExpectedFilters(
            Map<String, Object> evmDetail,
            Map<String, Object> solanaDetail,
            Map<String, Object> bitcoinDetail) {
        var filters = new LinkedHashMap<String, Map<String, Object>>();
        filters.put("EVM", evmDetail);
        filters.put("SOLANA", solanaDetail);
        filters.put("BITCOIN", bitcoinDetail);
        return filters;
    }

    private static Map<String, Object> filterDetail(boolean available, long approximateEntryCount) {
        var detail = new LinkedHashMap<String, Object>();
        detail.put("available", available);
        detail.put("approximateEntryCount", approximateEntryCount);
        return detail;
    }
}
