package com.stablebridge.indexer.domain.port;

import com.stablebridge.indexer.domain.event.TransferDetectedEvent;

import java.util.List;

/**
 * Port for publishing confirmed transfer events to the messaging infrastructure.
 *
 * <p>Events are published to per-chain Kafka topics ({@code transfer.events.<networkId>})
 * with {@code toAddress} as the partition key, ensuring per-wallet ordering.
 *
 * <p>Publishing must always happen <em>before</em> saving block progress to Redis
 * to guarantee at-least-once delivery. Consumers are expected to deduplicate by
 * {@code txHash + toAddress + networkId}.
 */
public interface TransferEventPublisher {

    /**
     * Publishes a single confirmed transfer event.
     *
     * @param event the transfer event to publish
     */
    void publish(TransferDetectedEvent event);

    /**
     * Publishes a batch of confirmed transfer events.
     *
     * <p>All events in the batch are published before returning. Implementations should
     * ensure atomicity where possible — either all events are published or the failure
     * is propagated so the block can be retried.
     *
     * @param events the transfer events to publish
     */
    void publishAll(List<TransferDetectedEvent> events);
}
