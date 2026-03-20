package com.stablebridge.indexer.application.config;

import com.stablebridge.indexer.application.properties.IndexerProperties;
import com.stablebridge.indexer.infrastructure.security.ApiKeyAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for API security components.
 *
 * <p>Bridges the gap between application-layer {@link IndexerProperties} and the
 * infrastructure-layer {@link ApiKeyAuthFilter} by passing the API key as a raw value.
 * This ensures the infrastructure layer remains independent of the application layer
 * (hexagonal architecture).
 */
@Configuration
public class SecurityAutoConfiguration {

    @Bean
    ApiKeyAuthFilter apiKeyAuthFilter(IndexerProperties indexerProperties) {
        return new ApiKeyAuthFilter(indexerProperties.api().key());
    }
}
