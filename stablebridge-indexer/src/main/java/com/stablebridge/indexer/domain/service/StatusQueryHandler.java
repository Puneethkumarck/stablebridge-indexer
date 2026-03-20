package com.stablebridge.indexer.domain.service;

import com.stablebridge.indexer.domain.model.BloomStatus;
import com.stablebridge.indexer.domain.model.ChainConfiguration;
import com.stablebridge.indexer.domain.model.ChainId;
import com.stablebridge.indexer.domain.model.ChainStatus;
import com.stablebridge.indexer.domain.model.NetworkType;
import com.stablebridge.indexer.domain.port.BlockProgressStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static com.stablebridge.indexer.domain.model.WorkerState.STOPPED;

@Service
@RequiredArgsConstructor
public class StatusQueryHandler {

    private final BlockProgressStore blockProgressStore;
    private final Map<String, ChainConfiguration> chainConfigurations;
    private final BloomStatus bloomStatus;

    public List<ChainStatus> getAllChainStatuses() {
        return chainConfigurations.entrySet().stream()
                .map(entry -> buildChainStatus(entry.getKey(), entry.getValue()))
                .toList();
    }

    public Optional<ChainStatus> getChainStatus(String chainName) {
        var config = chainConfigurations.get(chainName);
        if (config == null) {
            return Optional.empty();
        }
        return Optional.of(buildChainStatus(chainName, config));
    }

    public BloomStatus getBloomStatus() {
        return bloomStatus;
    }

    private ChainStatus buildChainStatus(String chainName, ChainConfiguration config) {
        var lastProcessedBlock = resolveLastProcessedBlock(chainName, config.networkType());

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
        var progress = blockProgressStore.getLastProcessedBlock(chainId);
        return progress.isPresent() ? Optional.of(progress.getAsLong()) : Optional.empty();
    }
}
