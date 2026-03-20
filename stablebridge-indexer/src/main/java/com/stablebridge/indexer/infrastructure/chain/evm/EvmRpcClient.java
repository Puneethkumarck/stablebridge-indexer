package com.stablebridge.indexer.infrastructure.chain.evm;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

@Slf4j
class EvmRpcClient {

    private static final String CONTENT_TYPE = "application/json";
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private final HttpClient httpClient;
    private final URI rpcUri;
    private final int batchSize;
    private final boolean useBlockReceipts;
    private final Duration timeout;
    private final AtomicLong requestIdCounter = new AtomicLong(1);

    EvmRpcClient(String rpcUrl, int batchSize, boolean useBlockReceipts, Duration timeout) {
        this.rpcUri = URI.create(rpcUrl);
        this.batchSize = batchSize;
        this.useBlockReceipts = useBlockReceipts;
        this.timeout = timeout;
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

        return IntStream.iterate(0, i -> i < txHashes.size(), i -> i + batchSize)
                .mapToObj(i -> txHashes.subList(i, Math.min(i + batchSize, txHashes.size())))
                .map(this::sendBatchReceiptRequests)
                .flatMap(List::stream)
                .toList();
    }

    List<EvmReceipt> getBlockReceipts(long blockNumber) {
        var hexBlockNumber = HexUtils.longToHex(blockNumber);
        var request = JsonRpcRequest.of(
                "eth_getBlockReceipts", List.of(hexBlockNumber), nextId());

        var body = serializeRequest(request);
        var httpResponse = executeHttpPost(body, "eth_getBlockReceipts");

        final JsonRpcResponse<List<EvmReceipt>> rpcResponse;
        try {
            rpcResponse = JSON_MAPPER.readValue(
                    httpResponse,
                    new TypeReference<JsonRpcResponse<List<EvmReceipt>>>() {});
        } catch (Exception e) {
            throw EvmRpcException.parseError("eth_getBlockReceipts", e);
        }
        validateResponse(rpcResponse, "eth_getBlockReceipts");
        return rpcResponse.result() != null ? rpcResponse.result() : List.of();
    }

    boolean supportsBlockReceipts() {
        return useBlockReceipts;
    }

    private List<EvmReceipt> sendBatchReceiptRequests(List<String> txHashes) {
        var requests = txHashes.stream()
                .map(txHash -> JsonRpcRequest.of("eth_getTransactionReceipt", List.of(txHash), nextId()))
                .toList();

        var body = serializeRequest(requests);
        var httpResponse = executeHttpPost(body, "eth_getTransactionReceipt[batch]");

        final List<JsonRpcResponse<EvmReceipt>> responses;
        try {
            responses = JSON_MAPPER.readValue(
                    httpResponse,
                    new TypeReference<List<JsonRpcResponse<EvmReceipt>>>() {});
        } catch (Exception e) {
            throw EvmRpcException.parseError("eth_getTransactionReceipt[batch]", e);
        }

        return responses.stream()
                .peek(response -> validateResponse(response, "eth_getTransactionReceipt"))
                .map(JsonRpcResponse::result)
                .filter(Objects::nonNull)
                .toList();
    }

    private <T> T sendSingleRequest(JsonRpcRequest request, Class<T> resultType) {
        var body = serializeRequest(request);
        var httpResponse = executeHttpPost(body, request.method());

        var typeRef = JSON_MAPPER.getTypeFactory()
                .constructParametricType(JsonRpcResponse.class, resultType);
        final JsonRpcResponse<T> rpcResponse;
        try {
            rpcResponse = JSON_MAPPER.readValue(httpResponse, typeRef);
        } catch (Exception e) {
            throw EvmRpcException.parseError(request.method(), e);
        }
        validateResponse(rpcResponse, request.method());
        return rpcResponse.result();
    }

    private String serializeRequest(Object request) {
        try {
            return JSON_MAPPER.writeValueAsString(request);
        } catch (Exception e) {
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
