package com.stablebridge.indexer.testutil;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.lifecycle.Startable;
import org.testcontainers.utility.DockerImageName;

public final class TestContainerSupport {

    private TestContainerSupport() {}

    @SuppressWarnings("resource")
    public static PostgreSQLContainer<?> postgres(String databaseName) {
        return new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName(databaseName)
                .withUsername("test")
                .withPassword("test");
    }

    public static KafkaContainer kafka() {
        return new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));
    }

    public static GenericContainer<?> redis() {
        return new GenericContainer<>(DockerImageName.parse("redis/redis-stack-server:latest"))
                .withExposedPorts(6379);
    }

    public static void startAll(Startable... containers) {
        for (var container : containers) {
            try {
                container.start();
            } catch (RuntimeException ex) {
                safeStop(containers);
                throw ex;
            }
        }
        registerShutdownHook(containers);
    }

    public static void registerPostgresProperties(DynamicPropertyRegistry registry,
                                                   PostgreSQLContainer<?> postgres) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    public static void registerKafkaProperties(DynamicPropertyRegistry registry,
                                                KafkaContainer kafka) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    public static void registerRedisProperties(DynamicPropertyRegistry registry,
                                                GenericContainer<?> redis) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    private static void safeStop(Startable... containers) {
        for (var container : containers) {
            try {
                if (container != null) {
                    container.stop();
                }
            } catch (Exception ignored) {
                // best-effort cleanup
            }
        }
    }

    private static void registerShutdownHook(Startable... containers) {
        Runtime.getRuntime().addShutdownHook(new Thread(
                () -> safeStop(containers),
                "testcontainers-shutdown"
        ));
    }
}
