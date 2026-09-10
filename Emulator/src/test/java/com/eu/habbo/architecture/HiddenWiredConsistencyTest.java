package com.eu.habbo.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * What ":hidewired" stops drawing and what it stops colliding with must be the same set of furni.
 *
 * <p>They drifted once, and the result was a stack you could see through and still walk into.
 * {@code RoomManager} hides on room entry with a plain {@code instanceof InteractionWired} over the
 * floor items, while {@code RoomWiredVisibilityService} built its list from the four
 * {@code RoomSpecialTypes} collections. Any wired box missing from those collections was hidden by the
 * first and never named by the second, so its tiles were never recalculated: invisible, still solid,
 * and nothing anywhere reported a problem.
 *
 * <p>The rule is therefore about the predicate, not the outcome: both sides pick wired the same way,
 * off the room's own floor items.
 */
class HiddenWiredConsistencyTest {

    private static final Path VISIBILITY_SERVICE =
            Path.of("src/main/java/com/eu/habbo/habbohotel/rooms/RoomWiredVisibilityService.java");
    private static final Path ROOM_MANAGER =
            Path.of("src/main/java/com/eu/habbo/habbohotel/rooms/RoomManager.java");

    @Test
    void bothSidesPickWiredTheSameWay() throws Exception {
        String service = Files.readString(VISIBILITY_SERVICE);
        String manager = Files.readString(ROOM_MANAGER);

        assertTrue(
                manager.contains("instanceof InteractionWired"),
                "RoomManager no longer hides wired by instanceof; this contract needs rewriting");

        assertTrue(
                service.contains("instanceof InteractionWired"),
                "RoomWiredVisibilityService must select wired the same way RoomManager hides it");

        assertTrue(
                service.contains("getFloorItems()"),
                "the list must come from the room's floor items, the same source RoomManager filters");
    }

    @Test
    void theCollisionSideDoesNotNarrowTheSetAgain() throws Exception {
        String service = Files.readString(VISIBILITY_SERVICE);

        assertFalse(
                service.contains("getRoomSpecialTypes()"),
                "RoomSpecialTypes holds only registered triggers/effects/conditions/extras: building the "
                        + "hide list from it is what left unregistered wired invisible but still colliding");
    }

    /**
     * The height half: standing where a hidden box was must not leave the avatar in the air.
     *
     * <p>{@code getStackHeight} decides how high a tile is, and it has to ignore hidden wired the same
     * way the tile state does - otherwise the boxes stop blocking but still lift whoever walks there.
     */
    @Test
    void stackHeightIgnoresHiddenWired() throws Exception {
        String tileManager =
                Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/rooms/RoomTileManager.java"));

        assertTrue(
                tileManager.contains("getTopItemAt(x, y, exclude, this::isHiddenWired)"),
                "getStackHeight must hand getTopItemAt the hidden-wired filter, or a hidden box still "
                        + "sets the height of the tile it stands on");
    }

    /** Recalculating the tiles is only half of it - whoever stands on them has to be settled too. */
    @Test
    void hidingWiredAlsoSettlesWhoeverStandsOnIt() throws Exception {
        String service = Files.readString(VISIBILITY_SERVICE);

        assertTrue(
                service.contains("updateTiles(") && service.contains("updateHabbosAt("),
                "a unit keeps the height it arrived with, so hiding a stack under someone must update "
                        + "the units as well as the tiles, the way RoomItemMovementService does");
    }
}
