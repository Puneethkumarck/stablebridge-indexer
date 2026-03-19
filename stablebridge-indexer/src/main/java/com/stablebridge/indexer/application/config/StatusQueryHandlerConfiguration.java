package com.stablebridge.indexer.application.config;

import com.stablebridge.indexer.application.properties.BloomProperties;
import com.stablebridge.indexer.application.properties.ChainProperties;
import com.stablebridge.indexer.application.properties.IndexerProperties;
import com.stablebridge.indexer.domain.model.BloomStatus;
import com.stablebridge.indexer.domain.model.ChainConfiguration;
import com.stablebridge.indexer.domain.model.NetworkType;
import com.stablebridge.indexer.domain.port.BlockProgressStore;
import com.stablebridge.indexer.domain.service.StatusQueryHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Configuration that wires the domain {@link StatusQueryHandler} with application-layer
 * properties, respecting the hexagonal boundary between domain and application layers.
 */
@Configuration
class StatusQueryHandlerConfiguration {

    @Bean
    StatusQueryHandler statusQueryHandler(
            BlockProgressStore blockProgressStore,
            IndexerProperties indexerProperties
    ) {
        Map<String, ChainConfiguration> chainConfigs = mapChainConfigurations(indexerProperties.chains());
        BloomStatus bloomStatus = mapBloomStatus(indexerProperties.bloom());
        return new StatusQueryHandler(blockProgressStore, chainConfigs, bloomStatus);
    }

    private Map<String, ChainConfiguration> mapChainConfigurations(Map<String, ChainProperties> chains) {
        Map<String, ChainConfiguration> result = new LinkedHashMap<>();
        for (Map.Entry<String, ChainProperties> entry : chains.entrySet()) {
            String chainName = entry.getKey();
            ChainProperties props = entry.getValue();
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
