package com.stablebridge.indexer.application.config;

import com.stablebridge.indexer.application.properties.BloomProperties;
import com.stablebridge.indexer.application.properties.ChainProperties;
import com.stablebridge.indexer.application.properties.IndexerProperties;
import com.stablebridge.indexer.domain.model.BloomStatus;
import com.stablebridge.indexer.domain.model.ChainConfiguration;
import com.stablebridge.indexer.domain.model.NetworkType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Configuration that exposes domain-friendly beans derived from application-layer properties,
 * respecting the hexagonal boundary between domain and application layers.
 */
@Configuration
class StatusQueryHandlerConfiguration {

    @Bean
    Map<String, ChainConfiguration> chainConfigurations(IndexerProperties indexerProperties) {
        return mapChainConfigurations(indexerProperties.chains());
    }

    @Bean
    BloomStatus bloomStatus(IndexerProperties indexerProperties) {
        return mapBloomStatus(indexerProperties.bloom());
    }

    private Map<String, ChainConfiguration> mapChainConfigurations(Map<String, ChainProperties> chains) {
        var result = new LinkedHashMap<String, ChainConfiguration>();
        for (var entry : chains.entrySet()) {
            var chainName = entry.getKey();
            var props = entry.getValue();
            result.put(chainName, ChainConfiguration.builder()
                    .chainName(chainName)
                    .networkType(resolveNetworkType(props.type()))
                    .enabled(props.enabled())
                    .build());
        }
        return result;
    }

    private BloomStatus mapBloomStatus(BloomProperties bloom) {
        return BloomStatus.builder()
                .backend(bloom.backend())
                .expectedInsertions(bloom.expectedInsertions())
                .errorRate(bloom.errorRate())
                .networkTypes(Arrays.asList(NetworkType.values()))
                .build();
    }

    private NetworkType resolveNetworkType(String type) {
        return NetworkType.valueOf(type.toUpperCase(Locale.ROOT));
    }
}
