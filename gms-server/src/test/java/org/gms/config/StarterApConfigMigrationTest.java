package org.gms.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class StarterApConfigMigrationTest {
    @Test
    void starterApAutoAssignIsDisabledByMigration() throws Exception {
        Path migration = Path.of("src/main/resources/db/migration/V1.11.4__disable_auto_assign_starters_ap.sql");

        assertTrue(Files.exists(migration), "missing migration that disables starter AP auto assignment");

        String sql = Files.readString(migration).toLowerCase().replace("`", "");
        assertTrue(sql.contains("use_auto_assign_starters_ap"), "migration must target starter AP auto assignment");
        assertTrue(sql.contains("config_value = 'false'") || sql.contains("config_value='false'"),
                "migration must set starter AP auto assignment to false");
    }
}
