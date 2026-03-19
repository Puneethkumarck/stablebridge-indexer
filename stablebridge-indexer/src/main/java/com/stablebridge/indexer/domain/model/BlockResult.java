package com.stablebridge.indexer.domain.model;

import lombok.Builder;

import java.util.List;

@Builder
public record BlockResult(
        IndexedBlock indexedBlock,
        List<Transfer> transfers) {}
