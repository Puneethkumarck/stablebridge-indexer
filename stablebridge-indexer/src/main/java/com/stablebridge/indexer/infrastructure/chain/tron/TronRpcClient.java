package com.stablebridge.indexer.infrastructure.chain.tron;

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
import java.util.Map;
import java.util.concurrent.Executors;

@Slf4j
class TronRpcClient {

    private static final String CONTENT_TYPE = "application/json";
    private static final String API_KEY_HEADER = "TRON-PRO-API-KEY";
    private static final int MAX_BLOCK_RANGE = 100;
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private final HttpClient httpClient;
    private final URI baseUri;
    private final String apiKey;
    private final Duration timeout;

    TronRpcClient(String baseUrl, String apiKey, Duration timeout) {
        this.baseUri = URI.create(baseUrl);
        this.apiKey = apiKey;
        this.timeout = timeout;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .connectTimeout(timeout)
                .build();
    }

    long getLatestSolidifiedBlockNumber() {
        var endpoint = "/walletsolidity/getnowblock";
        var body = serializeRequest(Map.of());
        var responseBody = executeHttpPost(endpoint, body);
        var block = parseResponse(responseBody, endpoint, TronBlock.class);
        return block.blockNumber();
    }

    TronBlock getBlockByNumber(long blockNumber) {
        var endpoint = "/walletsolidity/getblockbynum";
        var body = serializeRequest(Map.of("num", blockNumber, "visible", true));
        var responseBody = executeHttpPost(endpoint, body);
        return parseResponse(responseBody, endpoint, TronBlock.class);
    }

    List<TronTransactionInfo> getTransactionInfoByBlockNum(long blockNumber) {
        var endpoint = "/walletsolidity/gettransactioninfobyblocknum";
        var body = serializeRequest(Map.of("num", blockNumber));
        var responseBody = executeHttpPost(endpoint, body);
        return parseListResponse(responseBody, endpoint);
    }

    List<TronBlock> getBlocksByRange(long startNum, long endNum) {
        if (endNum - startNum > MAX_BLOCK_RANGE) {
            throw new IllegalArgumentException(
                    "Block range cannot exceed %d, requested: %d to %d".formatted(MAX_BLOCK_RANGE, startNum, endNum));
        }
        var endpoint = "/wallet/getblockbylimitnext";
        var body = serializeRequest(Map.of("startNum", startNum, "endNum", endNum, "visible", true));
        var responseBody = executeHttpPost(endpoint, body);
        var result = parseResponse(responseBody, endpoint, TronBlockList.class);
        return result.block() != null ? result.block() : List.of();
    }

    private <T> T parseResponse(String responseBody, String endpoint, Class<T> responseType) {
        checkForApiError(responseBody, endpoint);
        try {
            return JSON_MAPPER.readValue(responseBody, responseType);
        } catch (Exception e) {
            throw TronRpcException.parseError(endpoint, e);
        }
    }

    private List<TronTransactionInfo> parseListResponse(String responseBody, String endpoint) {
        checkForApiError(responseBody, endpoint);
        try {
            return JSON_MAPPER.readValue(responseBody, new TypeReference<>() {});
        } catch (Exception e) {
            throw TronRpcException.parseError(endpoint, e);
        }
    }

    private void checkForApiError(String responseBody, String endpoint) {
        if (responseBody == null || responseBody.isBlank()) {
            return;
        }
        try {
            var tree = JSON_MAPPER.readTree(responseBody);
            if (tree.isObject()) {
                var errorField = tree.get("Error");
                if (errorField != null && !errorField.isNull()) {
                    throw TronRpcException.rpcError(endpoint, errorField.asText());
                }
            }
        } catch (TronRpcException e) {
            throw e;
        } catch (Exception ignored) {
            // Not parseable as tree — will fail on actual parse below
        }
    }

    private String serializeRequest(Map<String, Object> params) {
        try {
            return JSON_MAPPER.writeValueAsString(params);
        } catch (Exception e) {
            throw TronRpcException.parseError("serialize", e);
        }
    }

    private String executeHttpPost(String endpoint, String body) {
        var uri = baseUri.resolve(endpoint);
        var requestBuilder = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(timeout)
                .header("Content-Type", CONTENT_TYPE)
                .POST(HttpRequest.BodyPublishers.ofString(body));

        if (apiKey != null && !apiKey.isBlank()) {
            requestBuilder.header(API_KEY_HEADER, apiKey);
        }

        var httpRequest = requestBuilder.build();
        log.debug("TRON API request: endpoint={}, body={}", endpoint, body);

        try {
            var response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            log.debug("TRON API response: endpoint={}, statusCode={}, length={}",
                    endpoint, response.statusCode(), response.body().length());
            if (response.statusCode() != 200) {
                log.error("TRON API call failed: endpoint={}, statusCode={}", endpoint, response.statusCode());
                throw TronRpcException.httpError(endpoint, response.statusCode());
            }
            return response.body();
        } catch (TronRpcException e) {
            throw e;
        } catch (IOException e) {
            log.error("TRON API call failed: endpoint={}, error={}", endpoint, e.getMessage());
            throw TronRpcException.networkError(endpoint, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("TRON API call interrupted: endpoint={}, error={}", endpoint, e.getMessage());
            throw TronRpcException.networkError(endpoint, e);
        }
    }
}
