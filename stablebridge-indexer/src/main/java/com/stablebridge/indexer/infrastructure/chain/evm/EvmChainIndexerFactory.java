package com.stablebridge.indexer.infrastructure.chain.evm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stablebridge.indexer.domain.model.ChainId;
import com.stablebridge.indexer.domain.port.ChainIndexer;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

@Slf4j
@UtilityClass
public class EvmChainIndexerFactory {

    private static final Map<String, ChainId> NETWORK_ID_TO_CHAIN_ID = Map.of(
            "ethereum_mainnet", ChainId.ETHEREUM,
            "polygon_mainnet", ChainId.POLYGON,
            "arbitrum_mainnet", ChainId.ARBITRUM,
            "optimism_mainnet", ChainId.OPTIMISM,
            "base_mainnet", ChainId.BASE,
            "avalanche_mainnet", ChainId.AVALANCHE,
            "bsc_mainnet", ChainId.BSC
    );

    public static ChainIndexer create(EvmChainConfig config, ObjectMapper objectMapper,
                                      MeterRegistry meterRegistry) {
        var chainId = resolveChainId(config.networkId());

        var rpcClient = new EvmRpcClient(
                config.rpcUrl(),
                config.rpcBatchSize(),
                config.useBlockReceipts(),
                config.rpcTimeout(),
                objectMapper
        );

        var resilientClient = new ResilientEvmRpcClient(
                rpcClient,
                config.networkId(),
                config.maxRetries(),
                config.rateLimitRps(),
                config.rateLimitBurst(),
                meterRegistry
        );

        var nativeTransferParser = new EvmNativeTransferParser(chainId, config.nativeDecimals());
        var tokenConfigs = mapTokenContracts(config.tokenContracts());
        var erc20TransferParser = new EvmErc20TransferParser(chainId, tokenConfigs);

        log.info("Created EvmChainIndexer for networkId={}, chainId={}, useFinalizedTag={}, "
                        + "minConfirmations={}, indexNativeTransfers={}, tokenContracts={}",
                config.networkId(), chainId, config.useFinalizedTag(),
                config.minConfirmations(),
                config.indexNativeTransfers(),
                tokenConfigs.size());

        return new EvmChainIndexer(
                resilientClient,
                chainId,
                config.useFinalizedTag(),
                config.minConfirmations(),
                config.indexNativeTransfers(),
                nativeTransferParser,
                erc20TransferParser
        );
    }

    public static ChainId resolveChainId(String networkId) {
        var chainId = NETWORK_ID_TO_CHAIN_ID.get(networkId);
        if (chainId == null) {
            throw new IllegalArgumentException(
                    "Unknown EVM network ID: '%s'. Supported: %s".formatted(
                            networkId, NETWORK_ID_TO_CHAIN_ID.keySet()));
        }
        return chainId;
    }

    private static List<EvmTokenConfig> mapTokenContracts(List<EvmChainTokenConfig> tokenContracts) {
        return tokenContracts.stream()
                .map(tc -> EvmTokenConfig.builder()
                        .address(tc.address())
                        .symbol(tc.symbol())
                        .decimals(tc.decimals())
                        .build())
                .toList();
    }

}
