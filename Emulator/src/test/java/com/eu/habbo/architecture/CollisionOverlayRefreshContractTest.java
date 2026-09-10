package com.eu.habbo.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * The collision overlay re-sends itself when the room changes, and hiding wired is such a change.
 *
 * <p>{@code tick} compares a signature built from the floor furni - id, tile, rotation, height,
 * footprint - and only pushes a new overlay when that string moves. Toggling ":hidewired" moves no
 * furni, so the signature was identical and watchers kept looking at the overlay they already had,
 * which still drew boxes that had just stopped counting. It caught up only when something in the room
 * happened to be nudged.
 */
class CollisionOverlayRefreshContractTest {

    private static final Path COMMAND =
            Path.of("src/main/java/com/eu/habbo/habbohotel/commands/DebugViewCollisionsCommand.java");

    @Test
    void theSignatureNoticesWiredBeingHidden() throws Exception {
        String source = Files.readString(COMMAND);
        int signatureAt = source.indexOf("private static String signature(Room room)");

        assertTrue(signatureAt >= 0, "signature(Room) is gone; this contract needs rewriting");

        int end = source.indexOf("\n    }", signatureAt);

        assertTrue(end > signatureAt, "could not find the end of signature(Room)");

        assertTrue(
                source.substring(signatureAt, end).contains("isWiredHidden()"),
                "the overlay signature must include whether wired is hidden, or ':hidewired' changes what "
                        + "the overlay should draw without anything asking it to redraw");
    }
}
