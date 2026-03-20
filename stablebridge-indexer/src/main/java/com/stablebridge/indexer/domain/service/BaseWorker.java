package com.stablebridge.indexer.domain.service;

import com.stablebridge.indexer.domain.event.TransferDetectedEvent;
import com.stablebridge.indexer.domain.model.BlockResult;
import com.stablebridge.indexer.domain.model.ChainId;
import com.stablebridge.indexer.domain.model.Transfer;
import com.stablebridge.indexer.domain.model.TransferDirection;
import com.stablebridge.indexer.domain.model.WorkerState;
import com.stablebridge.indexer.domain.model.WorkerType;
import com.stablebridge.indexer.domain.port.AddressFilter;
import com.stablebridge.indexer.domain.port.BlockProgressStore;
import com.stablebridge.indexer.domain.port.ChainIndexer;
import com.stablebridge.indexer.domain.port.TransferEventPublisher;
import com.stablebridge.indexer.domain.port.WalletAddressRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static com.stablebridge.indexer.domain.model.TransferDirection.INCOMING;
import static com.stablebridge.indexer.domain.model.TransferDirection.OUTGOING;
import static com.stablebridge.indexer.domain.model.WorkerState.PARKED;
import static com.stablebridge.indexer.domain.model.WorkerState.RUNNING;
import static com.stablebridge.indexer.domain.model.WorkerState.STOPPED;

@Slf4j
public abstract class BaseWorker {

    private final ChainIndexer chainIndexer;
    private final AddressFilter addressFilter;
    private final TransferEventPublisher transferEventPublisher;
    private final BlockProgressStore blockProgressStore;
    private final WalletAddressRepository walletAddressRepository;
    private final Counter blocksProcessedCounter;
    private final Counter transfersMatchedCounter;
    private final Counter blocksFailedCounter;
    private final AtomicReference<WorkerState> state = new AtomicReference<>(STOPPED);

    protected BaseWorker(
            ChainIndexer chainIndexer,
            AddressFilter addressFilter,
            TransferEventPublisher transferEventPublisher,
            BlockProgressStore blockProgressStore,
            WalletAddressRepository walletAddressRepository,
            MeterRegistry meterRegistry) {
        this.chainIndexer = chainIndexer;
        this.addressFilter = addressFilter;
        this.transferEventPublisher = transferEventPublisher;
        this.blockProgressStore = blockProgressStore;
        this.walletAddressRepository = walletAddressRepository;
        this.blocksProcessedCounter = Counter.builder("indexer.blocks.processed")
                .tag("chain", chainIndexer.getChainId().name())
                .register(meterRegistry);
        this.transfersMatchedCounter = Counter.builder("indexer.transfers.matched")
                .tag("chain", chainIndexer.getChainId().name())
                .register(meterRegistry);
        this.blocksFailedCounter = Counter.builder("indexer.blocks.failed")
                .tag("chain", chainIndexer.getChainId().name())
                .register(meterRegistry);
    }

    public WorkerState getState() {
        return state.get();
    }

    public ChainId getChainId() {
        return chainIndexer.getChainId();
    }

    public abstract WorkerType getWorkerType();

    public void start() {
        state.set(RUNNING);
        log.info("Worker started — chain={}, workerType={}", getChainId(), getWorkerType());
    }

    public void stop() {
        state.set(STOPPED);
        log.info("Worker stopped — chain={}, workerType={}", getChainId(), getWorkerType());
    }

    public void park() {
        state.set(PARKED);
        log.warn("Worker parked — chain={}, workerType={}", getChainId(), getWorkerType());
    }

    public boolean tryResume() {
        try {
            chainIndexer.getLatestFinalizedBlockNumber();
            state.set(RUNNING);
            log.info("Worker resumed from PARKED — chain={}, workerType={}", getChainId(), getWorkerType());
            return true;
        } catch (Exception e) {
            log.debug("Health probe failed, staying PARKED — chain={}, workerType={}, error={}",
                    getChainId(), getWorkerType(), e.getMessage());
            return false;
        }
    }

    public void processBlock(long blockNumber) {
        var traceId = UUID.randomUUID().toString();
        try {
            setMdcContext(blockNumber, traceId);

            log.debug("Processing block — blockNumber={}", blockNumber);

            var blockResult = chainIndexer.indexBlock(blockNumber);
            var matchedEvents = matchTransfers(blockResult);

            if (!matchedEvents.isEmpty()) {
                transferEventPublisher.publishAll(matchedEvents);
                transfersMatchedCounter.increment(matchedEvents.size());
                log.info("Published {} transfer events — blockNumber={}", matchedEvents.size(), blockNumber);
            }

            blockProgressStore.saveLastProcessedBlock(getChainId(), blockNumber);
            blocksProcessedCounter.increment();

            log.debug("Block processed successfully — blockNumber={}", blockNumber);
        } catch (Exception e) {
            blocksFailedCounter.increment();
            throw e;
        } finally {
            clearMdcContext();
        }
    }

    protected boolean isTwoWayIndexingEnabled() {
        return false;
    }

    protected List<TransferDetectedEvent> matchTransfers(BlockResult blockResult) {
        var networkType = getChainId().networkType();
        var events = new ArrayList<TransferDetectedEvent>();

        for (var transfer : blockResult.transfers()) {
            if (addressFilter.mightContain(transfer.toAddress(), networkType)
                    && walletAddressRepository.existsByAddressAndNetworkType(
                            transfer.toAddress(), networkType)) {
                events.add(toTransferDetectedEvent(transfer, INCOMING));
            }

            if (isTwoWayIndexingEnabled()
                    && addressFilter.mightContain(transfer.fromAddress(), networkType)
                    && walletAddressRepository.existsByAddressAndNetworkType(
                            transfer.fromAddress(), networkType)) {
                events.add(toTransferDetectedEvent(transfer, OUTGOING));
            }
        }

        return List.copyOf(events);
    }

    protected ChainIndexer getChainIndexer() {
        return chainIndexer;
    }

    protected BlockProgressStore getBlockProgressStore() {
        return blockProgressStore;
    }

    private TransferDetectedEvent toTransferDetectedEvent(Transfer transfer, TransferDirection direction) {
        return TransferDetectedEvent.builder()
                .transfer(transfer)
                .direction(direction)
                .detectedAt(Instant.now())
                .build();
    }

    private void setMdcContext(long blockNumber, String traceId) {
        MDC.put("chain", getChainId().name());
        MDC.put("blockNumber", String.valueOf(blockNumber));
        MDC.put("workerType", getWorkerType().name());
        MDC.put("traceId", traceId);
    }

    private void clearMdcContext() {
        MDC.remove("chain");
        MDC.remove("blockNumber");
        MDC.remove("workerType");
        MDC.remove("traceId");
    }
}
