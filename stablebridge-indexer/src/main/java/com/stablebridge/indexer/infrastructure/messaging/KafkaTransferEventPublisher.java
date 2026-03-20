package com.stablebridge.indexer.infrastructure.messaging;

import com.stablebridge.indexer.api.TransferEvent;
import com.stablebridge.indexer.domain.event.TransferDetectedEvent;
import com.stablebridge.indexer.domain.port.TransferEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

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

    private final KafkaTemplate<String, TransferEvent> kafkaTemplate;
    private final TransferEventMapper transferEventMapper;

    @Override
    public void publish(TransferDetectedEvent event) {
        TransferEvent transferEvent = transferEventMapper.toTransferEvent(event);
        String topic = TOPIC_PREFIX + event.transfer().chainId().name();
        String key = event.transfer().toAddress();

        log.info("Publishing transfer event to topic={}, key={}, txHash={}",
                topic, key, transferEvent.txHash());

        kafkaTemplate.send(topic, key, transferEvent);
    }

    @Override
    public void publishAll(List<TransferDetectedEvent> events) {
        for (TransferDetectedEvent event : events) {
            publish(event);
        }
    }
}
