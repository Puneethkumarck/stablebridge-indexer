package com.stablebridge.indexer.client;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ApiKeyRequestInterceptor implements RequestInterceptor {

    private static final String API_KEY_HEADER = "X-API-Key";

    private final String apiKey;

    @Override
    public void apply(RequestTemplate template) {
        template.header(API_KEY_HEADER, apiKey);
    }
}
