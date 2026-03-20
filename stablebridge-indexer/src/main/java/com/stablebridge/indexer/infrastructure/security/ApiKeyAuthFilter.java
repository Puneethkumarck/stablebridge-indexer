package com.stablebridge.indexer.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.stablebridge.indexer.api.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;

/**
 * Servlet filter that validates the {@code X-API-Key} header on all {@code /api/v1/**} endpoints.
 *
 * <p>Excludes {@code /actuator/**} paths to allow health checks and metrics without authentication.
 *
 * <p>If the API key is missing or invalid, responds with a 401 Unauthorized
 * {@link ErrorResponse} JSON body.
 *
 * <p>The expected API key is passed as a constructor parameter. The application-layer
 * configuration bridges the gap between properties and this infrastructure component,
 * following the same pattern as {@code RedisBloomAddressFilter}.
 */
@Slf4j
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    static final String API_KEY_HEADER = "X-API-Key";
    static final String API_PATH_PREFIX = "/api/v1/";
    static final String ACTUATOR_PATH_PREFIX = "/actuator";

    private final String expectedApiKey;
    private final ObjectMapper objectMapper;

    public ApiKeyAuthFilter(String expectedApiKey) {
        this.expectedApiKey = expectedApiKey;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith(API_PATH_PREFIX) || path.startsWith(ACTUATOR_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String apiKey = request.getHeader(API_KEY_HEADER);

        if (apiKey == null || apiKey.isBlank() || !apiKey.equals(expectedApiKey)) {
            log.warn("Unauthorized API request — path={}, remoteAddr={}",
                    request.getRequestURI(), request.getRemoteAddr());

            ErrorResponse errorResponse = new ErrorResponse(
                    HttpStatus.UNAUTHORIZED.value(),
                    "Unauthorized",
                    "Invalid or missing API key",
                    Instant.now());

            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(), errorResponse);
            return;
        }

        filterChain.doFilter(request, response);
    }
}
