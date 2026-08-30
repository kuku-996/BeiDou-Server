package org.gms.server.weather;

import java.util.concurrent.atomic.AtomicInteger;

/** Server-authoritative cosmetic clock and weather selection for Kaentake GMS083. */
public final class WeatherService {
    public static final long DAY_LENGTH_MS = 4L * 60L * 60L * 1000L;
    public static final long SKY_LENGTH_MS = 15L * 60L * 1000L;
    public static final int MINUTES_PER_DAY = 1440;
    public static final byte SKY_CLEAR = 0, SKY_RAIN = 1, SKY_SNOW = 2,
            SKY_OVERCAST = 3, SKY_STORM = 4;
    private static final AtomicInteger forcedMinute = new AtomicInteger(-1);
    private static final AtomicInteger forcedSky = new AtomicInteger(-1);

    private WeatherService() { }

    public static int minuteOfDay() {
        int forced = forcedMinute.get();
        if (forced >= 0) return forced;
        long position = Math.floorMod(System.currentTimeMillis(), DAY_LENGTH_MS);
        return (int) (position * MINUTES_PER_DAY / DAY_LENGTH_MS);
    }

    public static int msPerGameMinute() { return (int) (DAY_LENGTH_MS / MINUTES_PER_DAY); }

    public static byte skyForMap(int mapId) {
        int forced = forcedSky.get();
        if (forced >= 0) return (byte) forced;
        int region = mapId / 1_000_000;
        if (region == 211 || region == 140) return SKY_SNOW;
        long period = System.currentTimeMillis() / SKY_LENGTH_MS;
        int roll = Math.floorMod((int) (period ^ (region * 1103515245L)), 100);
        // Profiles 5..8 use custom Item/Cash/0512.img nodes. They remain disabled
        // until those nodes are shipped, so an incomplete client can never request a
        // missing particle resource. All visual sky, rain, snow, fog and lamp art is
        // still fully active through profiles 0..4.
        if (region == 260 || region == 261) return roll < 38 ? SKY_OVERCAST : SKY_CLEAR;
        if (region == 101) return roll < 32 ? SKY_RAIN : (roll < 48 ? SKY_STORM : SKY_CLEAR);
        if (region == 102) return roll < 30 ? SKY_OVERCAST : SKY_CLEAR;
        if (roll < 12) return SKY_RAIN;
        if (roll < 18) return SKY_OVERCAST;
        if (roll < 22) return SKY_STORM;
        return SKY_CLEAR;
    }

    public static int skyElapsedMillis() { return (int) Math.floorMod(System.currentTimeMillis(), SKY_LENGTH_MS); }

    public static int tintForMap(int mapId) {
        int region = mapId / 1_000_000;
        if (region == 211) return 0x41508E;
        if (region == 140) return 0x485294;
        if (region == 101) return 0x3D666C;
        if (region == 102 || region == 260 || region == 261) return 0x635582;
        return 0x585878;
    }

    /** Client weather_palettes.inc: El Nath is 0; 26 is the neutral/default palette. */
    public static byte paletteForMap(int mapId) { return (byte) (mapId / 1_000_000 == 211 ? 0 : 26); }

    public static void forceTime(int minute) { forcedMinute.set(Math.floorMod(minute, MINUTES_PER_DAY)); }
    public static void forceSky(byte sky) { forcedSky.set(sky); }
    public static void clearOverrides() { forcedMinute.set(-1); forcedSky.set(-1); }

    public static String currentTestState() {
        return String.format("time=%02d:%02d, sky=%d%s", minuteOfDay() / 60,
                minuteOfDay() % 60, skyForMap(100000000),
                forcedSky.get() >= 0 || forcedMinute.get() >= 0 ? " (forced)" : "");
    }
}
