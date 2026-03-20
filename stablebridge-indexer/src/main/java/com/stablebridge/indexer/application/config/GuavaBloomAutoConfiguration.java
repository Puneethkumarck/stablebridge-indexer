package com.stablebridge.indexer.application.config;

import com.stablebridge.indexer.application.properties.IndexerProperties;
import com.stablebridge.indexer.domain.port.WalletAddressRepository;
import com.stablebridge.indexer.infrastructure.bloom.GuavaBloomAddressFilter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "indexer.bloom.backend", havingValue = "memory")
public class GuavaBloomAutoConfiguration {

    @Bean
    GuavaBloomAddressFilter guavaBloomAddressFilter(
            IndexerProperties indexerProperties,
            WalletAddressRepository walletAddressRepository,
            MeterRegistry meterRegistry) {
        var bloom = indexerProperties.bloom();
        return new GuavaBloomAddressFilter(
                bloom.expectedInsertions(),
                bloom.errorRate(),
                walletAddressRepository,
                meterRegistry
        );
    }
}
