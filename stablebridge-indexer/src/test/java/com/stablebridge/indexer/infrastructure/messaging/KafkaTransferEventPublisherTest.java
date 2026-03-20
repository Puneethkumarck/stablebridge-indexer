package com.stablebridge.indexer.infrastructure.messaging;

import com.stablebridge.indexer.api.TransferEvent;
import com.stablebridge.indexer.domain.event.TransferDetectedEvent;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.stablebridge.indexer.domain.model.ChainId.POLYGON;
import static com.stablebridge.indexer.domain.model.TransferDirection.INCOMING;
import static com.stablebridge.indexer.testutil.TransferFixtures.DEFAULT_TO_ADDRESS;
import static com.stablebridge.indexer.testutil.TransferFixtures.aTransfer;
import static com.stablebridge.indexer.testutil.TransferFixtures.aTransferDetectedEvent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("KafkaTransferEventPublisher")
class KafkaTransferEventPublisherTest {

    @Mock
    private KafkaTemplate<String, TransferEvent> kafkaTemplate;

    @Mock
    private TransferEventMapper transferEventMapper;

    private MeterRegistry meterRegistry;

    private KafkaTransferEventPublisher publisher;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        publisher = new KafkaTransferEventPublisher(kafkaTemplate, transferEventMapper, meterRegistry);
    }

    @Test
    @DisplayName("publishes transfer event to correct topic with toAddress key")
    void publish_sendsToCorrectTopicWithToAddressKey() {
        // given
        var event = aTransferDetectedEvent().build();
        var transfer = event.transfer();

        var expectedApiEvent = new TransferEvent(
                transfer.txHash(),
                transfer.fromAddress(),
                transfer.toAddress(),
                transfer.rawAmount(),
                transfer.amount(),
                transfer.decimals(),
                transfer.tokenSymbol(),
                transfer.tokenContractAddress(),
                transfer.blockNumber(),
                transfer.blockHash(),
                transfer.transactionIndex(),
                transfer.logIndex(),
                transfer.chainId().name(),
                transfer.chainId().networkType().name(),
                transfer.timestamp(),
                transfer.nativeTransfer(),
                event.direction().name(),
                event.detectedAt());

        given(transferEventMapper.toTransferEvent(event)).willReturn(expectedApiEvent);
        given(kafkaTemplate.send("transfer.events.ETHEREUM", DEFAULT_TO_ADDRESS, expectedApiEvent))
                .willReturn(CompletableFuture.completedFuture(null));

        // when
        publisher.publish(event);

        // then
        then(kafkaTemplate).should().send(
                "transfer.events.ETHEREUM",
                DEFAULT_TO_ADDRESS,
                expectedApiEvent);
    }

    @Test
    @DisplayName("uses chain ID for topic name")
    void publish_usesChainIdForTopicName() {
        // given
        var polygonTransfer = aTransfer()
                .chainId(POLYGON)
                .build();
        var event = TransferDetectedEvent.builder()
                .transfer(polygonTransfer)
                .direction(INCOMING)
                .detectedAt(Instant.parse("2026-03-19T10:15:31Z"))
                .build();

        var expectedApiEvent = new TransferEvent(
                polygonTransfer.txHash(),
                polygonTransfer.fromAddress(),
                polygonTransfer.toAddress(),
                polygonTransfer.rawAmount(),
                polygonTransfer.amount(),
                polygonTransfer.decimals(),
                polygonTransfer.tokenSymbol(),
                polygonTransfer.tokenContractAddress(),
                polygonTransfer.blockNumber(),
                polygonTransfer.blockHash(),
                polygonTransfer.transactionIndex(),
                polygonTransfer.logIndex(),
                "POLYGON",
                "EVM",
                polygonTransfer.timestamp(),
                polygonTransfer.nativeTransfer(),
                "INCOMING",
                event.detectedAt());

        given(transferEventMapper.toTransferEvent(event)).willReturn(expectedApiEvent);
        given(kafkaTemplate.send("transfer.events.POLYGON", DEFAULT_TO_ADDRESS, expectedApiEvent))
                .willReturn(CompletableFuture.completedFuture(null));

        // when
        publisher.publish(event);

        // then
        then(kafkaTemplate).should().send(
                "transfer.events.POLYGON",
                DEFAULT_TO_ADDRESS,
                expectedApiEvent);
    }

    @Test
    @DisplayName("publishes each event individually when publishAll is called")
    void publishAll_sendsEachEventIndividually() {
        // given
        var firstTransfer = aTransfer()
                .toAddress("0xfirst1234567890abcdef1234567890abcdef1234")
                .build();
        var secondTransfer = aTransfer()
                .toAddress("0xsecond234567890abcdef1234567890abcdef1234")
                .build();

        var firstEvent = TransferDetectedEvent.builder()
                .transfer(firstTransfer)
                .direction(INCOMING)
                .detectedAt(Instant.parse("2026-03-19T10:15:31Z"))
                .build();
        var secondEvent = TransferDetectedEvent.builder()
                .transfer(secondTransfer)
                .direction(INCOMING)
                .detectedAt(Instant.parse("2026-03-19T10:15:32Z"))
                .build();

        var firstApiEvent = new TransferEvent(
                firstTransfer.txHash(),
                firstTransfer.fromAddress(),
                firstTransfer.toAddress(),
                firstTransfer.rawAmount(),
                firstTransfer.amount(),
                firstTransfer.decimals(),
                firstTransfer.tokenSymbol(),
                firstTransfer.tokenContractAddress(),
                firstTransfer.blockNumber(),
                firstTransfer.blockHash(),
                firstTransfer.transactionIndex(),
                firstTransfer.logIndex(),
                firstTransfer.chainId().name(),
                firstTransfer.chainId().networkType().name(),
                firstTransfer.timestamp(),
                firstTransfer.nativeTransfer(),
                "INCOMING",
                firstEvent.detectedAt());

        var secondApiEvent = new TransferEvent(
                secondTransfer.txHash(),
                secondTransfer.fromAddress(),
                secondTransfer.toAddress(),
                secondTransfer.rawAmount(),
                secondTransfer.amount(),
                secondTransfer.decimals(),
                secondTransfer.tokenSymbol(),
                secondTransfer.tokenContractAddress(),
                secondTransfer.blockNumber(),
                secondTransfer.blockHash(),
                secondTransfer.transactionIndex(),
                secondTransfer.logIndex(),
                secondTransfer.chainId().name(),
                secondTransfer.chainId().networkType().name(),
                secondTransfer.timestamp(),
                secondTransfer.nativeTransfer(),
                "INCOMING",
                secondEvent.detectedAt());

        given(transferEventMapper.toTransferEvent(firstEvent)).willReturn(firstApiEvent);
        given(transferEventMapper.toTransferEvent(secondEvent)).willReturn(secondApiEvent);
        given(kafkaTemplate.send("transfer.events.ETHEREUM",
                "0xfirst1234567890abcdef1234567890abcdef1234", firstApiEvent))
                .willReturn(CompletableFuture.completedFuture(null));
        given(kafkaTemplate.send("transfer.events.ETHEREUM",
                "0xsecond234567890abcdef1234567890abcdef1234", secondApiEvent))
                .willReturn(CompletableFuture.completedFuture(null));

        // when
        publisher.publishAll(List.of(firstEvent, secondEvent));

        // then
        then(kafkaTemplate).should().send(
                "transfer.events.ETHEREUM",
                "0xfirst1234567890abcdef1234567890abcdef1234",
                firstApiEvent);
        then(kafkaTemplate).should().send(
                "transfer.events.ETHEREUM",
                "0xsecond234567890abcdef1234567890abcdef1234",
                secondApiEvent);
    }

    @Test
    @DisplayName("does not send any event when publishAll receives empty list")
    void publishAll_withEmptyList_doesNotSend() {
        // given — empty list

        // when
        publisher.publishAll(List.of());

        // then
        then(kafkaTemplate).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("increments failure counter when Kafka send completes exceptionally")
    void incrementsFailureCounterWhenKafkaSendFails() {
        // given
        var event = aTransferDetectedEvent().build();
        var transfer = event.transfer();

        var apiEvent = new TransferEvent(
                transfer.txHash(),
                transfer.fromAddress(),
                transfer.toAddress(),
                transfer.rawAmount(),
                transfer.amount(),
                transfer.decimals(),
                transfer.tokenSymbol(),
                transfer.tokenContractAddress(),
                transfer.blockNumber(),
                transfer.blockHash(),
                transfer.transactionIndex(),
                transfer.logIndex(),
                transfer.chainId().name(),
                transfer.chainId().networkType().name(),
                transfer.timestamp(),
                transfer.nativeTransfer(),
                event.direction().name(),
                event.detectedAt());

        given(transferEventMapper.toTransferEvent(event)).willReturn(apiEvent);
        var failedFuture = new CompletableFuture<SendResult<String, TransferEvent>>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka broker unavailable"));
        given(kafkaTemplate.send("transfer.events.ETHEREUM", DEFAULT_TO_ADDRESS, apiEvent))
                .willReturn(failedFuture);

        // when
        publisher.publish(event);

        // then
        var counter = meterRegistry.find("indexer.kafka.publish.failed")
                .tag("chain", "ETHEREUM")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }
}
