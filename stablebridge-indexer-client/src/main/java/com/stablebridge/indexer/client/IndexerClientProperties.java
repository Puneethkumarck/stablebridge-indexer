package com.stablebridge.indexer.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "indexer.client")
public record IndexerClientProperties(String url, String apiKey) {}
