package com.stablebridge.indexer;

import com.stablebridge.indexer.api.TransferEvent;
import com.stablebridge.indexer.domain.model.BlockResult;
import com.stablebridge.indexer.domain.model.NetworkType;
import com.stablebridge.indexer.domain.port.AddressFilter;
import com.stablebridge.indexer.domain.port.BlockProgressStore;
import com.stablebridge.indexer.domain.port.ChainIndexer;
import com.stablebridge.indexer.domain.port.TransferEventPublisher;
import com.stablebridge.indexer.domain.port.WalletAddressRepository;
import com.stablebridge.indexer.domain.service.RegularWorker;
import com.stablebridge.indexer.testutil.AbstractIntegrationTest;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static com.stablebridge.indexer.domain.model.ChainId.ETHEREUM;
import static com.stablebridge.indexer.testutil.TransferFixtures.aBlockResult;
import static com.stablebridge.indexer.testutil.TransferFixtures.aTransfer;
import static com.stablebridge.indexer.testutil.TransferFixtures.anIndexedBlock;
import static com.stablebridge.indexer.testutil.WalletAddressFixtures.aWalletAddress;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@DisplayName("Block → Bloom + DB Confirm → Kafka E2E")
class BlockToKafkaEndToEndTest extends AbstractIntegrationTest {

    private static final long BLOCK_NUMBER = 19_500_100L;
    private static final String WATCHED_ADDRESS = "0xabcdef1234567890abcdef1234567890abcdef12";
    private static final String UNWATCHED_ADDRESS = "0x9999999999999999999999999999999999999999";
    private static final String KAFKA_TOPIC = "transfer.events.ETHEREUM";

    @Autowired
    private WalletAddressRepository walletAddressRepository;

    @Autowired
    private AddressFilter addressFilter;

    @Autowired
    private TransferEventPublisher transferEventPublisher;

    @Autowired
    private BlockProgressStore blockProgressStore;

    @Autowired
    private MeterRegistry meterRegistry;

    private KafkaConsumer<String, TransferEvent> consumer;

    @BeforeEach
    void setUp() {
        consumer = createKafkaConsumer();
        consumer.subscribe(List.of(KAFKA_TOPIC));
    }

    @AfterEach
    void tearDown() {
        if (consumer != null) {
            consumer.close();
        }
    }

    @Test
    @DisplayName("processes block through bloom + DB confirm and publishes matching transfer to Kafka")
    void processBlock_withWatchedAddress_publishesToKafkaAndSavesProgress() {
        // given — save wallet address to DB and initialize bloom
        var wallet = aWalletAddress()
                .id(null)
                .address(WATCHED_ADDRESS)
                .networkType(NetworkType.EVM)
                .createdAt(null)
                .updatedAt(null)
                .build();
        walletAddressRepository.save(wallet);
        addressFilter.add(WATCHED_ADDRESS, NetworkType.EVM);

        var transfer = aTransfer()
                .toAddress(WATCHED_ADDRESS)
                .blockNumber(BLOCK_NUMBER)
                .build();

        var blockResult = aBlockResult()
                .indexedBlock(anIndexedBlock().blockNumber(BLOCK_NUMBER).build())
                .transfers(List.of(transfer))
                .build();

        var chainIndexer = stubChainIndexer(BLOCK_NUMBER, blockResult);

        var worker = new RegularWorker(
                chainIndexer, addressFilter, transferEventPublisher,
                blockProgressStore, walletAddressRepository, meterRegistry,
                Duration.ofSeconds(12), 10, BLOCK_NUMBER);
        worker.start();

        // when
        worker.processBlock(BLOCK_NUMBER);

        // then — verify Kafka received the transfer event with correct key and payload
        var records = pollKafkaForKey(WATCHED_ADDRESS);
        assertThat(records).isNotEmpty();

        var record = records.getFirst();

        var expected = TransferEvent.builder()
                .txHash(transfer.txHash())
                .fromAddress(transfer.fromAddress())
                .toAddress(WATCHED_ADDRESS)
                .rawAmount(transfer.rawAmount())
                .amount(transfer.amount())
                .decimals(transfer.decimals())
                .tokenSymbol(transfer.tokenSymbol())
                .tokenContractAddress(transfer.tokenContractAddress())
                .blockNumber(BLOCK_NUMBER)
                .blockHash(transfer.blockHash())
                .transactionIndex(transfer.transactionIndex())
                .logIndex(transfer.logIndex())
                .chainId("ETHEREUM")
                .networkType("EVM")
                .timestamp(transfer.timestamp())
                .nativeTransfer(false)
                .direction("INCOMING")
                .build();

        assertThat(record.value())
                .usingRecursiveComparison()
                .ignoringFields("detectedAt")
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .isEqualTo(expected);

        // then — verify progress saved to Redis AFTER Kafka publish
        var progress = blockProgressStore.getLastProcessedBlock(ETHEREUM);
        assertThat(progress).isPresent();
        assertThat(progress.getAsLong()).isEqualTo(BLOCK_NUMBER);
    }

