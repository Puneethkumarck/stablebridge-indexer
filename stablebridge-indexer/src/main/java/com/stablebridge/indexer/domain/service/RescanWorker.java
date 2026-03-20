package com.stablebridge.indexer.domain.service;

import com.stablebridge.indexer.domain.model.WorkerType;
import com.stablebridge.indexer.domain.port.AddressFilter;
import com.stablebridge.indexer.domain.port.BlockProgressStore;
import com.stablebridge.indexer.domain.port.ChainIndexer;
import com.stablebridge.indexer.domain.port.TransferEventPublisher;
import com.stablebridge.indexer.domain.port.WalletAddressRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;

import static com.stablebridge.indexer.domain.model.WorkerType.RESCAN;

@Slf4j
public class RescanWorker extends BaseWorker {

    static final int MAX_ATTEMPTS = 5;

    private final ConcurrentHashMap<Long, Integer> attemptCounts = new ConcurrentHashMap<>();

    public RescanWorker(
            ChainIndexer chainIndexer,
            AddressFilter addressFilter,
            TransferEventPublisher transferEventPublisher,
            BlockProgressStore blockProgressStore,
            WalletAddressRepository walletAddressRepository,
            MeterRegistry meterRegistry) {
        super(chainIndexer, addressFilter, transferEventPublisher,
                blockProgressStore, walletAddressRepository, meterRegistry);
    }

    @Override
    public WorkerType getWorkerType() {
        return RESCAN;
    }

    public void rescan() {
        var failedBlocks = getBlockProgressStore().getFailedBlocks(getChainId());

        if (failedBlocks.isEmpty()) {
            log.debug("No failed blocks to rescan — chain={}", getChainId());
            return;
        }

        log.info("Rescanning {} failed blocks — chain={}", failedBlocks.size(), getChainId());

        failedBlocks.stream()
                .sorted()
                .forEach(this::retryBlock);
    }

    private void retryBlock(long blockNumber) {
        var attempt = attemptCounts.getOrDefault(blockNumber, 0) + 1;

        if (attempt > MAX_ATTEMPTS) {
            log.error("Max rescan attempts exceeded — chain={}, blockNumber={}, maxAttempts={}",
                    getChainId(), blockNumber, MAX_ATTEMPTS);
            attemptCounts.remove(blockNumber);
            return;
        }

        var backoffMs = calculateBackoff(attempt);
        log.info("Retrying failed block — attempt={}/{}, chain={}, blockNumber={}, backoffMs={}",
                attempt, MAX_ATTEMPTS, getChainId(), blockNumber, backoffMs);

        try {
            Thread.sleep(backoffMs);
            processBlock(blockNumber);
            getBlockProgressStore().removeFailedBlock(getChainId(), blockNumber);
            attemptCounts.remove(blockNumber);
            log.info("Rescan succeeded — chain={}, blockNumber={}", getChainId(), blockNumber);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            attemptCounts.put(blockNumber, attempt);
        } catch (Exception e) {
            attemptCounts.put(blockNumber, attempt);
            log.warn("Rescan failed — chain={}, blockNumber={}, attempt={}/{}, error={}",
                    getChainId(), blockNumber, attempt, MAX_ATTEMPTS, e.getMessage());
        }
    }

    long calculateBackoff(int attempt) {
        return Math.min((long) Math.pow(2, attempt - 1) * 1000L, 30_000L);
    }
}
