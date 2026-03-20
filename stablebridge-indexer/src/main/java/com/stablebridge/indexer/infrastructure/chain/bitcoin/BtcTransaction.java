package com.stablebridge.indexer.infrastructure.chain.bitcoin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;

import java.util.List;

@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
record BtcTransaction(
        String txid,
        List<BtcVin> vin,
        List<BtcVout> vout) {}
