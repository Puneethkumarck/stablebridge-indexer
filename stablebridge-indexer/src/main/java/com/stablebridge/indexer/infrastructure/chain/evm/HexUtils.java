package com.stablebridge.indexer.infrastructure.chain.evm;

import lombok.experimental.UtilityClass;

import java.time.Instant;

@UtilityClass
class HexUtils {

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
