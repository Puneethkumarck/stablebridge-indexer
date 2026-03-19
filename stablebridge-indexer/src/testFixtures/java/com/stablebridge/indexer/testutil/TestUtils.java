package com.stablebridge.indexer.testutil;

import org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration;
import org.mockito.ArgumentMatcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;

/**
 * Test utilities providing custom Mockito argument matchers that use AssertJ recursive comparison.
 *
 * <p>These matchers enforce the project coding standard: NEVER use {@code any()}, {@code anyString()},
 * {@code eq()} or similar generic Mockito matchers. Always pass actual values in both stubs
 * ({@code given()}) and verifications ({@code then().should()}).
 *
 * <p>Only allowed matchers:
 * <ul>
 *   <li>{@link #eqIgnoringTimestamps(Object)} — ignores common timestamp fields</li>
 *   <li>{@link #eqIgnoring(Object, String...)} — ignores caller-specified fields</li>
 * </ul>
 */
public final class TestUtils {

    private static final String[] TIMESTAMP_FIELDS = {
            "createdAt", "updatedAt", "detectedAt", "timestamp"
    };

    private TestUtils() {
        // utility class
    }

    /**
     * Custom Mockito argument matcher that compares using AssertJ recursive comparison,
     * ignoring common Instant/timestamp fields: {@code createdAt}, {@code updatedAt},
     * {@code detectedAt}, {@code timestamp}.
     *
     * <p>Usage in BDDMockito:
     * <pre>{@code
     * given(port.save(eqIgnoringTimestamps(expectedWallet))).willReturn(saved);
     * then(port).should().save(eqIgnoringTimestamps(expectedWallet));
     * }</pre>
     *
     * @param expected the expected object to compare against
     * @param <T>      the type of the argument
     * @return the expected value wrapped in a Mockito {@code argThat()} matcher
     */
    public static <T> T eqIgnoringTimestamps(T expected) {
        return argThat(new RecursiveComparisonMatcher<>(expected, TIMESTAMP_FIELDS));
    }

    /**
     * Custom Mockito argument matcher that compares using AssertJ recursive comparison,
     * ignoring the caller-specified fields.
     *
     * <p>Usage in BDDMockito:
     * <pre>{@code
     * given(port.save(eqIgnoring(expectedWallet, "id", "createdAt"))).willReturn(saved);
     * then(port).should().save(eqIgnoring(expectedWallet, "id", "createdAt"));
     * }</pre>
     *
     * @param expected the expected object to compare against
     * @param fields   the field names to ignore during comparison
     * @param <T>      the type of the argument
     * @return the expected value wrapped in a Mockito {@code argThat()} matcher
     */
    public static <T> T eqIgnoring(T expected, String... fields) {
        return argThat(new RecursiveComparisonMatcher<>(expected, fields));
    }

    /**
     * Mockito {@link ArgumentMatcher} that delegates to AssertJ recursive comparison
     * with specified fields ignored.
     */
    private static final class RecursiveComparisonMatcher<T> implements ArgumentMatcher<T> {

        private final T expected;
        private final String[] ignoredFields;

        RecursiveComparisonMatcher(T expected, String[] ignoredFields) {
            this.expected = expected;
            this.ignoredFields = ignoredFields;
        }

        @Override
        public boolean matches(T actual) {
            if (actual == null && expected == null) {
                return true;
            }
            if (actual == null || expected == null) {
                return false;
            }

            RecursiveComparisonConfiguration configuration = RecursiveComparisonConfiguration.builder()
                    .withIgnoredFields(ignoredFields)
                    .build();

            return !assertThat(actual)
                    .usingRecursiveComparison(configuration)
                    .isEqualTo(expected)
                    .equals(null); // always returns true if assertion passed (no exception)
        }

        @Override
        public String toString() {
            return "eqIgnoring(" + expected + ", fields=" + String.join(", ", ignoredFields) + ")";
        }
    }
}
