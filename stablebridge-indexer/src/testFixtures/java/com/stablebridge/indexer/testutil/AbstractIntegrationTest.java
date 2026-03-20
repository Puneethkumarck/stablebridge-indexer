package com.stablebridge.indexer.testutil;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import static com.stablebridge.indexer.testutil.TestContainerSupport.kafka;
import static com.stablebridge.indexer.testutil.TestContainerSupport.postgres;
import static com.stablebridge.indexer.testutil.TestContainerSupport.redis;
import static com.stablebridge.indexer.testutil.TestContainerSupport.registerKafkaProperties;
import static com.stablebridge.indexer.testutil.TestContainerSupport.registerPostgresProperties;
import static com.stablebridge.indexer.testutil.TestContainerSupport.registerRedisProperties;
import static com.stablebridge.indexer.testutil.TestContainerSupport.startAll;

@SuppressWarnings("resource")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = postgres("stablebridge_indexer_test");
    protected static final KafkaContainer KAFKA = kafka();
    protected static final GenericContainer<?> REDIS = redis();

    static {
        startAll(POSTGRES, KAFKA, REDIS);
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registerPostgresProperties(registry, POSTGRES);
        registerKafkaProperties(registry, KAFKA);
        registerRedisProperties(registry, REDIS);
    }
}
