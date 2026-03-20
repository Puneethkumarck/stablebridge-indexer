package com.stablebridge.indexer.infrastructure.chain.evm;

import java.time.Instant;

/**
 * Utility for converting between hex-encoded EVM values and Java types.
 */
final class HexUtils {

    private HexUtils() {
        // utility class
    }

    static long hexToLong(String hex) {
        if (hex == null || hex.isBlank()) {
            throw new IllegalArgumentException("Hex string must not be null or blank");
        }
        var stripped = hex.startsWith("0x") || hex.startsWith("0X")
                ? hex.substring(2)
                : hex;
        return Long.parseUnsignedLong(stripped, 16);
    }

    static String longToHex(long value) {
        return "0x" + Long.toHexString(value);
    }

    static Instant hexToInstant(String hex) {
        var epochSeconds = hexToLong(hex);
        return Instant.ofEpochSecond(epochSeconds);
    }
}
