package org.gms.server.artificial;

import org.gms.server.maps.FootholdTree;
import org.gms.server.maps.MapObject;
import org.gms.server.maps.MapObjectType;
import org.gms.server.maps.MapleMap;
import org.gms.server.maps.Portal;

import java.awt.Point;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * BeiDou adapter for SoloMapling's anchor-weighted town presence sampler.
 *
 * <p>NPCs and portals attract bots to useful parts of a town while foothold
 * projection keeps every generated point on valid ground.</p>
 */
final class BotTownPlanner {
    private static final int PORTAL_SCAN_LIMIT = 64;
    private static final int MAP_SAMPLE_STEP = 120;
    private static final int MIN_POINT_SPACING = 60;
    private static final int[] ANCHOR_OFFSETS = {
            0, -60, 60, -120, 120, -200, 200, -300, 300
    };

    private BotTownPlanner() {
    }

    static List<Point> sample(MapleMap map, int count, Random random) {
        if (map == null || count <= 0) {
            return List.of();
        }

        Map<Long, Candidate> candidates = new HashMap<>();
        for (Anchor anchor : collectAnchors(map)) {
            addAnchorCandidates(map, anchor, candidates);
        }
        addMapWideCandidates(map, candidates);

        List<Point> points = new ArrayList<>(candidates.size());
        List<Double> weights = new ArrayList<>(candidates.size());
        for (Candidate candidate : candidates.values()) {
            points.add(candidate.point());
            weights.add(candidate.weight());
        }
        return selectSpaced(points, weights, count, random, MIN_POINT_SPACING);
    }

    private static List<Anchor> collectAnchors(MapleMap map) {
        List<Anchor> anchors = new ArrayList<>();
        for (MapObject object : map.getMapObjectsInRange(
                new Point(0, 0), Double.POSITIVE_INFINITY, List.of(MapObjectType.NPC))) {
            if (object.getPosition() != null) {
                anchors.add(new Anchor(object.getPosition(), 1.0));
            }
        }

        for (int portalId = 0; portalId < PORTAL_SCAN_LIMIT; portalId++) {
            Portal portal = map.getPortal(portalId);
            if (portal != null && portal.getPosition() != null) {
                anchors.add(new Anchor(portal.getPosition(), 0.55));
            }
        }
        return anchors;
    }

    private static void addAnchorCandidates(
            MapleMap map,
            Anchor anchor,
            Map<Long, Candidate> candidates) {
        for (int offset : ANCHOR_OFFSETS) {
            int x = anchor.point().x + offset;
            BotPlatform platform = BotPlatform.findBelow(map, new Point(x, anchor.point().y - 48));
            if (platform == null) {
                continue;
            }

            Point point = platform.project(x);
            double distanceFactor = Math.exp(-(double) offset * offset / (2.0 * 220.0 * 220.0));
            addCandidate(candidates, platform, point, 0.15 + anchor.strength() * distanceFactor);
        }
    }

    private static void addMapWideCandidates(MapleMap map, Map<Long, Candidate> candidates) {
        FootholdTree tree = map.getFootholds();
        if (tree == null) {
            return;
        }

        int minX = tree.getMinDropX();
        int maxX = tree.getMaxDropX();
        if (maxX <= minX || maxX - minX > 20_000) {
            minX = tree.getX1();
            maxX = tree.getX2();
        }
        if (maxX <= minX || maxX - minX > 20_000) {
            return;
        }

        int probeY = tree.getY1() - 1;
        for (int x = minX; x <= maxX; x += MAP_SAMPLE_STEP) {
            BotPlatform platform = BotPlatform.findBelow(map, new Point(x, probeY));
            if (platform != null) {
                addCandidate(candidates, platform, platform.project(x), 0.12);
            }
        }
    }

    private static void addCandidate(
            Map<Long, Candidate> candidates,
            BotPlatform platform,
            Point point,
            double weight) {
        int bucket = Math.floorDiv(point.x, MIN_POINT_SPACING);
        long key = ((long) platform.getFootholdId() << 32) ^ (bucket & 0xffffffffL);
        candidates.merge(key, new Candidate(point, weight),
                (oldValue, newValue) -> new Candidate(oldValue.point(), oldValue.weight() + newValue.weight()));
    }

    static List<Point> selectSpaced(
            List<Point> points,
            List<Double> weights,
            int count,
            Random random,
            int minSpacing) {
        if (points == null || weights == null || random == null
                || points.size() != weights.size() || count <= 0) {
            return List.of();
        }

        List<WeightedPoint> pool = new ArrayList<>(points.size());
        for (int i = 0; i < points.size(); i++) {
            if (points.get(i) != null) {
                pool.add(new WeightedPoint(new Point(points.get(i)), Math.max(0.0, weights.get(i))));
            }
        }

        List<Point> selected = new ArrayList<>(Math.min(count, pool.size()));
        while (selected.size() < count && !pool.isEmpty()) {
            List<WeightedPoint> eligible = pool.stream()
                    .filter(candidate -> isSpaced(candidate.point(), selected, minSpacing))
                    .toList();
            if (eligible.isEmpty()) {
                eligible = pool;
            }

            WeightedPoint chosen = weightedPick(eligible, random);
            selected.add(new Point(chosen.point()));
            pool.remove(chosen);
        }
        return selected;
    }

    private static boolean isSpaced(Point candidate, List<Point> selected, int minSpacing) {
        for (Point point : selected) {
            if (Math.abs(candidate.y - point.y) < 48
                    && Math.abs(candidate.x - point.x) < minSpacing) {
                return false;
            }
        }
        return true;
    }

    private static WeightedPoint weightedPick(List<WeightedPoint> candidates, Random random) {
        double total = candidates.stream().mapToDouble(WeightedPoint::weight).sum();
        if (total <= 0.0) {
            return candidates.get(random.nextInt(candidates.size()));
        }

        double roll = random.nextDouble() * total;
        double cumulative = 0.0;
        for (WeightedPoint candidate : candidates) {
            cumulative += candidate.weight();
            if (roll < cumulative) {
                return candidate;
            }
        }
        return candidates.get(candidates.size() - 1);
    }

    private record Anchor(Point point, double strength) {
    }

    private record Candidate(Point point, double weight) {
    }

    private record WeightedPoint(Point point, double weight) {
    }
}
