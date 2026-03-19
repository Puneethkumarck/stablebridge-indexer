package com.stablebridge.indexer.testutil;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for integration tests that require a real PostgreSQL database.
 *
 * <p>Provides a shared PostgreSQL Testcontainer that is started once and reused across
 * all integration test classes that extend this base. Spring Boot datasource properties
 * are automatically wired via {@link DynamicPropertySource}.
 *
 * <p>Subclasses should be placed in the {@code src/integration-test/java} source set and
 * will automatically inherit the container configuration.
 *
 * <p>Usage:
 * <pre>{@code
 * class WalletRepositoryIntegrationTest extends AbstractIntegrationTest {
 *
 *     @Autowired
 *     private WalletAddressRepository repository;
 *
 *     @Test
 *     void shouldSaveAndFindWalletAddress() {
 *         // test with real PostgreSQL
 *     }
 * }
 * }</pre>
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("integration-test")
public abstract class AbstractIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("stablebridge_indexer_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL datasource
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");

        // Flyway
        registry.add("spring.flyway.enabled", () -> "true");

        // JPA
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");

        // Disable Redis auto-configuration for integration tests that only need PostgreSQL
        registry.add("spring.data.redis.repositories.enabled", () -> "false");

        // Disable Kafka auto-configuration for integration tests that only need PostgreSQL
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9092");
    }
}
