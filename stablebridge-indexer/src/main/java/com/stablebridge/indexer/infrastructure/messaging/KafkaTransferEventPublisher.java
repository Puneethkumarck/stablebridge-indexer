package com.stablebridge.indexer.infrastructure.messaging;

import com.stablebridge.indexer.domain.event.TransferDetectedEvent;
import com.stablebridge.indexer.domain.port.TransferEventPublisher;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kafka-based implementation of {@link TransferEventPublisher}.
 *
 * <p>Publishes confirmed transfer events to per-chain Kafka topics
 * ({@code transfer.events.<chainId>}) with {@code toAddress} as the partition key,
 * ensuring per-wallet ordering for downstream consumers.
 *
 * <p>At-least-once delivery: Kafka publish must always happen <em>before</em>
 * saving block progress to Redis. Consumer dedup key: {@code txHash + toAddress + networkId}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class KafkaTransferEventPublisher implements TransferEventPublisher {

    private static final String TOPIC_PREFIX = "transfer.events.";

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final TransferEventMapper transferEventMapper;
    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, Counter> failureCounters = new ConcurrentHashMap<>();

    @Override
    public void publish(TransferDetectedEvent event) {
        var transferEvent = transferEventMapper.toTransferEvent(event);
        var chain = event.transfer().chainId().name();
        var topic = TOPIC_PREFIX + chain;
        var key = event.transfer().toAddress();

        log.info("Publishing transfer event to topic={}, key={}, txHash={}",
                topic, key, transferEvent.txHash());

        kafkaTemplate.send(topic, key, transferEvent)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        failureCounter(chain).increment();
                        log.error("Failed to publish transfer event — topic={}, key={}, error={}",
                                topic, key, ex.getMessage());
                    }
                });
    }

    @Override
    public void publishAll(List<TransferDetectedEvent> events) {
        events.forEach(this::publish);
    }

    private Counter failureCounter(String chain) {
        return failureCounters.computeIfAbsent(chain, c ->
                Counter.builder("indexer.kafka.publish.failed")
                        .tag("chain", c)
                        .register(meterRegistry));
    }
}
