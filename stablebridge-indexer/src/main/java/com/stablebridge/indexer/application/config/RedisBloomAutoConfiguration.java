package com.stablebridge.indexer.application.config;

import com.stablebridge.indexer.application.properties.BloomProperties;
import com.stablebridge.indexer.domain.port.WalletAddressRepository;
import com.stablebridge.indexer.infrastructure.bloom.RedisBloomAddressFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Auto-configuration for the Redis Bloom filter-backed address filter.
 *
 * <p>Activated when {@code indexer.bloom.backend=redis} (the production default).
 * This bridges the application configuration layer ({@link BloomProperties}) with the
 * infrastructure adapter ({@link RedisBloomAddressFilter}), keeping the infrastructure
 * layer free of application-layer dependencies (hexagonal architecture).
 */
@Configuration
@ConditionalOnProperty(name = "indexer.bloom.backend", havingValue = "redis")
public class RedisBloomAutoConfiguration {

    @Bean
    RedisBloomAddressFilter redisBloomAddressFilter(
            StringRedisTemplate stringRedisTemplate,
            BloomProperties bloomProperties,
            WalletAddressRepository walletAddressRepository) {
        return new RedisBloomAddressFilter(
                stringRedisTemplate,
                bloomProperties.expectedInsertions(),
                bloomProperties.errorRate(),
                walletAddressRepository
        );
    }
}
