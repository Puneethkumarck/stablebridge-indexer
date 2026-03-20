package com.stablebridge.indexer.application.config;

import com.stablebridge.indexer.domain.model.NetworkType;
import com.stablebridge.indexer.domain.port.AddressFilter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class BloomFilterHealthIndicator implements HealthIndicator {

    static final String BLOOM_SIZE_METRIC = "indexer.bloom.size";
    static final String PROBE_ADDRESS = "0x0000000000000000000000000000000000000000";

    private final AddressFilter addressFilter;
    private final MeterRegistry meterRegistry;

    @Override
    public Health health() {
        var filterDetails = Arrays.stream(NetworkType.values())
                .collect(Collectors.toMap(
                        NetworkType::name,
                        this::buildFilterDetail,
                        (a, b) -> a,
                        LinkedHashMap::new));

        var allAvailable = filterDetails.values().stream()
                .allMatch(detail -> Boolean.TRUE.equals(detail.get("available")));

        if (allAvailable) {
            return Health.up()
                    .withDetail("filters", filterDetails)
                    .build();
        }

        return Health.down()
                .withDetail("filters", filterDetails)
                .build();
    }

    private Map<String, Object> buildFilterDetail(NetworkType networkType) {
        var detail = new LinkedHashMap<String, Object>();

        var available = isFilterAvailable(networkType);
        detail.put("available", available);

        var entryCount = getApproximateEntryCount(networkType);
        detail.put("approximateEntryCount", entryCount);

        return detail;
    }

    private boolean isFilterAvailable(NetworkType networkType) {
        try {
            addressFilter.mightContain(PROBE_ADDRESS, networkType);
            return true;
        } catch (Exception e) {
            log.warn("Bloom filter unavailable for networkType={} — error={}", networkType, e.getMessage());
            return false;
        }
    }

    private long getApproximateEntryCount(NetworkType networkType) {
        var gauge = meterRegistry.find(BLOOM_SIZE_METRIC)
                .tag("networkType", networkType.name())
                .gauge();

        if (gauge == null) {
            return 0L;
        }

        return (long) gauge.value();
    }
}
