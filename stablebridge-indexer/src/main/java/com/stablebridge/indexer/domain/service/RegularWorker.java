package com.stablebridge.indexer.domain.service;

import com.stablebridge.indexer.domain.model.WorkerType;
import com.stablebridge.indexer.domain.port.AddressFilter;
import com.stablebridge.indexer.domain.port.BlockProgressStore;
import com.stablebridge.indexer.domain.port.ChainIndexer;
import com.stablebridge.indexer.domain.port.TransferEventPublisher;
import com.stablebridge.indexer.domain.port.WalletAddressRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static com.stablebridge.indexer.domain.model.WorkerType.REGULAR;

@Slf4j
public class RegularWorker extends BaseWorker {

    private final Duration pollInterval;
    private final int batchSize;
    private final long startBlock;
    private final AtomicLong chainLag = new AtomicLong(0);

    public RegularWorker(
            ChainIndexer chainIndexer,
            AddressFilter addressFilter,
            TransferEventPublisher transferEventPublisher,
            BlockProgressStore blockProgressStore,
            WalletAddressRepository walletAddressRepository,
            MeterRegistry meterRegistry,
            Duration pollInterval,
            int batchSize,
            long startBlock) {
        super(chainIndexer, addressFilter, transferEventPublisher,
                blockProgressStore, walletAddressRepository, meterRegistry);
        this.pollInterval = pollInterval;
        this.batchSize = batchSize;
        this.startBlock = startBlock;
        Gauge.builder("indexer.chain.lag", chainLag, AtomicLong::get)
                .tag("chain", chainIndexer.getChainId().name())
                .register(meterRegistry);
    }

    @Override
    public WorkerType getWorkerType() {
        return REGULAR;
    }

    public void poll() {
        var latestFinalized = getChainIndexer().getLatestFinalizedBlockNumber();
        var lastProcessed = getBlockProgressStore().getLastProcessedBlock(getChainId())
                .orElse(startBlock - 1);
        var fromBlock = lastProcessed + 1;
        var toBlock = Math.min(fromBlock + batchSize - 1, latestFinalized);

        if (fromBlock > latestFinalized) {
            log.debug("No new finalized blocks — chain={}, latestFinalized={}", getChainId(), latestFinalized);
            return;
        }

        var lag = latestFinalized - lastProcessed;
        chainLag.set(lag);
        if (lag > batchSize * 10L) {
            log.warn("Block lag exceeds threshold — chain={}, lag={}, latestFinalized={}, lastProcessed={}",
                    getChainId(), lag, latestFinalized, lastProcessed);
        }

        for (var blockNumber = fromBlock; blockNumber <= toBlock; blockNumber++) {
            processBlock(blockNumber);
        }
    }

    public Duration getPollInterval() {
        return pollInterval;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public long getStartBlock() {
        return startBlock;
    }
}
