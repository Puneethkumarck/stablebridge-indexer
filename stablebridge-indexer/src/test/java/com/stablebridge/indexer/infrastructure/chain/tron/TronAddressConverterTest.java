package com.stablebridge.indexer.infrastructure.chain.tron;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TronAddressConverter")
class TronAddressConverterTest {

    private static final String USDT_BASE58 = "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t";
    private static final String USDT_HEX = "41a614f803b6fd780986a42c78ec9c7f77e6ded13c";

    @Nested
    @DisplayName("base58ToHex")
    class Base58ToHex {

        @Test
        @DisplayName("converts known TRON address to lowercase hex with 41 prefix")
        void convertsKnownTronAddress() {
            // given
            var base58 = USDT_BASE58;

            // when
            var result = TronAddressConverter.base58ToHex(base58);

            // then
            assertThat(result).isEqualTo(USDT_HEX);
        }

        @Test
        @DisplayName("throws when input is null")
        void throwsWhenNull() {
            // given
            String base58 = null;

            // when / then
            assertThatThrownBy(() -> TronAddressConverter.base58ToHex(base58))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("throws when input contains invalid Base58 character")
        void throwsWhenInvalidCharacter() {
            // given — '0' is not in the Base58 alphabet
            var invalid = "TR0NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t";

            // when / then
            assertThatThrownBy(() -> TronAddressConverter.base58ToHex(invalid))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("throws when checksum does not match")
        void throwsWhenChecksumMismatch() {
            // given — mutate the last character of a valid address to break the checksum
            var tampered = USDT_BASE58.substring(0, USDT_BASE58.length() - 1) + "u";

            // when / then
            assertThatThrownBy(() -> TronAddressConverter.base58ToHex(tampered))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("hexToBase58")
    class HexToBase58 {

        @Test
        @DisplayName("converts known hex to Base58Check")
        void convertsKnownHex() {
            // given
            var hex = USDT_HEX;

            // when
            var result = TronAddressConverter.hexToBase58(hex);

            // then
            assertThat(result).isEqualTo(USDT_BASE58);
        }

        @Test
        @DisplayName("accepts uppercase hex")
        void acceptsUppercaseHex() {
            // given
            var hexUpper = USDT_HEX.toUpperCase();

            // when
            var result = TronAddressConverter.hexToBase58(hexUpper);

            // then
            assertThat(result).isEqualTo(USDT_BASE58);
        }

        @Test
        @DisplayName("throws when input is null")
        void throwsWhenNull() {
            // given
            String hex = null;

            // when / then
            assertThatThrownBy(() -> TronAddressConverter.hexToBase58(hex))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("throws when hex length is wrong")
        void throwsWhenWrongLength() {
            // given — too short
            var shortHex = "41a614f803b6fd780986a42c78ec9c7f77e6ded1";

            // when / then
            assertThatThrownBy(() -> TronAddressConverter.hexToBase58(shortHex))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("roundtrip")
    class Roundtrip {

        @Test
        @DisplayName("base58 -> hex -> base58 returns original")
        void base58Roundtrip() {
            // given
            var base58 = USDT_BASE58;

            // when
            var hex = TronAddressConverter.base58ToHex(base58);
            var result = TronAddressConverter.hexToBase58(hex);

            // then
            assertThat(result).isEqualTo(base58);
        }

        @Test
        @DisplayName("hex -> base58 -> hex returns original")
        void hexRoundtrip() {
            // given
            var hex = USDT_HEX;

            // when
            var base58 = TronAddressConverter.hexToBase58(hex);
            var result = TronAddressConverter.base58ToHex(base58);

            // then
            assertThat(result).isEqualTo(hex);
        }
    }

    @Nested
    @DisplayName("topicToHex")
    class TopicToHex {

        @Test
        @DisplayName("extracts last 40 hex chars and prepends 41")
        void extractsAddressFromTopic() {
            // given
            var topic = "0x000000000000000000000041a614f803b6fd780986a42c78ec9c7f77e6ded13c";

            // when
            var result = TronAddressConverter.topicToHex(topic);

            // then
            assertThat(result).isEqualTo(USDT_HEX);
        }

        @Test
        @DisplayName("handles topic without 0x prefix")
        void handlesTopicWithoutPrefix() {
            // given
            var topic = "000000000000000000000041a614f803b6fd780986a42c78ec9c7f77e6ded13c";

            // when
            var result = TronAddressConverter.topicToHex(topic);

            // then
            assertThat(result).isEqualTo(USDT_HEX);
        }

        @Test
        @DisplayName("returns lowercase hex")
        void returnsLowercase() {
            // given
            var topic = "0x000000000000000000000041A614F803B6FD780986A42C78EC9C7F77E6DED13C";

            // when
            var result = TronAddressConverter.topicToHex(topic);

            // then
            assertThat(result).isEqualTo(USDT_HEX);
        }
    }

    @Nested
    @DisplayName("logAddressToHex")
    class LogAddressToHex {

        @Test
        @DisplayName("prepends 41 to address without prefix")
        void prependsPrefix() {
            // given
            var logAddress = "a614f803b6fd780986a42c78ec9c7f77e6ded13c";

            // when
            var result = TronAddressConverter.logAddressToHex(logAddress);

            // then
            assertThat(result).isEqualTo(USDT_HEX);
        }

        @Test
        @DisplayName("handles 0x prefix and strips it before prepending 41")
        void stripsHexPrefix() {
            // given
            var logAddress = "0xa614f803b6fd780986a42c78ec9c7f77e6ded13c";

            // when
            var result = TronAddressConverter.logAddressToHex(logAddress);

            // then
            assertThat(result).isEqualTo(USDT_HEX);
        }

        @Test
        @DisplayName("returns lowercase hex")
        void returnsLowercase() {
            // given
            var logAddress = "A614F803B6FD780986A42C78EC9C7F77E6DED13C";

            // when
            var result = TronAddressConverter.logAddressToHex(logAddress);

            // then
            assertThat(result).isEqualTo(USDT_HEX);
        }
    }
}
