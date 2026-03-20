package com.stablebridge.indexer.application.config;

import com.stablebridge.indexer.application.properties.ChainProperties;
import com.stablebridge.indexer.application.properties.IndexerProperties;
import com.stablebridge.indexer.application.properties.TokenContractProperties;
import com.stablebridge.indexer.domain.port.ChainIndexer;
import com.stablebridge.indexer.infrastructure.chain.bitcoin.BitcoinChainConfig;
import com.stablebridge.indexer.infrastructure.chain.bitcoin.BitcoinChainIndexerFactory;
import com.stablebridge.indexer.infrastructure.chain.evm.EvmChainConfig;
import com.stablebridge.indexer.infrastructure.chain.evm.EvmChainIndexerFactory;
import com.stablebridge.indexer.infrastructure.chain.evm.EvmChainTokenConfig;
import com.stablebridge.indexer.infrastructure.chain.solana.SolanaChainConfig;
import com.stablebridge.indexer.infrastructure.chain.solana.SolanaChainIndexerFactory;
import com.stablebridge.indexer.infrastructure.chain.solana.SolanaChainTokenConfig;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.stream.Stream;

import static com.stablebridge.indexer.application.properties.ConfirmationStrategy.FINALIZED;

@Slf4j
@Configuration
class ChainAutoConfiguration {

    private static final String EVM_CHAIN_TYPE = "evm";
    private static final String SOLANA_CHAIN_TYPE = "solana";
    private static final String BITCOIN_CHAIN_TYPE = "bitcoin";

    @Bean
    List<ChainIndexer> chainIndexers(IndexerProperties indexerProperties,
                                      MeterRegistry meterRegistry) {
        var evmIndexers = indexerProperties.chains().entrySet().stream()
                .filter(entry -> entry.getValue().enabled())
                .filter(entry -> EVM_CHAIN_TYPE.equals(entry.getValue().type()))
                .map(entry -> createEvmChainIndexer(entry.getKey(), entry.getValue(), meterRegistry));

        var solanaIndexers = indexerProperties.chains().entrySet().stream()
                .filter(entry -> entry.getValue().enabled())
                .filter(entry -> SOLANA_CHAIN_TYPE.equals(entry.getValue().type()))
                .map(entry -> createSolanaChainIndexer(entry.getKey(), entry.getValue()));

        var bitcoinIndexers = indexerProperties.chains().entrySet().stream()
                .filter(entry -> entry.getValue().enabled())
                .filter(entry -> BITCOIN_CHAIN_TYPE.equals(entry.getValue().type()))
                .map(entry -> createBitcoinChainIndexer(entry.getKey(), entry.getValue()));

        var indexers = Stream.of(evmIndexers, solanaIndexers, bitcoinIndexers)
                .flatMap(s -> s)
                .toList();

        log.info("Auto-configured {} chain indexers", indexers.size());
        return indexers;
    }

    private static ChainIndexer createEvmChainIndexer(String networkId,
                                                       ChainProperties chainProperties,
                                                       MeterRegistry meterRegistry) {
        var config = toEvmChainConfig(networkId, chainProperties);
        return EvmChainIndexerFactory.create(config, meterRegistry);
    }

    private static ChainIndexer createSolanaChainIndexer(String networkId,
                                                          ChainProperties chainProperties) {
        var config = toSolanaChainConfig(networkId, chainProperties);
        return SolanaChainIndexerFactory.create(config);
    }

    private static ChainIndexer createBitcoinChainIndexer(String networkId,
                                                            ChainProperties chainProperties) {
        var config = toBitcoinChainConfig(networkId, chainProperties);
        return BitcoinChainIndexerFactory.create(config);
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
                .tokenContracts(mapEvmTokenContracts(chainProperties.tokenContracts()))
                .build();
    }

    static SolanaChainConfig toSolanaChainConfig(String networkId,
                                                  ChainProperties chainProperties) {
        var rpc = chainProperties.rpc();
        return SolanaChainConfig.builder()
                .networkId(networkId)
                .rpcUrl(rpc.urls().getFirst())
                .rpcTimeout(rpc.timeout())
                .indexNativeTransfers(chainProperties.indexNativeTransfers())
                .nativeDecimals(chainProperties.nativeDecimals())
                .tokenContracts(mapSolanaTokenContracts(chainProperties.tokenContracts()))
                .build();
    }

    static BitcoinChainConfig toBitcoinChainConfig(String networkId,
                                                            ChainProperties chainProperties) {
        var rpc = chainProperties.rpc();
        return BitcoinChainConfig.builder()
                .networkId(networkId)
                .rpcUrl(rpc.urls().getFirst())
                .rpcUsername(rpc.username())
                .rpcPassword(rpc.password())
                .rpcTimeout(rpc.timeout())
                .minConfirmations(chainProperties.minConfirmations())
                .build();
    }

    private static List<EvmChainTokenConfig> mapEvmTokenContracts(List<TokenContractProperties> tokenContracts) {
        return tokenContracts.stream()
                .map(tc -> EvmChainTokenConfig.builder()
                        .address(tc.address())
                        .symbol(tc.symbol())
                        .decimals(tc.decimals())
                        .build())
                .toList();
    }

    private static List<SolanaChainTokenConfig> mapSolanaTokenContracts(List<TokenContractProperties> tokenContracts) {
        return tokenContracts.stream()
                .map(tc -> SolanaChainTokenConfig.builder()
                        .mintAddress(tc.address())
                        .symbol(tc.symbol())
                        .decimals(tc.decimals())
                        .build())
                .toList();
    }
}
