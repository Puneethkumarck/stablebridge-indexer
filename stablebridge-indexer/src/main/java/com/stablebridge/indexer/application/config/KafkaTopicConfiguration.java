package com.stablebridge.indexer.application.config;

import com.stablebridge.indexer.application.properties.IndexerProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Slf4j
@Configuration
@RequiredArgsConstructor
class KafkaTopicConfiguration {

    private static final String TOPIC_PREFIX = "transfer.events.";
    private static final int DEFAULT_PARTITIONS = 6;
    private static final short DEFAULT_REPLICATION_FACTOR = 1;

    private final IndexerProperties indexerProperties;

    @Bean
    List<NewTopic> transferEventTopics() {
        return indexerProperties.chains().entrySet().stream()
                .filter(entry -> entry.getValue().enabled())
                .map(entry -> {
                    var topicName = TOPIC_PREFIX + entry.getKey();
                    log.info("Auto-creating Kafka topic: {}", topicName);
                    return new NewTopic(topicName, DEFAULT_PARTITIONS, DEFAULT_REPLICATION_FACTOR);
                })
                .toList();
    }
}
