package com.stablebridge.indexer.application.controller;

import com.stablebridge.indexer.api.BloomStatusResponse;
import com.stablebridge.indexer.api.ErrorResponse;
import com.stablebridge.indexer.api.IndexerStatusResponse;
import com.stablebridge.indexer.domain.model.BloomStatus;
import com.stablebridge.indexer.domain.model.ChainStatus;
import com.stablebridge.indexer.domain.service.StatusQueryHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * REST controller for indexer status endpoints.
 *
 * <p>Provides read-only views of chain indexing progress and bloom filter configuration.
 * These endpoints are intended for monitoring dashboards and operational tooling.
 */
@RestController
@RequestMapping("/api/v1/status")
@RequiredArgsConstructor
public class StatusController {

    private final StatusQueryHandler statusQueryHandler;
    private final StatusControllerMapper mapper;

    /**
     * Returns the status of all configured chains.
     *
     * @return 200 OK with list of chain statuses
     */
    @GetMapping
    public ResponseEntity<List<IndexerStatusResponse>> getAllChainStatuses() {
        List<ChainStatus> statuses = statusQueryHandler.getAllChainStatuses();
        return ResponseEntity.ok(mapper.toResponseList(statuses));
    }

    /**
     * Returns the status of a single chain by name.
     *
     * @param chainName the chain network identifier (e.g., {@code "ethereum_mainnet"})
     * @return 200 OK with chain status, or 404 if chain is not configured
     */
    @GetMapping("/{chainName}")
    public ResponseEntity<?> getChainStatus(@PathVariable String chainName) {
        Optional<ChainStatus> status = statusQueryHandler.getChainStatus(chainName);
        if (status.isEmpty()) {
            ErrorResponse error = new ErrorResponse(
                    HttpStatus.NOT_FOUND.value(),
                    HttpStatus.NOT_FOUND.getReasonPhrase(),
                    "Chain not configured: " + chainName,
                    Instant.now()
            );
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }
        return ResponseEntity.ok(mapper.toResponse(status.get()));
    }

    /**
     * Returns the bloom filter status and configuration.
     *
     * @return 200 OK with bloom filter status
     */
    @GetMapping("/bloom")
    public ResponseEntity<BloomStatusResponse> getBloomStatus() {
        BloomStatus bloomStatus = statusQueryHandler.getBloomStatus();
        return ResponseEntity.ok(mapper.toBloomResponse(bloomStatus));
    }
}
