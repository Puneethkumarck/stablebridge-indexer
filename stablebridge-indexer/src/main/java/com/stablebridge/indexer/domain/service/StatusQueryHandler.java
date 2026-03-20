package com.stablebridge.indexer.domain.service;

import com.stablebridge.indexer.domain.model.BloomStatus;
import com.stablebridge.indexer.domain.model.ChainConfiguration;
import com.stablebridge.indexer.domain.model.ChainId;
import com.stablebridge.indexer.domain.model.ChainStatus;
import com.stablebridge.indexer.domain.model.NetworkType;
import com.stablebridge.indexer.domain.model.WorkerState;
import com.stablebridge.indexer.domain.port.BlockProgressStore;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

import static com.stablebridge.indexer.domain.model.WorkerState.STOPPED;

/**
 * Domain query handler for indexer status reporting.
 *
 * <p>Provides read-only views of chain indexing progress and bloom filter configuration.
 * Workers are not yet implemented (Phase 4), so worker state defaults to {@link WorkerState#STOPPED}
 * and finalized block information is not yet available.
 */
@RequiredArgsConstructor
public class StatusQueryHandler {

    private final BlockProgressStore blockProgressStore;
    private final Map<String, ChainConfiguration> chainConfigurations;
    private final BloomStatus bloomStatus;

    /**
     * Returns the status of all configured chains.
     *
     * @return list of chain statuses, one per configured chain
     */
    public List<ChainStatus> getAllChainStatuses() {
        return chainConfigurations.entrySet().stream()
                .map(entry -> buildChainStatus(entry.getKey(), entry.getValue()))
                .toList();
    }

    /**
     * Returns the status of a single chain by name.
     *
     * @param chainName the chain network identifier (e.g., {@code "ethereum_mainnet"})
     * @return the chain status, or empty if the chain is not configured
     */
    public Optional<ChainStatus> getChainStatus(String chainName) {
        ChainConfiguration config = chainConfigurations.get(chainName);
        if (config == null) {
            return Optional.empty();
        }
        return Optional.of(buildChainStatus(chainName, config));
    }

    /**
     * Returns the bloom filter status and configuration.
     *
     * @return bloom filter status with backend, sizing, and covered network types
     */
    public BloomStatus getBloomStatus() {
        return bloomStatus;
    }

    private ChainStatus buildChainStatus(String chainName, ChainConfiguration config) {
        Long lastProcessedBlock = resolveLastProcessedBlock(chainName, config.networkType());

        return ChainStatus.builder()
                .chainName(chainName)
                .networkType(config.networkType())
                .workerState(STOPPED)
                .lastProcessedBlock(lastProcessedBlock)
                .latestFinalizedBlock(null)
                .blocksBehind(null)
                .enabled(config.enabled())
                .build();
    }

    private Long resolveLastProcessedBlock(String chainName, NetworkType networkType) {
        return resolveChainId(chainName, networkType)
                .flatMap(this::getLastProcessedBlockForChain)
                .orElse(null);
    }

    private Optional<ChainId> resolveChainId(String chainName, NetworkType networkType) {
        return Arrays.stream(ChainId.values())
                .filter(id -> id.networkType() == networkType)
                .filter(id -> chainName.toUpperCase(Locale.ROOT).startsWith(id.name()))
                .findFirst();
    }

    private Optional<Long> getLastProcessedBlockForChain(ChainId chainId) {
        OptionalLong progress = blockProgressStore.getLastProcessedBlock(chainId);
        return progress.isPresent() ? Optional.of(progress.getAsLong()) : Optional.empty();
    }
}
