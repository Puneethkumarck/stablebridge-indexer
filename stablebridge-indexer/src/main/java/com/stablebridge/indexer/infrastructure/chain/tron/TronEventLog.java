package com.stablebridge.indexer.infrastructure.chain.tron;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;

import java.util.List;

@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
record TronEventLog(
        String address,
        List<String> topics,
        String data) {}
