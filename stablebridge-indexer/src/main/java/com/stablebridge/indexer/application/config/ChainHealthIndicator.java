package com.stablebridge.indexer.application.config;

import com.stablebridge.indexer.domain.model.ChainId;
import com.stablebridge.indexer.domain.port.BlockProgressStore;
import com.stablebridge.indexer.domain.port.ChainIndexer;
import com.stablebridge.indexer.domain.service.BaseWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.stablebridge.indexer.domain.model.WorkerState.RUNNING;
import static com.stablebridge.indexer.domain.model.WorkerType.REGULAR;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChainHealthIndicator implements HealthIndicator {

    private final IndexerOrchestrator orchestrator;
    private final BlockProgressStore blockProgressStore;
    private final List<ChainIndexer> chainIndexers;

    @Override
    public Health health() {
        var workers = orchestrator.getWorkers();

        if (workers.isEmpty()) {
            return Health.unknown()
                    .withDetail("reason", "No chains configured")
                    .build();
        }

        var chainIndexerMap = chainIndexers.stream()
                .collect(Collectors.toMap(ChainIndexer::getChainId, Function.identity()));

        var chainDetails = buildChainDetails(workers, chainIndexerMap);

        var hasRunningWorker = workers.stream()
                .anyMatch(w -> w.getState() == RUNNING);

        if (hasRunningWorker) {
            return Health.up()
                    .withDetail("chains", chainDetails)
                    .build();
        }

        return Health.down()
                .withDetail("chains", chainDetails)
                .build();
    }

    private Map<String, Map<String, Object>> buildChainDetails(
            List<BaseWorker> workers,
            Map<ChainId, ChainIndexer> chainIndexerMap) {

        return workers.stream()
                .filter(w -> w.getWorkerType() == REGULAR)
                .collect(Collectors.toMap(
                        w -> w.getChainId().name(),
                        w -> buildWorkerDetail(w, chainIndexerMap),
                        (a, b) -> a,
                        LinkedHashMap::new));
    }

    private Map<String, Object> buildWorkerDetail(BaseWorker worker, Map<ChainId, ChainIndexer> chainIndexerMap) {
        var detail = new LinkedHashMap<String, Object>();
        detail.put("state", worker.getState().name());

        var lastProcessed = blockProgressStore.getLastProcessedBlock(worker.getChainId());
        lastProcessed.ifPresent(block -> detail.put("latestBlock", block));

        var chainIndexer = chainIndexerMap.get(worker.getChainId());
        if (chainIndexer != null) {
            try {
                var chainHead = chainIndexer.getLatestFinalizedBlockNumber();
                detail.put("chainHead", chainHead);
                lastProcessed.ifPresent(block -> detail.put("lag", chainHead - block));
            } catch (Exception e) {
                log.debug("Failed to fetch chain head for {} — error={}", worker.getChainId(), e.getMessage());
                detail.put("chainHead", "unavailable");
            }
        }

        return detail;
    }
}
