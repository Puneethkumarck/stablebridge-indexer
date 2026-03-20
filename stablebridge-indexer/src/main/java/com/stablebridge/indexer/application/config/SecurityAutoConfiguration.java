package com.stablebridge.indexer.application.config;

import com.stablebridge.indexer.application.properties.IndexerProperties;
import com.stablebridge.indexer.infrastructure.security.ApiKeyAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class SecurityAutoConfiguration {

    @Bean
    ApiKeyAuthFilter apiKeyAuthFilter(IndexerProperties indexerProperties, ObjectMapper objectMapper) {
        return new ApiKeyAuthFilter(indexerProperties.api().key(), objectMapper);
    }
}
