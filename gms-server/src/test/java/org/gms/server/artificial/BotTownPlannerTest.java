package org.gms.server.artificial;

import org.junit.jupiter.api.Test;

import java.awt.Point;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BotTownPlannerTest {
    @Test
    void keepsSelectedPointsApartWhenAlternativesExist() {
        List<Point> selected = BotTownPlanner.selectSpaced(
                List.of(new Point(0, 100), new Point(20, 100), new Point(200, 100)),
                List.of(1.0, 1.0, 1.0),
                2,
                new Random(7),
                60);

        assertEquals(2, selected.size());
        assertTrue(Math.abs(selected.get(0).x - selected.get(1).x) >= 60);
    }

    @Test
    void fallsBackToUniformSelectionWhenAllWeightsAreZero() {
        List<Point> selected = BotTownPlanner.selectSpaced(
                List.of(new Point(0, 100), new Point(100, 100)),
                List.of(0.0, 0.0),
                2,
                new Random(3),
                40);

        assertEquals(2, selected.size());
    }

    @Test
    void rejectsMismatchedPointAndWeightLists() {
        List<Point> selected = BotTownPlanner.selectSpaced(
                List.of(new Point(0, 100)),
                List.of(),
                1,
                new Random(1),
                40);

        assertTrue(selected.isEmpty());
    }
}
