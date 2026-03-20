package com.stablebridge.indexer.application.config;

import com.stablebridge.indexer.application.properties.BloomProperties;
import com.stablebridge.indexer.domain.port.WalletAddressRepository;
import com.stablebridge.indexer.infrastructure.bloom.GuavaBloomAddressFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "indexer.bloom.backend", havingValue = "memory")
public class GuavaBloomAutoConfiguration {

    @Bean
    GuavaBloomAddressFilter guavaBloomAddressFilter(
            BloomProperties bloomProperties,
            WalletAddressRepository walletAddressRepository) {
        return new GuavaBloomAddressFilter(
                bloomProperties.expectedInsertions(),
                bloomProperties.errorRate(),
                walletAddressRepository
        );
    }
}
