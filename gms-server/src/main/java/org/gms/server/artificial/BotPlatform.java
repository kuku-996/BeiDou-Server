package org.gms.server.artificial;

import org.gms.server.maps.Foothold;
import org.gms.server.maps.MapleMap;

import java.awt.Point;

/**
 * Small BeiDou adapter for SoloMapling's platform abstraction.
 *
 * <p>The upstream project builds platforms from movement recordings. BeiDou
 * already has authoritative footholds in each loaded map, so those footholds
 * are used as the platform source here.</p>
 */
final class BotPlatform {
    private static final int EDGE_MARGIN = 12;

    private final Foothold foothold;
    private final int minX;
    private final int maxX;

    private BotPlatform(Foothold foothold) {
        this.foothold = foothold;
        this.minX = Math.min(foothold.getX1(), foothold.getX2()) + EDGE_MARGIN;
        this.maxX = Math.max(foothold.getX1(), foothold.getX2()) - EDGE_MARGIN;
    }

    static BotPlatform fromFoothold(Foothold foothold) {
        if (foothold == null || foothold.isWall()) {
            return null;
        }
        BotPlatform platform = new BotPlatform(foothold);
        return platform.isWalkable() ? platform : null;
    }

    static BotPlatform findBelow(MapleMap map, Point position) {
        if (map == null || position == null || map.getFootholds() == null) {
            return null;
        }
        Foothold foothold = map.getFootholds().findBelow(new Point(position.x, position.y - 1));
        return fromFoothold(foothold);
    }

    boolean isWalkable() {
        return maxX > minX;
    }

    int getLeftX() {
        return minX;
    }

    int getRightX() {
        return maxX;
    }

    int getFootholdId() {
        return foothold.getId();
    }

    int getYAt(int x) {
        return getYAtX(x);
    }

    boolean isSamePlatform(BotPlatform other) {
        return other != null && foothold.getId() == other.foothold.getId();
    }

    /** Projects an arbitrary X coordinate onto this platform. */
    Point project(int x) {
        int projectedX = Math.max(minX, Math.min(maxX, x));
        return new Point(projectedX, getYAtX(projectedX));
    }

    private int getYAtX(int x) {
        int x1 = foothold.getX1();
        int x2 = foothold.getX2();
        int y1 = foothold.getY1();
        int y2 = foothold.getY2();
        if (x1 == x2) {
            return y1;
        }
        double ratio = (double) (x - x1) / (x2 - x1);
        return (int) Math.round(y1 + ratio * (y2 - y1));
    }
}
