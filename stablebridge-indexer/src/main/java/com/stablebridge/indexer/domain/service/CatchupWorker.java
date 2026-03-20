package com.stablebridge.indexer.domain.service;

import com.stablebridge.indexer.domain.model.WorkerType;
import com.stablebridge.indexer.domain.port.AddressFilter;
import com.stablebridge.indexer.domain.port.BlockProgressStore;
import com.stablebridge.indexer.domain.port.ChainIndexer;
import com.stablebridge.indexer.domain.port.TransferEventPublisher;
import com.stablebridge.indexer.domain.port.WalletAddressRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.LongStream;

import static com.stablebridge.indexer.domain.model.WorkerType.CATCHUP;

@Slf4j
public class CatchupWorker extends BaseWorker {

    private final int chunkSize;

    public CatchupWorker(
            ChainIndexer chainIndexer,
            AddressFilter addressFilter,
            TransferEventPublisher transferEventPublisher,
            BlockProgressStore blockProgressStore,
            WalletAddressRepository walletAddressRepository,
            MeterRegistry meterRegistry,
            int chunkSize) {
        super(chainIndexer, addressFilter, transferEventPublisher,
                blockProgressStore, walletAddressRepository, meterRegistry);
        this.chunkSize = chunkSize;
    }

    @Override
    public WorkerType getWorkerType() {
        return CATCHUP;
    }

    public void processRanges() {
        var ranges = getBlockProgressStore().getCatchupRanges(getChainId());

        if (ranges.isEmpty()) {
            log.debug("No catchup ranges found — chain={}", getChainId());
            return;
        }

        log.info("Processing {} catchup range(s) — chain={}", ranges.size(), getChainId());

        ranges.forEach((fromBlock, toBlock) -> {
            processRange(fromBlock, toBlock);
            getBlockProgressStore().removeCatchupRange(getChainId(), fromBlock);
        });
    }

    public int getChunkSize() {
        return chunkSize;
    }

    private void processRange(long fromBlock, long toBlock) {
        log.info("Processing catchup range — chain={}, fromBlock={}, toBlock={}", getChainId(), fromBlock, toBlock);

        var chunkStarts = LongStream.iterate(fromBlock, b -> b <= toBlock, b -> b + chunkSize)
                .boxed()
                .toList();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = chunkStarts.stream()
                    .map(chunkStart -> {
                        var chunkEnd = Math.min(chunkStart + chunkSize - 1, toBlock);
                        return executor.submit(() -> processChunk(chunkStart, chunkEnd));
                    })
                    .toList();

            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("Catchup range processing interrupted — chain={}", getChainId(), e);
                    return;
                } catch (ExecutionException e) {
                    log.error("Catchup chunk failed — chain={}", getChainId(), e);
                }
            }
        }
    }

    private void processChunk(long fromBlock, long toBlock) {
        LongStream.rangeClosed(fromBlock, toBlock).forEach(block -> {
            try {
                processBlock(block);
            } catch (Exception e) {
                log.error("Failed to process block during catchup — chain={}, block={}", getChainId(), block, e);
                getBlockProgressStore().addFailedBlock(getChainId(), block);
            }
        });
    }
}
