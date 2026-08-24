package org.gms.server.artificial;

import org.gms.server.maps.MapleMap;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

/**
 * Finds a nearby foothold that a v83 beginner can reach with one ordinary jump
 * or a controlled drop. Rope and ladder data is intentionally not assumed here:
 * this server version does not expose those WZ objects yet.
 */
final class BotCrossPlatformPlanner {
    private static final int MAX_HORIZONTAL_DISTANCE = 144;
    private static final int MAX_JUMP_UP = 96;
    private static final int MAX_DROP_DOWN = 120;
    private static final int Y_SCAN_MIN = -144;
    private static final int Y_SCAN_MAX = 144;
    private static final int SCAN_STEP = 16;

    private BotCrossPlatformPlanner() {
    }

    static Target findTarget(MapleMap map, BotPlatform current, Point from, int direction) {
        if (map == null || current == null || from == null || direction == 0) {
            return null;
        }

        List<Candidate> candidates = new ArrayList<>();
        for (int distance = 8; distance <= MAX_HORIZONTAL_DISTANCE; distance += 8) {
            int probeX = from.x + direction * distance;
            for (int yOffset = Y_SCAN_MIN; yOffset <= Y_SCAN_MAX; yOffset += SCAN_STEP) {
                BotPlatform candidate = BotPlatform.findBelow(map, new Point(probeX, from.y + yOffset));
                if (candidate == null || current.isSamePlatform(candidate)) {
                    continue;
                }

                Point landing = candidate.project(probeX);
                int horizontal = Math.abs(landing.x - from.x);
                int vertical = landing.y - from.y;
                if (horizontal > MAX_HORIZONTAL_DISTANCE
                        || vertical < -MAX_JUMP_UP
                        || vertical > MAX_DROP_DOWN
                        || Integer.signum(landing.x - from.x) != Integer.signum(direction)) {
                    continue;
                }

                Candidate found = new Candidate(candidate, landing, horizontal, vertical);
                if (candidates.stream().noneMatch(existing ->
                        existing.platform.getFootholdId() == candidate.getFootholdId())) {
                    candidates.add(found);
                }
            }
        }
        return chooseBest(candidates);
    }

    static Target chooseBest(List<Candidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        Candidate best = candidates.stream()
                .min((left, right) -> Double.compare(score(left), score(right)))
                .orElse(null);
        if (best == null) {
            return null;
        }
        return new Target(best.platform, best.landing);
    }

    private static double score(Candidate candidate) {
        // Prefer short horizontal transfers, while keeping a small preference
        // for platforms that do not require a large vertical change.
        return candidate.horizontal + Math.abs(candidate.vertical) * 0.55;
    }

    record Candidate(BotPlatform platform, Point landing, int horizontal, int vertical) {
    }

    record Target(BotPlatform platform, Point landing) {
    }
}
