package com.eu.habbo.messages.incoming;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Every packet that does a staff job must refuse a caller who lacks the rank for it.
 *
 * <p>A packet header is just a number on the wire: anyone with a modified client can send a
 * housekeeping or catalog-admin packet, whatever their rank and whatever the client's UI offers
 * them. The rank check therefore has to live in the handler, not in the window that normally opens
 * it - a handler that reads its payload and acts before asking is a hole, not a missing feature.
 *
 * <p>This is a source contract rather than a live exercise: it fails when a new handler is added to
 * one of these packages without a guard, which is exactly when the hole would be introduced.
 */
class StaffPacketAuthorizationContractTest {

    private static final Path INCOMING = Path.of("src/main/java/com/eu/habbo/messages/incoming");

    /** Classes in these packages that are helpers, not packet handlers. */
    private static final List<String> NOT_HANDLERS = List.of(
            "Guard.java", "Policy.java", "Duration.java", "Request.java", "Payload.java", "Data.java",
            "Checks.java", "Responder.java", "Parser.java", "Runtime.java", "Envelope.java", "Event.java$SUPERCLASS");

    @Test
    void everyHousekeepingPacketChecksTheHousekeepingPermission() throws IOException {
        List<String> unguarded = unguarded(INCOMING.resolve("housekeeping"), "ACC_HOUSEKEEPING");

        assertTrue(unguarded.isEmpty(), () -> "Housekeeping packets without a permission check: " + unguarded);
    }

    @Test
    void everyCatalogAdminPacketChecksTheCatalogPermission() throws IOException {
        // The studio handlers share CatalogStudioEvent.authorize(); the rest check inline.
        List<String> unguarded = new ArrayList<>();

        for (Path handler : handlers(INCOMING.resolve("catalog/catalogadmin"))) {
            String source = Files.readString(handler);
            boolean guarded = source.contains("ACC_CATALOGFURNI")
                    || source.contains("if (!authorize()) return")
                    || source.contains("if (!this.authorize()) return");

            if (!guarded) unguarded.add(handler.getFileName().toString());
        }

        assertTrue(unguarded.isEmpty(), () -> "Catalog admin packets without a permission check: " + unguarded);
    }

    /**
     * The shared guard the studio handlers lean on. If it ever stops naming a permission, every one
     * of them silently opens up at once, and the per-handler check above would still pass.
     */
    @Test
    void theSharedCatalogStudioGuardNamesAPermission() throws IOException {
        String guard = Files.readString(
                INCOMING.resolve("catalog/catalogadmin/studio/CatalogStudioEvent.java"));

        assertTrue(guard.contains("hasPermission(Permission.ACC_CATALOGFURNI)"));
        assertTrue(guard.contains("return false"), "a caller without the permission must be refused");
        assertFalse(guard.contains("return true;\n    }\n\n    final int actorId"), "the guard must not pass everyone");
    }

    private static List<String> unguarded(Path directory, String permission) throws IOException {
        List<String> unguarded = new ArrayList<>();

        for (Path handler : handlers(directory)) {
            if (!Files.readString(handler).contains(permission)) {
                unguarded.add(handler.getFileName().toString());
            }
        }

        return unguarded;
    }

    private static List<Path> handlers(Path directory) throws IOException {
        try (Stream<Path> paths = Files.walk(directory)) {
            return paths.filter(path -> path.toString().endsWith(".java"))
                    .filter(StaffPacketAuthorizationContractTest::looksLikeAHandler)
                    .toList();
        }
    }

    private static boolean looksLikeAHandler(Path path) {
        String name = path.getFileName().toString();

        if (!name.endsWith("Event.java")) return false;

        return NOT_HANDLERS.stream().noneMatch(name::endsWith);
    }
}