    @Test
    @DisplayName("does not publish transfer when address is not in bloom filter or DB")
    void processBlock_withUnwatchedAddress_doesNotPublishToKafka() {
        // given — block with transfer to an unwatched address
        var transfer = aTransfer()
                .toAddress(UNWATCHED_ADDRESS)
                .blockNumber(BLOCK_NUMBER + 1)
                .build();

        var blockResult = aBlockResult()
                .indexedBlock(anIndexedBlock().blockNumber(BLOCK_NUMBER + 1).build())
                .transfers(List.of(transfer))
                .build();

        var chainIndexer = stubChainIndexer(BLOCK_NUMBER + 1, blockResult);

        var worker = new RegularWorker(
                chainIndexer, addressFilter, transferEventPublisher,
                blockProgressStore, walletAddressRepository, meterRegistry,
                Duration.ofSeconds(12), 10, BLOCK_NUMBER + 1);
        worker.start();

        // when
        worker.processBlock(BLOCK_NUMBER + 1);

        // then — no Kafka messages for unwatched address
        var records = pollKafkaForKey(UNWATCHED_ADDRESS);
        assertThat(records).isEmpty();

        // then — progress still saved (block processed, just no matching transfers)
        var progress = blockProgressStore.getLastProcessedBlock(ETHEREUM);
        assertThat(progress).isPresent();
        assertThat(progress.getAsLong()).isEqualTo(BLOCK_NUMBER + 1);
    }

    @Test
    @DisplayName("verifies rawAmount, amount, decimals, and tokenSymbol in published event")
    void processBlock_verifyTransferEventFields() {
        // given
        var walletAddress = "0xfedcba0987654321fedcba0987654321fedcba09";
        var wallet = aWalletAddress()
                .id(null)
                .address(walletAddress)
                .networkType(NetworkType.EVM)
                .createdAt(null)
                .updatedAt(null)
                .build();
        walletAddressRepository.save(wallet);
        addressFilter.add(walletAddress, NetworkType.EVM);

        var transfer = aTransfer()
                .toAddress(walletAddress)
                .txHash("0xdeadbeef1234567890abcdef1234567890abcdef1234567890abcdef12345678")
                .rawAmount("5000000")
                .amount(new BigDecimal("5.000000"))
                .decimals(6)
                .tokenSymbol("USDT")
                .tokenContractAddress("0xdac17f958d2ee523a2206206994597c13d831ec7")
                .blockNumber(BLOCK_NUMBER + 2)
                .build();

        var blockResult = aBlockResult()
                .indexedBlock(anIndexedBlock().blockNumber(BLOCK_NUMBER + 2).build())
                .transfers(List.of(transfer))
                .build();

        var chainIndexer = stubChainIndexer(BLOCK_NUMBER + 2, blockResult);

        var worker = new RegularWorker(
                chainIndexer, addressFilter, transferEventPublisher,
                blockProgressStore, walletAddressRepository, meterRegistry,
                Duration.ofSeconds(12), 10, BLOCK_NUMBER + 2);
        worker.start();

        // when
        worker.processBlock(BLOCK_NUMBER + 2);

        // then — verify specific financial fields in the Kafka event
        var records = pollKafkaForKey(walletAddress);
        assertThat(records).isNotEmpty();

        var event = records.getFirst().value();
        assertThat(event.rawAmount()).isEqualTo("5000000");
        assertThat(event.amount()).isEqualByComparingTo(new BigDecimal("5.000000"));
        assertThat(event.decimals()).isEqualTo(6);
        assertThat(event.tokenSymbol()).isEqualTo("USDT");
    }

    private ChainIndexer stubChainIndexer(long blockNumber, BlockResult blockResult) {
        var chainIndexer = mock(ChainIndexer.class);
        given(chainIndexer.indexBlock(blockNumber)).willReturn(blockResult);
        given(chainIndexer.getChainId()).willReturn(ETHEREUM);
        return chainIndexer;
    }

    private List<ConsumerRecord<String, TransferEvent>> pollKafkaForKey(String expectedKey) {
        var records = consumer.poll(Duration.ofSeconds(5));
        return StreamSupport.stream(records.spliterator(), false)
                .filter(record -> expectedKey.equals(record.key()))
                .toList();
    }

    private KafkaConsumer<String, TransferEvent> createKafkaConsumer() {
        var props = Map.<String, Object>of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "e2e-test-" + System.nanoTime(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JacksonJsonDeserializer.class,
                JacksonJsonDeserializer.TRUSTED_PACKAGES, "com.stablebridge.indexer.api",
                JacksonJsonDeserializer.VALUE_DEFAULT_TYPE, TransferEvent.class.getName()
        );
        return new KafkaConsumer<>(props);
    }
}
