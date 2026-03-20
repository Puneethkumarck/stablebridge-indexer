package com.stablebridge.indexer.application.controller;

import com.stablebridge.indexer.api.BloomStatusResponse;
import com.stablebridge.indexer.api.ErrorResponse;
import com.stablebridge.indexer.api.IndexerStatusResponse;
import com.stablebridge.indexer.domain.service.StatusQueryHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping("/api/v1/status")
@RequiredArgsConstructor
public class StatusController {

    private final StatusQueryHandler statusQueryHandler;
    private final StatusControllerMapper mapper;

    @GetMapping
    public ResponseEntity<List<IndexerStatusResponse>> getAllChainStatuses() {
        var statuses = statusQueryHandler.getAllChainStatuses();
        return ResponseEntity.ok(mapper.toResponseList(statuses));
    }

    @GetMapping("/{chainName}")
    public ResponseEntity<?> getChainStatus(@PathVariable String chainName) {
        var status = statusQueryHandler.getChainStatus(chainName);
        if (status.isEmpty()) {
            var error = new ErrorResponse(
                    NOT_FOUND.value(),
                    NOT_FOUND.getReasonPhrase(),
                    "Chain not configured: " + chainName,
                    Instant.now()
            );
            return ResponseEntity.status(NOT_FOUND).body(error);
        }
        return ResponseEntity.ok(mapper.toResponse(status.get()));
    }

    @GetMapping("/bloom")
    public ResponseEntity<BloomStatusResponse> getBloomStatus() {
        var bloomStatus = statusQueryHandler.getBloomStatus();
        return ResponseEntity.ok(mapper.toBloomResponse(bloomStatus));
    }
}
