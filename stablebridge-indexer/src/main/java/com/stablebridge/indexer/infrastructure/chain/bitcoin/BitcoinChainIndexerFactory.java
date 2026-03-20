package com.stablebridge.indexer.infrastructure.chain.bitcoin;

import com.stablebridge.indexer.domain.model.ChainId;
import com.stablebridge.indexer.domain.port.ChainIndexer;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
@UtilityClass
public class BitcoinChainIndexerFactory {

    private static final Map<String, ChainId> NETWORK_ID_TO_CHAIN_ID = Map.of(
            "bitcoin_mainnet", ChainId.BITCOIN_CHAIN);

    public static ChainIndexer create(BitcoinChainConfig config) {
        var chainId = resolveChainId(config.networkId());
        var rpcClient = new BitcoinRpcClient(
                config.rpcUrl(), config.rpcUsername(), config.rpcPassword(), config.rpcTimeout());
        var transferParser = new BitcoinTransferParser(chainId);
        var confirmationTracker = new BitcoinConfirmationTracker(rpcClient, config.minConfirmations());

        log.info("Created BitcoinChainIndexer for networkId={}, chainId={}, minConfirmations={}",
                config.networkId(), chainId, config.minConfirmations());

        return new BitcoinChainIndexer(rpcClient, chainId, transferParser, confirmationTracker);
    }

    public static ChainId resolveChainId(String networkId) {
        var chainId = NETWORK_ID_TO_CHAIN_ID.get(networkId);
        if (chainId == null) {
            throw new IllegalArgumentException(
                    "Unknown Bitcoin network ID: '%s'. Supported: %s".formatted(
                            networkId, NETWORK_ID_TO_CHAIN_ID.keySet()));
        }
        return chainId;
    }
}
