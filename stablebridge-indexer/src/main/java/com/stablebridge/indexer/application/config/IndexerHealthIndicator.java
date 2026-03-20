package com.stablebridge.indexer.application.config;

import com.stablebridge.indexer.domain.service.BaseWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.stablebridge.indexer.domain.model.WorkerState.PARKED;
import static com.stablebridge.indexer.domain.model.WorkerState.STOPPED;

@Slf4j
@Component
@RequiredArgsConstructor
public class IndexerHealthIndicator implements HealthIndicator {

    private static final Status DEGRADED = new Status("DEGRADED");

    private final IndexerOrchestrator orchestrator;

    @Override
    public Health health() {
        var workers = orchestrator.getWorkers();

        if (workers.isEmpty()) {
            return Health.unknown()
                    .withDetail("reason", "No chains configured")
                    .build();
        }

        var chainDetails = buildChainDetails(workers);

        if (!orchestrator.isRunning()) {
            return Health.down()
                    .withDetail("chains", chainDetails)
                    .build();
        }

        var hasStoppedWorker = workers.stream()
                .anyMatch(w -> w.getState() == STOPPED);
        var hasParkedWorker = workers.stream()
                .anyMatch(w -> w.getState() == PARKED);

        if (hasStoppedWorker) {
            return Health.down()
                    .withDetail("chains", chainDetails)
                    .build();
        }

        if (hasParkedWorker) {
            return Health.status(DEGRADED)
                    .withDetail("chains", chainDetails)
                    .build();
        }

        return Health.up()
                .withDetail("chains", chainDetails)
                .build();
    }

    private Map<String, String> buildChainDetails(List<BaseWorker> workers) {
        return workers.stream()
                .collect(Collectors.toMap(
                        w -> w.getChainId().name() + ":" + w.getWorkerType().name(),
                        w -> w.getState().name(),
                        (a, b) -> a,
                        LinkedHashMap::new));
    }
}
