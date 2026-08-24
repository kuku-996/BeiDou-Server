package org.gms.server.artificial;

import org.gms.server.maps.Foothold;
import org.junit.jupiter.api.Test;

import java.awt.Point;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BotCrossPlatformPlannerTest {
    @Test
    void choosesTheNearestReachablePlatform() {
        BotPlatform near = platform(new Point(100, 100), new Point(220, 100), 1);
        BotPlatform far = platform(new Point(240, 60), new Point(360, 60), 2);

        BotCrossPlatformPlanner.Target target = BotCrossPlatformPlanner.chooseBest(List.of(
                new BotCrossPlatformPlanner.Candidate(far, new Point(260, 60), 160, -40),
                new BotCrossPlatformPlanner.Candidate(near, new Point(140, 100), 40, 0)));

        assertEquals(1, target.platform().getFootholdId());
        assertEquals(new Point(140, 100), target.landing());
    }

    @Test
    void retainsTheLandingPointForAPlatformFarBelow() {
        BotPlatform lower = platform(new Point(100, 180), new Point(220, 180), 3);

        BotCrossPlatformPlanner.Target target = BotCrossPlatformPlanner.chooseBest(List.of(
                new BotCrossPlatformPlanner.Candidate(lower, new Point(140, 180), 40, 80)));

        assertEquals(new Point(140, 180), target.landing());
    }

    @Test
    void returnsNoTargetWhenCandidatesAreEmpty() {
        assertNull(BotCrossPlatformPlanner.chooseBest(List.of()));
    }

    private BotPlatform platform(Point start, Point end, int id) {
        return BotPlatform.fromFoothold(new Foothold(start, end, id));
    }
}
