package org.gms.client.creator.novice;

import org.gms.client.creator.CharacterFactoryRecipe;

/**
 * Four base attributes selected by the native adventurer creation dice UI.
 *
 * <p>The server treats these values as untrusted packet input. A valid roll
 * has four values in the legacy client range 4..13 and a fixed total of 25.</p>
 */
public record AdventurerDiceStats(int str, int dex, int intelligence, int luk) {
    public static final int MIN_STAT = 4;
    public static final int MAX_STAT = 13;
    public static final int REQUIRED_TOTAL = 25;

    public boolean isValid() {
        return inRange(str)
                && inRange(dex)
                && inRange(intelligence)
                && inRange(luk)
                && str + dex + intelligence + luk == REQUIRED_TOTAL;
    }

    public void applyTo(CharacterFactoryRecipe recipe) {
        if (!isValid()) {
            throw new IllegalStateException("Cannot apply invalid adventurer dice stats");
        }
        recipe.setStr(str);
        recipe.setDex(dex);
        recipe.setInt(intelligence);
        recipe.setLuk(luk);
    }

    private static boolean inRange(int value) {
        return value >= MIN_STAT && value <= MAX_STAT;
    }
}
