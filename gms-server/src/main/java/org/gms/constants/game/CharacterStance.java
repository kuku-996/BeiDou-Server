package org.gms.constants.game;

/** v83 movement-packet stance ids shared by the synthetic-player movement adapter. */
public final class CharacterStance {
    public static final int WALK_RIGHT_STANCE = 2;
    public static final int WALK_LEFT_STANCE = 3;
    public static final int STAND_RIGHT_STANCE = 4;
    public static final int STAND_LEFT_STANCE = 5;
    public static final int JUMP_RIGHT_STANCE = 6;
    public static final int JUMP_LEFT_STANCE = 7;
    // 8/9 are normal attack poses, not prone poses.  Sending them from a
    // movement packet makes an observer leave locomotion state.
    public static final int PRONE_RIGHT_STANCE = 10;
    public static final int PRONE_LEFT_STANCE = 11;
    public static final int ROPE_RIGHT_STANCE = 12;
    public static final int ROPE_LEFT_STANCE = 13;
    public static final int LADDER_RIGHT_STANCE = 14;
    public static final int LADDER_LEFT_STANCE = 15;

    // The artificial-player system never intentionally sends a real death
    // movement fragment.  Keep these only for defensive internal checks; the
    // movement adapter must not emit them.
    public static final int DEAD_RIGHT_STANCE = 18;
    public static final int DEAD_LEFT_STANCE = 19;

    private CharacterStance() {
    }

    public static boolean isClimbing(int stance) {
        return stance == ROPE_RIGHT_STANCE || stance == ROPE_LEFT_STANCE
                || stance == LADDER_RIGHT_STANCE || stance == LADDER_LEFT_STANCE;
    }

    public static int ropeStance(int facingDir) {
        return facingDir < 0 ? ROPE_LEFT_STANCE : ROPE_RIGHT_STANCE;
    }

    public static int ladderStance(int facingDir) {
        return facingDir < 0 ? LADDER_LEFT_STANCE : LADDER_RIGHT_STANCE;
    }

    public static boolean isStanding(int stance) {
        return stance == STAND_RIGHT_STANCE || stance == STAND_LEFT_STANCE;
    }
}
