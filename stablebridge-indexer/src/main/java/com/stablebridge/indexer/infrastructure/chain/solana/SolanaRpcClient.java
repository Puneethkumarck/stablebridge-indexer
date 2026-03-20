package com.stablebridge.indexer.infrastructure.chain.solana;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class SolanaRpcClient {

    private static final String CONTENT_TYPE = "application/json";
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private static final Map<String, String> FINALIZED_COMMITMENT = Map.of("commitment", "finalized");
    private static final Map<String, Object> BLOCK_REQUEST_CONFIG = Map.of(
            "encoding", "jsonParsed",
            "maxSupportedTransactionVersion", 0,
            "commitment", "finalized"
    );
    private static final Map<String, Object> TRANSACTION_REQUEST_CONFIG = Map.of(
            "encoding", "jsonParsed",
            "maxSupportedTransactionVersion", 0,
            "commitment", "finalized"
    );

    private final HttpClient httpClient;
    private final URI rpcUri;
    private final Duration timeout;
    private final AtomicLong requestIdCounter = new AtomicLong(1);

    public SolanaRpcClient(String rpcUrl, Duration timeout) {
        this.rpcUri = URI.create(rpcUrl);
        this.timeout = timeout;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .connectTimeout(timeout)
                .build();
    }

    public long getLatestSlot() {
        var request = JsonRpcRequest.of("getSlot", List.of(FINALIZED_COMMITMENT), nextId());
        return sendSingleRequest(request, Long.class);
    }

    public SolanaBlock getBlock(long slot) {
        var request = JsonRpcRequest.of("getBlock", List.of(slot, BLOCK_REQUEST_CONFIG), nextId());
        return sendSingleRequest(request, SolanaBlock.class);
    }

    public SolanaTransaction getTransaction(String signature) {
        var request = JsonRpcRequest.of(
                "getTransaction", List.of(signature, TRANSACTION_REQUEST_CONFIG), nextId());
        return sendSingleRequest(request, SolanaTransaction.class);
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
            throw SolanaRpcException.parseError(request.method(), e);
        }
        validateResponse(rpcResponse, request.method());
        return rpcResponse.result();
    }

    private String serializeRequest(Object request) {
        try {
            return JSON_MAPPER.writeValueAsString(request);
        } catch (Exception e) {
            throw SolanaRpcException.parseError("serialize", e);
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
                throw SolanaRpcException.httpError(method, response.statusCode());
            }
            return response.body();
        } catch (SolanaRpcException e) {
            throw e;
        } catch (IOException e) {
            log.error("RPC call failed: method={}, error={}", method, e.getMessage());
            throw SolanaRpcException.networkError(method, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("RPC call interrupted: method={}, error={}", method, e.getMessage());
            throw SolanaRpcException.networkError(method, e);
        }
    }

    private <T> void validateResponse(JsonRpcResponse<T> response, String method) {
        if (response.error() != null) {
            log.error("RPC call failed: method={}, error={}", method, response.error().message());
            throw SolanaRpcException.rpcError(method, response.error().code(), response.error().message());
        }
    }

    private long nextId() {
        return requestIdCounter.getAndIncrement();
    }
}
