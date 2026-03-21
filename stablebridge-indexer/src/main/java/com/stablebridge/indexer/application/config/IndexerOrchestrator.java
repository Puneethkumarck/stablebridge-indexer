package com.stablebridge.indexer.application.config;

import com.stablebridge.indexer.application.properties.ChainProperties;
import com.stablebridge.indexer.application.properties.IndexerProperties;
import com.stablebridge.indexer.domain.model.NetworkType;
import com.stablebridge.indexer.domain.port.AddressFilter;
import com.stablebridge.indexer.domain.port.BlockProgressStore;
import com.stablebridge.indexer.domain.port.ChainIndexer;
import com.stablebridge.indexer.domain.port.TransferEventPublisher;
import com.stablebridge.indexer.domain.port.WalletAddressRepository;
import com.stablebridge.indexer.domain.service.BaseWorker;
import com.stablebridge.indexer.domain.service.CatchupWorker;
import com.stablebridge.indexer.domain.service.RegularWorker;
import com.stablebridge.indexer.domain.service.RescanWorker;
import com.stablebridge.indexer.infrastructure.chain.evm.EvmChainIndexerFactory;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.stablebridge.indexer.domain.model.WorkerState.PARKED;
import static com.stablebridge.indexer.domain.model.WorkerState.RUNNING;

@Slf4j
@Component
public class IndexerOrchestrator implements SmartLifecycle {

    static final int CATCHUP_CHUNK_SIZE = 100;
    static final Duration CATCHUP_INTERVAL = Duration.ofSeconds(60);
    static final Duration RESCAN_INTERVAL = Duration.ofSeconds(300);
    static final Duration HEALTH_PROBE_INTERVAL = Duration.ofSeconds(60);
    private static final long TERMINATION_TIMEOUT_SECONDS = 25;

    private final List<ChainIndexer> chainIndexers;
    private final AddressFilter addressFilter;
    private final TransferEventPublisher transferEventPublisher;
    private final BlockProgressStore blockProgressStore;
    private final WalletAddressRepository walletAddressRepository;
    private final MeterRegistry meterRegistry;
    private final IndexerProperties indexerProperties;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final List<BaseWorker> workers = new ArrayList<>();
    private ExecutorService executor;

    public IndexerOrchestrator(
            List<ChainIndexer> chainIndexers,
            AddressFilter addressFilter,
            TransferEventPublisher transferEventPublisher,
            BlockProgressStore blockProgressStore,
            WalletAddressRepository walletAddressRepository,
            MeterRegistry meterRegistry,
            IndexerProperties indexerProperties) {
        this.chainIndexers = chainIndexers;
        this.addressFilter = addressFilter;
        this.transferEventPublisher = transferEventPublisher;
        this.blockProgressStore = blockProgressStore;
        this.walletAddressRepository = walletAddressRepository;
        this.meterRegistry = meterRegistry;
        this.indexerProperties = indexerProperties;
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            log.warn("IndexerOrchestrator already running — ignoring duplicate start()");
            return;
        }

        initializeBloomFilter();

        workers.clear();
        executor = Executors.newVirtualThreadPerTaskExecutor();

        chainIndexers.forEach(this::startWorkersForChain);

        log.info("IndexerOrchestrator started — {} chain(s), {} worker(s)",
                chainIndexers.size(), workers.size());
    }

    @Override
    public void stop(Runnable callback) {
        log.info("IndexerOrchestrator shutdown initiated — stopping {} worker(s)", workers.size());

        workers.forEach(BaseWorker::stop);
        executor.shutdownNow();

        try {
            if (!executor.awaitTermination(TERMINATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                log.warn("Executor did not terminate within {}s", TERMINATION_TIMEOUT_SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        running.set(false);
        callback.run();
        log.info("IndexerOrchestrator shutdown complete");
    }

    @Override
    public void stop() {
        stop(() -> {});
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    public List<BaseWorker> getWorkers() {
        return List.copyOf(workers);
    }

    private void initializeBloomFilter() {
        for (var networkType : NetworkType.values()) {
            var addresses = walletAddressRepository.findAllByNetworkType(networkType);
            addresses.forEach(wallet -> addressFilter.add(wallet.address(), networkType));
            log.info("Initialized bloom filter — networkType={}, addressCount={}",
                    networkType, addresses.size());
        }
    }

    private void startWorkersForChain(ChainIndexer chainIndexer) {
        var chainProps = resolveChainProperties(chainIndexer);
        if (chainProps == null) {
            log.warn("No chain properties found for chainId={} — skipping", chainIndexer.getChainId());
            return;
        }

        var regularWorker = new RegularWorker(
                chainIndexer, addressFilter, transferEventPublisher,
                blockProgressStore, walletAddressRepository, meterRegistry,
                chainProps.pollInterval(), chainProps.batchSize(), chainProps.startBlock());

        var catchupWorker = new CatchupWorker(
                chainIndexer, addressFilter, transferEventPublisher,
                blockProgressStore, walletAddressRepository, meterRegistry,
                CATCHUP_CHUNK_SIZE);
        catchupWorker.setChunkExecutor(executor);

        var rescanWorker = new RescanWorker(
                chainIndexer, addressFilter, transferEventPublisher,
                blockProgressStore, walletAddressRepository, meterRegistry);

        regularWorker.start();
        catchupWorker.start();
        rescanWorker.start();

        workers.add(regularWorker);
        workers.add(catchupWorker);
        workers.add(rescanWorker);

        executor.submit(() -> runWorkerLoop(regularWorker, regularWorker::poll, chainProps.pollInterval()));
        executor.submit(() -> runWorkerLoop(catchupWorker, catchupWorker::processRanges, CATCHUP_INTERVAL));
        executor.submit(() -> runWorkerLoop(rescanWorker, rescanWorker::rescan, RESCAN_INTERVAL));

        log.info("Started workers for chain={}, pollInterval={}, batchSize={}, startBlock={}",
                chainIndexer.getChainId(), chainProps.pollInterval(),
                chainProps.batchSize(), chainProps.startBlock());
    }

    private ChainProperties resolveChainProperties(ChainIndexer chainIndexer) {
        return indexerProperties.chains().entrySet().stream()
                .filter(entry -> {
                    try {
                        var chainId = EvmChainIndexerFactory.resolveChainId(entry.getKey());
                        return chainId == chainIndexer.getChainId();
                    } catch (IllegalArgumentException e) {
                        return false;
                    }
                })
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private void runWorkerLoop(BaseWorker worker, Runnable task, Duration interval) {
        while (worker.getState() == RUNNING) {
            try {
                task.run();
                if (worker.getState() != RUNNING) {
                    break;
                }
                Thread.sleep(interval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("{} worker error — chain={}",
                        worker.getWorkerType(), worker.getChainId(), e);
                worker.park();
                runResumeProbe(worker);
            }
        }
    }

    private void runResumeProbe(BaseWorker worker) {
        while (worker.getState() == PARKED) {
            try {
                Thread.sleep(HEALTH_PROBE_INTERVAL);
                if (worker.getState() != PARKED) {
                    break;
                }
                worker.tryResume();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}
