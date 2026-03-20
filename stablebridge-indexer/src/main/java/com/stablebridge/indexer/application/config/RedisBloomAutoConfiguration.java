package com.stablebridge.indexer.application.config;

import com.stablebridge.indexer.application.properties.IndexerProperties;
import com.stablebridge.indexer.domain.port.WalletAddressRepository;
import com.stablebridge.indexer.infrastructure.bloom.RedisBloomAddressFilter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
@ConditionalOnProperty(name = "indexer.bloom.backend", havingValue = "redis")
public class RedisBloomAutoConfiguration {

    @Bean
    RedisBloomAddressFilter redisBloomAddressFilter(
            StringRedisTemplate stringRedisTemplate,
            IndexerProperties indexerProperties,
            WalletAddressRepository walletAddressRepository,
            MeterRegistry meterRegistry) {
        var bloom = indexerProperties.bloom();
        return new RedisBloomAddressFilter(
                stringRedisTemplate,
                bloom.expectedInsertions(),
                bloom.errorRate(),
                walletAddressRepository,
                meterRegistry
        );
    }
}
