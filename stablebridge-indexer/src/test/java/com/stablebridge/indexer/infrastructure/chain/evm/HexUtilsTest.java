package com.stablebridge.indexer.infrastructure.chain.evm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("HexUtils")
class HexUtilsTest {

    @Nested
    @DisplayName("hexToLong")
    class HexToLong {

        @Test
        @DisplayName("converts hex string with 0x prefix to long")
        void convertsHexWithPrefix() {
            // given
            var hex = "0x1234";

            // when
            var result = HexUtils.hexToLong(hex);

            // then
            assertThat(result).isEqualTo(4660L);
        }

        @Test
        @DisplayName("converts hex string with 0X prefix to long")
        void convertsHexWithUppercasePrefix() {
            // given
            var hex = "0X1234";

            // when
            var result = HexUtils.hexToLong(hex);

            // then
            assertThat(result).isEqualTo(4660L);
        }

        @Test
        @DisplayName("converts hex string without prefix to long")
        void convertsHexWithoutPrefix() {
            // given
            var hex = "ff";

            // when
            var result = HexUtils.hexToLong(hex);

            // then
            assertThat(result).isEqualTo(255L);
        }

        @Test
        @DisplayName("converts zero hex to zero")
        void convertsZero() {
            // given
            var hex = "0x0";

            // when
            var result = HexUtils.hexToLong(hex);

            // then
            assertThat(result).isZero();
        }

        @Test
        @DisplayName("converts large hex block number")
        void convertsLargeBlockNumber() {
            // given
            var hex = "0x10d4f1";

            // when
            var result = HexUtils.hexToLong(hex);

            // then
            assertThat(result).isEqualTo(1_103_089L);
        }

        @Test
        @DisplayName("throws IllegalArgumentException for null input")
        void throwsForNull() {
            // given / when / then
            assertThatThrownBy(() -> HexUtils.hexToLong(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("throws IllegalArgumentException for blank input")
        void throwsForBlank() {
            // given / when / then
            assertThatThrownBy(() -> HexUtils.hexToLong("  "))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("longToHex")
    class LongToHex {

        @Test
        @DisplayName("converts long to hex string with 0x prefix")
        void convertsLongToHex() {
            // given
            var value = 4660L;

            // when
            var result = HexUtils.longToHex(value);

            // then
            assertThat(result).isEqualTo("0x1234");
        }

        @Test
        @DisplayName("converts zero to 0x0")
        void convertsZero() {
            // given / when
            var result = HexUtils.longToHex(0L);

            // then
            assertThat(result).isEqualTo("0x0");
        }

        @Test
        @DisplayName("round-trips correctly with hexToLong")
        void roundTrips() {
            // given
            var original = 1_102_065L;

            // when
            var hex = HexUtils.longToHex(original);
            var roundTripped = HexUtils.hexToLong(hex);

            // then
            assertThat(roundTripped).isEqualTo(original);
        }
    }

    @Nested
    @DisplayName("hexToInstant")
    class HexToInstant {

        @Test
        @DisplayName("converts hex timestamp to Instant")
        void convertsHexToInstant() {
            // given
            var hex = "0x65b3e8c0";

            // when
            var result = HexUtils.hexToInstant(hex);

            // then
            assertThat(result).isEqualTo(Instant.ofEpochSecond(0x65b3e8c0L));
        }

        @Test
        @DisplayName("converts zero timestamp to epoch")
        void convertsZeroToEpoch() {
            // given
            var hex = "0x0";

            // when
            var result = HexUtils.hexToInstant(hex);

            // then
            assertThat(result).isEqualTo(Instant.EPOCH);
        }
    }
}
