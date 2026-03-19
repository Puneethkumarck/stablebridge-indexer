package com.stablebridge.indexer.domain.event;

import com.stablebridge.indexer.domain.model.Transfer;
import lombok.Builder;

import java.time.Instant;

@Builder
public record TransferDetectedEvent(
        Transfer transfer,
        Instant detectedAt) {}
