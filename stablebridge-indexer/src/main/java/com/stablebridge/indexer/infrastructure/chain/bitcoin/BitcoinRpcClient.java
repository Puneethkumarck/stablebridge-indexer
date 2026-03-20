package com.stablebridge.indexer.infrastructure.chain.bitcoin;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class BitcoinRpcClient {

    private static final String CONTENT_TYPE = "application/json";
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private final HttpClient httpClient;
    private final URI rpcUri;
    private final String authHeader;
    private final Duration timeout;
    private final AtomicLong requestIdCounter = new AtomicLong(1);

    public BitcoinRpcClient(String rpcUrl, String username, String password, Duration timeout) {
        this.rpcUri = URI.create(rpcUrl);
        this.timeout = timeout;
        this.authHeader = buildBasicAuthHeader(username, password);
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .connectTimeout(timeout)
                .build();
    }

    public long getBlockCount() {
        var request = JsonRpcRequest.of("getblockcount", List.of(), nextId());
        return sendSingleRequest(request, Long.class);
    }

    public String getBlockHash(long height) {
        var request = JsonRpcRequest.of("getblockhash", List.of(height), nextId());
        return sendSingleRequest(request, String.class);
    }

    public BtcBlock getBlock(String hash) {
        var request = JsonRpcRequest.of("getblock", List.of(hash, 2), nextId());
        return sendSingleRequest(request, BtcBlock.class);
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
            throw BitcoinRpcException.parseError(request.method(), e);
        }
        validateResponse(rpcResponse, request.method());
        return rpcResponse.result();
    }

    private String serializeRequest(Object request) {
        try {
            return JSON_MAPPER.writeValueAsString(request);
        } catch (Exception e) {
            throw BitcoinRpcException.parseError("serialize", e);
        }
    }

    private String executeHttpPost(String body, String method) {
        var httpRequest = HttpRequest.newBuilder()
                .uri(rpcUri)
                .timeout(timeout)
                .header("Content-Type", CONTENT_TYPE)
                .header("Authorization", authHeader)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        try {
            var response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.error("RPC call failed: method={}, statusCode={}", method, response.statusCode());
                throw BitcoinRpcException.httpError(method, response.statusCode());
            }
            return response.body();
        } catch (BitcoinRpcException e) {
            throw e;
        } catch (IOException e) {
            log.error("RPC call failed: method={}, error={}", method, e.getMessage());
            throw BitcoinRpcException.networkError(method, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("RPC call interrupted: method={}, error={}", method, e.getMessage());
            throw BitcoinRpcException.networkError(method, e);
        }
    }

    private <T> void validateResponse(JsonRpcResponse<T> response, String method) {
        if (response.error() != null) {
            log.error("RPC call failed: method={}, error={}", method, response.error().message());
            throw BitcoinRpcException.rpcError(method, response.error().code(), response.error().message());
        }
    }

    private long nextId() {
        return requestIdCounter.getAndIncrement();
    }

    private static String buildBasicAuthHeader(String username, String password) {
        var credentials = username + ":" + password;
        var encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }
}
