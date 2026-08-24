package org.gms.client.creator.novice;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdventurerDiceStatsTest {
    @Test
    void acceptsLegacyRangeWithFixedTotal() {
        assertTrue(new AdventurerDiceStats(4, 6, 4, 11).isValid());
        assertTrue(new AdventurerDiceStats(13, 4, 4, 4).isValid());
    }

    @Test
    void rejectsOutOfRangeOrWrongTotal() {
        assertFalse(new AdventurerDiceStats(3, 7, 7, 8).isValid());
        assertFalse(new AdventurerDiceStats(14, 4, 4, 4).isValid());
        assertFalse(new AdventurerDiceStats(4, 4, 4, 12).isValid());
    }

}
