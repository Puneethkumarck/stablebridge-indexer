package com.stablebridge.indexer.client;

import feign.RequestTemplate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyRequestInterceptorTest {

    private static final String API_KEY = "test-api-key-12345";

    private final ApiKeyRequestInterceptor interceptor = new ApiKeyRequestInterceptor(API_KEY);

    @Test
    void shouldAddApiKeyHeader() {
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        assertThat(template.headers().get("X-API-Key")).containsExactly(API_KEY);
    }

    @Test
    void shouldNotOverwriteExistingHeaders() {
        RequestTemplate template = new RequestTemplate();
        template.header("Content-Type", "application/json");

        interceptor.apply(template);

        assertThat(template.headers().get("X-API-Key")).containsExactly(API_KEY);
        assertThat(template.headers().get("Content-Type")).containsExactly("application/json");
    }
}
