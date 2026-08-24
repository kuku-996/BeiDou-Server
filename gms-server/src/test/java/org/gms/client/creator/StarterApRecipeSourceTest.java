package org.gms.client.creator;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StarterApRecipeSourceTest {
    @Test
    void beginnerCreationDoesNotGrantRemainingApWhenManualStarterApIsEnabled() throws Exception {
        Path source = Path.of("src/main/java/org/gms/client/creator/CharacterFactoryRecipe.java");
        String java = Files.readString(source);

        assertTrue(java.contains("private int str = 4, dex = 4, int_ = 4, luk = 4;"),
                "starter recipe should preserve the requested 4/4/4/4 baseline");
        assertFalse(java.contains("str = 12;"), "starter recipe must not force old STR starter baseline");
        assertFalse(java.contains("dex = 5;"), "starter recipe must not force old DEX starter baseline");
        assertFalse(java.contains("ap = 9;"), "starter recipe must not grant extra level-1 remaining AP");
    }
}
