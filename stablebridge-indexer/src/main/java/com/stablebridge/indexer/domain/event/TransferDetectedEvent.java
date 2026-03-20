package com.stablebridge.indexer.domain.event;

import com.stablebridge.indexer.domain.model.Transfer;
import com.stablebridge.indexer.domain.model.TransferDirection;
import lombok.Builder;

import java.time.Instant;

@Builder(toBuilder = true)
public record TransferDetectedEvent(
        Transfer transfer,
        TransferDirection direction,
        Instant detectedAt) {}
