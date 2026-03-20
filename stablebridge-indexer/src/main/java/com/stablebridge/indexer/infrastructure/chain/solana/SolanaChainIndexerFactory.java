package com.stablebridge.indexer.infrastructure.chain.solana;

import com.stablebridge.indexer.domain.model.ChainId;
import com.stablebridge.indexer.domain.port.ChainIndexer;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

@Slf4j
@UtilityClass
public class SolanaChainIndexerFactory {

    private static final Map<String, ChainId> NETWORK_ID_TO_CHAIN_ID = Map.of(
            "solana_mainnet", ChainId.SOLANA_CHAIN
    );

    public static ChainIndexer create(SolanaChainConfig config) {
        var chainId = resolveChainId(config.networkId());

        var rpcClient = new SolanaRpcClient(config.rpcUrl(), config.rpcTimeout());
        var nativeTransferParser = new SolanaNativeTransferParser(chainId);
        var tokenConfigs = mapTokenContracts(config.tokenContracts());
        var splTransferParser = new SolanaSpITransferParser(chainId, tokenConfigs);

        log.info("Created SolanaChainIndexer for networkId={}, chainId={}, "
                        + "indexNativeTransfers={}, tokenContracts={}",
                config.networkId(), chainId,
                config.indexNativeTransfers(),
                tokenConfigs.size());

        return new SolanaChainIndexer(
                rpcClient,
                chainId,
                config.indexNativeTransfers(),
                nativeTransferParser,
                splTransferParser
        );
    }

    public static ChainId resolveChainId(String networkId) {
        var chainId = NETWORK_ID_TO_CHAIN_ID.get(networkId);
        if (chainId == null) {
            throw new IllegalArgumentException(
                    "Unknown Solana network ID: '%s'. Supported: %s".formatted(
                            networkId, NETWORK_ID_TO_CHAIN_ID.keySet()));
        }
        return chainId;
    }

    private static List<SolanaTokenConfig> mapTokenContracts(List<SolanaChainTokenConfig> tokenContracts) {
        return tokenContracts.stream()
                .map(tc -> SolanaTokenConfig.builder()
                        .mintAddress(tc.mintAddress())
                        .symbol(tc.symbol())
                        .decimals(tc.decimals())
                        .build())
                .toList();
    }
}
