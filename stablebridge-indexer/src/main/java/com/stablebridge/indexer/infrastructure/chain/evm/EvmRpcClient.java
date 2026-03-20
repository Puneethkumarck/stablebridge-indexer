package com.stablebridge.indexer.infrastructure.chain.evm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
class EvmRpcClient {

    private static final String CONTENT_TYPE = "application/json";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI rpcUri;
    private final int batchSize;
    private final boolean useBlockReceipts;
    private final Duration timeout;
    private final AtomicLong requestIdCounter = new AtomicLong(1);

    EvmRpcClient(String rpcUrl, int batchSize, boolean useBlockReceipts,
                 Duration timeout, ObjectMapper objectMapper) {
        this.rpcUri = URI.create(rpcUrl);
        this.batchSize = batchSize;
        this.useBlockReceipts = useBlockReceipts;
        this.timeout = timeout;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .connectTimeout(timeout)
                .build();
    }

    long getLatestBlockNumber() {
        var request = JsonRpcRequest.of("eth_blockNumber", List.of(), nextId());
        var response = sendSingleRequest(request, String.class);
        return HexUtils.hexToLong(response);
    }

    EvmBlock getBlockByNumber(long blockNumber) {
        var hexBlockNumber = HexUtils.longToHex(blockNumber);
        var request = JsonRpcRequest.of(
                "eth_getBlockByNumber", List.of(hexBlockNumber, true), nextId());
        return sendSingleRequest(request, EvmBlock.class);
    }

    List<EvmReceipt> getTransactionReceipts(List<String> txHashes) {
        if (txHashes == null || txHashes.isEmpty()) {
            return List.of();
        }

        var allReceipts = new ArrayList<EvmReceipt>();
        for (var i = 0; i < txHashes.size(); i += batchSize) {
            var batchEnd = Math.min(i + batchSize, txHashes.size());
            var batch = txHashes.subList(i, batchEnd);
            var batchReceipts = sendBatchReceiptRequests(batch);
            allReceipts.addAll(batchReceipts);
        }
        return List.copyOf(allReceipts);
    }

    List<EvmReceipt> getBlockReceipts(long blockNumber) {
        var hexBlockNumber = HexUtils.longToHex(blockNumber);
        var request = JsonRpcRequest.of(
                "eth_getBlockReceipts", List.of(hexBlockNumber), nextId());

        var body = serializeRequest(request);
        var httpResponse = executeHttpPost(body, "eth_getBlockReceipts");

        try {
            var rpcResponse = objectMapper.readValue(
                    httpResponse,
                    new TypeReference<JsonRpcResponse<List<EvmReceipt>>>() {});
            validateResponse(rpcResponse, "eth_getBlockReceipts");
            return rpcResponse.result() != null ? rpcResponse.result() : List.of();
        } catch (EvmRpcException e) {
            throw e;
        } catch (JsonProcessingException e) {
            throw EvmRpcException.parseError("eth_getBlockReceipts", e);
        }
    }

    boolean supportsBlockReceipts() {
        return useBlockReceipts;
    }

    private List<EvmReceipt> sendBatchReceiptRequests(List<String> txHashes) {
        var requests = new ArrayList<JsonRpcRequest>(txHashes.size());
        for (var txHash : txHashes) {
            requests.add(JsonRpcRequest.of(
                    "eth_getTransactionReceipt", List.of(txHash), nextId()));
        }

        var body = serializeRequest(requests);
        var httpResponse = executeHttpPost(body, "eth_getTransactionReceipt[batch]");

        try {
            var responses = objectMapper.readValue(
                    httpResponse,
                    new TypeReference<List<JsonRpcResponse<EvmReceipt>>>() {});

            var receipts = new ArrayList<EvmReceipt>(responses.size());
            for (var response : responses) {
                validateResponse(response, "eth_getTransactionReceipt");
                if (response.result() != null) {
                    receipts.add(response.result());
                }
            }
            return receipts;
        } catch (EvmRpcException e) {
            throw e;
        } catch (JsonProcessingException e) {
            throw EvmRpcException.parseError("eth_getTransactionReceipt[batch]", e);
        }
    }

    private <T> T sendSingleRequest(JsonRpcRequest request, Class<T> resultType) {
        var body = serializeRequest(request);
        var httpResponse = executeHttpPost(body, request.method());

        try {
            var typeRef = objectMapper.getTypeFactory()
                    .constructParametricType(JsonRpcResponse.class, resultType);
            JsonRpcResponse<T> rpcResponse = objectMapper.readValue(httpResponse, typeRef);
            validateResponse(rpcResponse, request.method());
            return rpcResponse.result();
        } catch (EvmRpcException e) {
            throw e;
        } catch (JsonProcessingException e) {
            throw EvmRpcException.parseError(request.method(), e);
        }
    }

    private String serializeRequest(Object request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw EvmRpcException.parseError("serialize", e);
        }
    }

    private String executeHttpPost(String body, String method) {
        var httpRequest = HttpRequest.newBuilder()
                .uri(rpcUri)
                .timeout(timeout)
                .header("Content-Type", CONTENT_TYPE)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        try {
            var response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.error("RPC call failed: method={}, statusCode={}", method, response.statusCode());
                throw EvmRpcException.httpError(method, response.statusCode());
            }
            return response.body();
        } catch (EvmRpcException e) {
            throw e;
        } catch (IOException e) {
            log.error("RPC call failed: method={}, error={}", method, e.getMessage());
            throw EvmRpcException.networkError(method, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("RPC call interrupted: method={}, error={}", method, e.getMessage());
            throw EvmRpcException.networkError(method, e);
        }
    }

    private <T> void validateResponse(JsonRpcResponse<T> response, String method) {
        if (response.error() != null) {
            log.error("RPC call failed: method={}, error={}", method, response.error().message());
            throw EvmRpcException.rpcError(method, response.error().code(), response.error().message());
        }
    }

    private long nextId() {
        return requestIdCounter.getAndIncrement();
    }
}
