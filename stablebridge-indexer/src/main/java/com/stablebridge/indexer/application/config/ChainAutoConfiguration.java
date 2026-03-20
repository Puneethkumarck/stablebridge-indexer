package com.stablebridge.indexer.application.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stablebridge.indexer.application.properties.ChainProperties;
import com.stablebridge.indexer.application.properties.IndexerProperties;
import com.stablebridge.indexer.application.properties.TokenContractProperties;
import com.stablebridge.indexer.domain.port.ChainIndexer;
import com.stablebridge.indexer.infrastructure.chain.evm.EvmChainIndexerFactory;
import com.stablebridge.indexer.infrastructure.chain.evm.EvmChainIndexerFactory.EvmChainConfig;
import com.stablebridge.indexer.infrastructure.chain.evm.EvmChainIndexerFactory.TokenConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static com.stablebridge.indexer.application.properties.ConfirmationStrategy.FINALIZED;

@Slf4j
@Configuration
class ChainAutoConfiguration {

    private static final String EVM_CHAIN_TYPE = "evm";

    @Bean
    List<ChainIndexer> chainIndexers(IndexerProperties indexerProperties,
                                      ObjectMapper objectMapper) {
        var indexers = indexerProperties.chains().entrySet().stream()
                .filter(entry -> entry.getValue().enabled())
                .filter(entry -> EVM_CHAIN_TYPE.equals(entry.getValue().type()))
                .map(entry -> createEvmChainIndexer(entry.getKey(), entry.getValue(), objectMapper))
                .toList();

        log.info("Auto-configured {} EVM chain indexers", indexers.size());
        return indexers;
    }

    private static ChainIndexer createEvmChainIndexer(String networkId,
                                                       ChainProperties chainProperties,
                                                       ObjectMapper objectMapper) {
        var config = toEvmChainConfig(networkId, chainProperties);
        return EvmChainIndexerFactory.create(config, objectMapper);
    }

    static EvmChainConfig toEvmChainConfig(String networkId,
                                                    ChainProperties chainProperties) {
        var rpc = chainProperties.rpc();
        return EvmChainConfig.builder()
                .networkId(networkId)
                .rpcUrl(rpc.urls().getFirst())
                .rpcBatchSize(rpc.batchSize())
                .useBlockReceipts(rpc.useBlockReceipts())
                .rpcTimeout(rpc.timeout())
                .maxRetries(rpc.maxRetries())
                .rateLimitRps(rpc.rateLimitRps())
                .rateLimitBurst(rpc.rateLimitBurst())
                .useFinalizedTag(chainProperties.confirmationStrategy() == FINALIZED)
                .minConfirmations(chainProperties.minConfirmations())
                .indexNativeTransfers(chainProperties.indexNativeTransfers())
                .nativeDecimals(chainProperties.nativeDecimals())
                .tokenContracts(mapTokenContracts(chainProperties.tokenContracts()))
                .build();
    }

    private static List<TokenConfig> mapTokenContracts(List<TokenContractProperties> tokenContracts) {
        return tokenContracts.stream()
                .map(tc -> TokenConfig.builder()
                        .address(tc.address())
                        .symbol(tc.symbol())
                        .decimals(tc.decimals())
                        .build())
                .toList();
    }
}
