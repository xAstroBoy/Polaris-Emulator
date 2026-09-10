package com.eu.habbo.habbohotel.catalog;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CatalogPageLayoutsSchemaTest {

    /**
     * Every layout the code can produce must exist in the database enum. A layout added after the
     * alignment migration gets its own migration, so all of them are searched rather than that one.
     */
    @Test
    void migrationContainsEverySupportedCatalogPageLayout() throws Exception {
        StringBuilder migrations = new StringBuilder();

        try (var files = Files.list(Path.of("src/main/resources/db/migration"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".sql")).toList()) {
                migrations.append(Files.readString(file)).append('\n');
            }
        }

        String schema = migrations.toString();

        for (CatalogPageLayouts layout : CatalogPageLayouts.values()) {
            assertTrue(
                    schema.contains("'" + layout.name() + "'"),
                    () -> "Missing database enum value for " + layout.name());
        }
    }
}
