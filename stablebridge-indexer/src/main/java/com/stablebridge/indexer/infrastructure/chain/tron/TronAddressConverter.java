package com.stablebridge.indexer.infrastructure.chain.tron;

import lombok.experimental.UtilityClass;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

@UtilityClass
class TronAddressConverter {

    private static final String BASE58_ALPHABET =
            "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
    private static final BigInteger BASE = BigInteger.valueOf(58);
    private static final int ADDRESS_BYTES_WITH_PREFIX = 21;
    private static final int CHECKSUM_LENGTH = 4;
    private static final int FULL_DECODED_LENGTH = ADDRESS_BYTES_WITH_PREFIX + CHECKSUM_LENGTH;
    private static final byte TRON_PREFIX = (byte) 0x41;
    private static final String TRON_PREFIX_HEX = "41";
    private static final int TOPIC_ADDRESS_HEX_LENGTH = 40;

    static String base58ToHex(String base58Address) {
        if (base58Address == null) {
            throw new IllegalArgumentException("base58 address must not be null");
        }

        var decoded = decodeBase58(base58Address);
        if (decoded.length != FULL_DECODED_LENGTH) {
            throw new IllegalArgumentException(
                    "decoded TRON address must be " + FULL_DECODED_LENGTH
                            + " bytes, got " + decoded.length);
        }

        var payload = Arrays.copyOfRange(decoded, 0, ADDRESS_BYTES_WITH_PREFIX);
        var providedChecksum = Arrays.copyOfRange(decoded, ADDRESS_BYTES_WITH_PREFIX, FULL_DECODED_LENGTH);
        var expectedChecksum = computeChecksum(payload);

        if (!Arrays.equals(providedChecksum, expectedChecksum)) {
            throw new IllegalArgumentException("TRON address checksum mismatch for " + base58Address);
        }

        return bytesToHex(payload);
    }

    static String hexToBase58(String hexAddress) {
        if (hexAddress == null) {
            throw new IllegalArgumentException("hex address must not be null");
        }

        var normalized = stripHexPrefix(hexAddress).toLowerCase();
        if (normalized.length() != ADDRESS_BYTES_WITH_PREFIX * 2) {
            throw new IllegalArgumentException(
                    "hex TRON address must be " + (ADDRESS_BYTES_WITH_PREFIX * 2)
                            + " chars, got " + normalized.length());
        }

        var payload = hexToBytes(normalized);
        if (payload[0] != TRON_PREFIX) {
            throw new IllegalArgumentException("TRON address must start with 0x41 prefix");
        }
        var checksum = computeChecksum(payload);
        var full = new byte[FULL_DECODED_LENGTH];
        System.arraycopy(payload, 0, full, 0, ADDRESS_BYTES_WITH_PREFIX);
        System.arraycopy(checksum, 0, full, ADDRESS_BYTES_WITH_PREFIX, CHECKSUM_LENGTH);

        return encodeBase58(full);
    }

    static String topicToHex(String topic) {
        if (topic == null) {
            throw new IllegalArgumentException("topic must not be null");
        }

        var normalized = stripHexPrefix(topic).toLowerCase();
        if (normalized.length() < TOPIC_ADDRESS_HEX_LENGTH) {
            throw new IllegalArgumentException(
                    "topic must be at least " + TOPIC_ADDRESS_HEX_LENGTH + " hex chars");
        }

        var suffix = normalized.substring(normalized.length() - TOPIC_ADDRESS_HEX_LENGTH);
        return TRON_PREFIX_HEX + suffix;
    }

    static String logAddressToHex(String logAddress) {
        if (logAddress == null) {
            throw new IllegalArgumentException("log address must not be null");
        }

        var normalized = stripHexPrefix(logAddress).toLowerCase();
        if (normalized.length() != TOPIC_ADDRESS_HEX_LENGTH) {
            throw new IllegalArgumentException(
                    "log address must be " + TOPIC_ADDRESS_HEX_LENGTH
                            + " hex chars, got " + normalized.length());
        }

        return TRON_PREFIX_HEX + normalized;
    }

    private static byte[] decodeBase58(String input) {
        if (input.isEmpty()) {
            throw new IllegalArgumentException("base58 input must not be empty");
        }

        var value = BigInteger.ZERO;
        for (var i = 0; i < input.length(); i++) {
            var c = input.charAt(i);
            var digit = BASE58_ALPHABET.indexOf(c);
            if (digit < 0) {
                throw new IllegalArgumentException(
                        "invalid Base58 character '" + c + "' at position " + i);
            }
            value = value.multiply(BASE).add(BigInteger.valueOf(digit));
        }

        var payload = value.toByteArray();
        if (payload.length > 0 && payload[0] == 0) {
            payload = Arrays.copyOfRange(payload, 1, payload.length);
        }

        var leadingZeros = 0;
        while (leadingZeros < input.length() && input.charAt(leadingZeros) == BASE58_ALPHABET.charAt(0)) {
            leadingZeros++;
        }

        var decoded = new byte[leadingZeros + payload.length];
        System.arraycopy(payload, 0, decoded, leadingZeros, payload.length);
        return decoded;
    }

    private static String encodeBase58(byte[] input) {
        var leadingZeros = 0;
        while (leadingZeros < input.length && input[leadingZeros] == 0) {
            leadingZeros++;
        }

        var value = new BigInteger(1, input);
        var sb = new StringBuilder();
        while (value.signum() > 0) {
            var divmod = value.divideAndRemainder(BASE);
            value = divmod[0];
            sb.append(BASE58_ALPHABET.charAt(divmod[1].intValue()));
        }
        for (var i = 0; i < leadingZeros; i++) {
            sb.append(BASE58_ALPHABET.charAt(0));
        }
        return sb.reverse().toString();
    }

    private static byte[] computeChecksum(byte[] payload) {
        var hashed = sha256(sha256(payload));
        return Arrays.copyOfRange(hashed, 0, CHECKSUM_LENGTH);
    }

    private static byte[] sha256(byte[] input) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        var sb = new StringBuilder(bytes.length * 2);
        for (var b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static byte[] hexToBytes(String hex) {
        if ((hex.length() & 1) != 0) {
            throw new IllegalArgumentException("hex string must have even length");
        }
        var out = new byte[hex.length() / 2];
        for (var i = 0; i < out.length; i++) {
            var hi = Character.digit(hex.charAt(i * 2), 16);
            var lo = Character.digit(hex.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) {
                throw new IllegalArgumentException("invalid hex character at position " + (i * 2));
            }
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    private static String stripHexPrefix(String hex) {
        return hex.startsWith("0x") || hex.startsWith("0X") ? hex.substring(2) : hex;
    }
}
