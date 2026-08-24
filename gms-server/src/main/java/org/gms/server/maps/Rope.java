package org.gms.server.maps;

import java.awt.Point;

/** Immutable v83 rope/ladder geometry used by the SoloMapling movement adapter. */
public final class Rope {
    private final int x;
    private final int topY;
    private final int bottomY;
    private final boolean ladder;

    public Rope(int x, int y1, int y2, boolean ladder) {
        this.x = x;
        this.topY = Math.min(y1, y2);
        this.bottomY = Math.max(y1, y2);
        this.ladder = ladder;
    }

    public int x() {
        return x;
    }

    public int topY() {
        return topY;
    }

    public int bottomY() {
        return bottomY;
    }

    public boolean isLadder() {
        return ladder;
    }

    public Point point() {
        return new Point(x, topY);
    }
}
