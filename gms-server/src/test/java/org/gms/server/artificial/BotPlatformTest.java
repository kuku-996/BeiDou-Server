package org.gms.server.artificial;

import org.gms.server.maps.Foothold;
import org.junit.jupiter.api.Test;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BotPlatformTest {
    @Test
    void projectsOntoFlatPlatform() {
        BotPlatform platform = platform(new Point(0, 100), new Point(200, 100));

        assertEquals(new Point(80, 100), platform.project(80));
    }

    @Test
    void interpolatesBothSlopeDirections() {
        BotPlatform ascending = platform(new Point(0, 100), new Point(200, 200));
        BotPlatform reversed = platform(new Point(200, 200), new Point(0, 100));

        assertEquals(new Point(100, 150), ascending.project(100));
        assertEquals(new Point(100, 150), reversed.project(100));
    }

    @Test
    void clampsProjectionInsideSafeEdges() {
        BotPlatform platform = platform(new Point(0, 100), new Point(200, 200));

        assertEquals(new Point(12, 106), platform.project(-50));
        assertEquals(new Point(188, 194), platform.project(250));
    }

    @Test
    void rejectsWallsAndPlatformsThatAreTooNarrow() {
        assertNull(BotPlatform.fromFoothold(new Foothold(new Point(10, 0), new Point(10, 100), 1)));
        assertNull(BotPlatform.fromFoothold(new Foothold(new Point(0, 100), new Point(20, 100), 2)));
    }

    private BotPlatform platform(Point start, Point end) {
        return BotPlatform.fromFoothold(new Foothold(start, end, 99));
    }
}
