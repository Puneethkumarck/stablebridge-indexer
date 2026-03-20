package com.stablebridge.indexer.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@ExtendWith(MockitoExtension.class)
@DisplayName("ApiKeyAuthFilter")
class ApiKeyAuthFilterTest {

    private static final String VALID_API_KEY = "test-api-key-12345";

    @Mock
    private FilterChain filterChain;

    private ApiKeyAuthFilter filter;

    @BeforeEach
    void setUp() {
        var objectMapper = JsonMapper.builder().build();
        filter = new ApiKeyAuthFilter(VALID_API_KEY, objectMapper);
    }

    @Nested
    @DisplayName("shouldNotFilter")
    class ShouldNotFilter {

        @Test
        @DisplayName("excludes actuator paths from filtering")
        void excludesActuatorPaths() {
            var request = new MockHttpServletRequest();
            request.setRequestURI("/actuator/health");

            var result = filter.shouldNotFilter(request);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("excludes non-API paths from filtering")
        void excludesNonApiPaths() {
            var request = new MockHttpServletRequest();
            request.setRequestURI("/some/other/path");

            var result = filter.shouldNotFilter(request);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("includes API v1 paths for filtering")
        void includesApiV1Paths() {
            var request = new MockHttpServletRequest();
            request.setRequestURI("/api/v1/wallets");

            var result = filter.shouldNotFilter(request);

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("doFilterInternal")
    class DoFilterInternal {

        @Test
        @DisplayName("allows request with valid API key and continues filter chain")
        void allowsRequestWithValidApiKey() throws ServletException, IOException {
            var request = new MockHttpServletRequest();
            request.setRequestURI("/api/v1/wallets");
            request.addHeader("X-API-Key", VALID_API_KEY);
            var response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            then(filterChain).should().doFilter(request, response);
        }

        @Test
        @DisplayName("rejects request without API key with 401 status")
        void rejectsRequestWithoutApiKey() throws ServletException, IOException {
            var request = new MockHttpServletRequest();
            request.setRequestURI("/api/v1/wallets");
            var response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(UNAUTHORIZED.value());
        }

        @Test
        @DisplayName("rejects request with invalid API key with 401 status")
        void rejectsRequestWithInvalidApiKey() throws ServletException, IOException {
            var request = new MockHttpServletRequest();
            request.setRequestURI("/api/v1/wallets");
            request.addHeader("X-API-Key", "wrong-key");
            var response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(UNAUTHORIZED.value());
        }

        @Test
        @DisplayName("rejects request with blank API key with 401 status")
        void rejectsRequestWithBlankApiKey() throws ServletException, IOException {
            var request = new MockHttpServletRequest();
            request.setRequestURI("/api/v1/wallets");
            request.addHeader("X-API-Key", "   ");
            var response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(UNAUTHORIZED.value());
        }

        @Test
        @DisplayName("does not continue filter chain when API key is missing")
        void doesNotContinueFilterChainWhenApiKeyMissing() throws ServletException, IOException {
            var request = new MockHttpServletRequest();
            request.setRequestURI("/api/v1/wallets");
            var response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            then(filterChain).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("returns JSON error response body when API key is missing")
        void returnsJsonErrorResponseWhenApiKeyMissing() throws ServletException, IOException {
            var request = new MockHttpServletRequest();
            request.setRequestURI("/api/v1/wallets");
            var response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            assertThat(response.getContentType()).isEqualTo("application/json");
        }
    }
}
