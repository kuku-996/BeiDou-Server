package org.gms.net.server.handlers.login;

import java.util.Optional;

public record LegacyStarterStats(int str, int dex, int intelligence, int luk) {
    public static final int ADVENTURER_JOB = 1;
    public static final int EXTENSION_BYTES = 4;
    public static final LegacyStarterStats DEFAULT = new LegacyStarterStats(4, 4, 4, 4);

    public static Optional<LegacyStarterStats> parseExtension(int job, boolean enabled, byte[] bytes) {
        if (!enabled || job != ADVENTURER_JOB || bytes == null || bytes.length != EXTENSION_BYTES) {
            return Optional.empty();
        }

        LegacyStarterStats stats = new LegacyStarterStats(
                Byte.toUnsignedInt(bytes[0]),
                Byte.toUnsignedInt(bytes[1]),
                Byte.toUnsignedInt(bytes[2]),
                Byte.toUnsignedInt(bytes[3]));
        return stats.isValid() ? Optional.of(stats) : Optional.empty();
    }

    public static Optional<LegacyStarterStats> resolveForCreate(int job, boolean enabled, byte[] trailingBytes) {
        if (trailingBytes == null || trailingBytes.length == 0) {
            return Optional.of(DEFAULT);
        }
        return parseExtension(job, enabled, trailingBytes);
    }

    public boolean isValid() {
        return inRange(str) && inRange(dex) && inRange(intelligence) && inRange(luk)
                && str + dex + intelligence + luk == 25;
    }

    private static boolean inRange(int value) {
        return value >= 4 && value <= 13;
    }
}
