package com.stablebridge.indexer.client;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnProperty(name = "indexer.client.url")
@EnableConfigurationProperties(IndexerClientProperties.class)
@EnableFeignClients(clients = IndexerClient.class)
public class IndexerClientAutoConfiguration {

    @Bean
    public ApiKeyRequestInterceptor apiKeyRequestInterceptor(
            IndexerClientProperties properties) {
        return new ApiKeyRequestInterceptor(properties.apiKey());
    }
}
