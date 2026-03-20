package com.stablebridge.indexer.api;

import lombok.Builder;

import java.time.Instant;

@Builder
public record ErrorResponse(
        int status,
        String error,
        String message,
        Instant timestamp) {}
