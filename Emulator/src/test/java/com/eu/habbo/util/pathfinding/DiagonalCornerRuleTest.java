package com.eu.habbo.util.pathfinding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomLayout;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.rooms.pathfinding.impl.AdjacentTileFinder;
import com.eu.habbo.habbohotel.rooms.pathfinding.impl.PathfinderContext;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The corner rule decides whether a diagonal step may squeeze between the two tiles beside it.
 *
 * <p>It asked {@code RoomTile.isWalkable()}, which is true only for {@link RoomTileState#OPEN}, so a
 * seat counted as a wall. In a room floored with seats every corner is a seat, and the room loses
 * diagonal movement entirely however the room flag or ":diagonali" is set - which is how a hall of 239
 * poufs ended up letting units hop in straight lines only.
 *
 * <p>What the rule is actually for is furniture you cannot enter, so that is what it tests now.
 */
class DiagonalCornerRuleTest {

    /** Moving from (1,1) to (2,2); the corners the rule inspects are (2,1) and (1,2). */
    private static final short FROM_X = 1;
    private static final short FROM_Y = 1;
    private static final short TO_X = 2;
    private static final short TO_Y = 2;

    private static boolean diagonalBlockedBetween(RoomTileState cornerA, RoomTileState cornerB) {
        Map<Long, RoomTile> cache = new HashMap<>();

        // Pre-seeding the cache keeps findTile away from the room, so no Room has to be built here.
        put(cache, TO_X, FROM_Y, cornerA);
        put(cache, FROM_X, TO_Y, cornerB);

        PathfinderContext context =
                new PathfinderContext(null, null, null, null, false, true, null, false, cache);

        return AdjacentTileFinder.isBlockedDiagonal(context, FROM_X, FROM_Y, TO_X, TO_Y);
    }

    private static void put(Map<Long, RoomTile> cache, short x, short y, RoomTileState state) {
        cache.put(((long) x << 32) | (y & 0xFFFFFFFFL), new RoomTile(x, y, (short) 0, state, true));
    }

    /** Only the map size is ever asked for here: the tiles themselves come from the seeded cache. */
    private static Room roomOf(int sizeX, int sizeY) {
        RoomLayout layout = mock(RoomLayout.class);
        Room room = mock(Room.class);

        when(layout.getMapSizeX()).thenReturn(sizeX);
        when(layout.getMapSizeY()).thenReturn(sizeY);
        when(room.getLayout()).thenReturn(layout);

        return room;
    }

    @Test
    void seatsOnBothCornersDoNotBlockTheDiagonal() {
        assertFalse(
                diagonalBlockedBetween(RoomTileState.SIT, RoomTileState.SIT),
                "a unit can stand on a seat, so two seats are not a wall to squeeze between");
    }

    @Test
    void bedsOnBothCornersDoNotBlockTheDiagonal() {
        assertFalse(
                diagonalBlockedBetween(RoomTileState.LAY, RoomTileState.LAY),
                "a bed is enterable in the same way a seat is");
    }

    @Test
    void solidFurniOnBothCornersStillBlocksTheDiagonal() {
        assertTrue(
                diagonalBlockedBetween(RoomTileState.BLOCKED, RoomTileState.BLOCKED),
                "the rule exists to stop a unit slipping between two solid furni");
    }

    /**
     * The diagonal bridge: two floor tiles joined only corner to corner, with nothing beside them.
     *
     * <p>Reported on an empty floor with no furniture at all - you stood on the tip of one shape and
     * could not step to the tile touching it diagonally, because both tiles beside the step are holes
     * and the rule read a hole as a wall.
     */
    @Test
    void holesInTheRoomModelDoNotBlockTheDiagonal() {
        assertFalse(
                diagonalBlockedBetween(RoomTileState.INVALID, RoomTileState.INVALID),
                "a hole is absent, not solid: there is nothing there to squeeze between");
    }

    @Test
    void oneOpenCornerIsAlwaysEnough() {
        assertFalse(
                diagonalBlockedBetween(RoomTileState.OPEN, RoomTileState.BLOCKED),
                "the rule only ever closed a diagonal when BOTH corners were impassable");
    }

    /** A tile off the edge of the map is absent for the same reason a hole is. */
    @Test
    void tilesOffTheMapDoNotBlockTheDiagonal() {
        Map<Long, RoomTile> cache = new HashMap<>();

        // Neither corner is seeded, so findTile returns null for both.
        PathfinderContext context =
                new PathfinderContext(roomOf(3, 3), null, null, null, false, true, null, false, cache);

        assertFalse(
                AdjacentTileFinder.isBlockedDiagonal(context, FROM_X, FROM_Y, TO_X, TO_Y),
                "a missing tile is not a wall; the destination is validated on its own");
    }

    /**
     * The step the A* is actually offered, rather than the corner rule on its own.
     *
     * <p>On plain floor a unit that may move diagonally has to be handed all four diagonal neighbours,
     * otherwise no amount of room flag or ":diagonali" produces a diagonal walk.
     */
    @Test
    void openFloorOffersAllFourDiagonals() {
        Map<Long, RoomTile> cache = new HashMap<>();

        for (short x = 0; x <= 2; x++) {
            for (short y = 0; y <= 2; y++) {
                put(cache, x, y, RoomTileState.OPEN);
            }
        }

        RoomTile centre = cache.get(((long) 1 << 32) | 1L);
        PathfinderContext context =
                new PathfinderContext(roomOf(3, 3), centre, centre, null, false, true, null, false, cache);

        Set<RoomTile> adjacent = new HashSet<>();
        AdjacentTileFinder.addDiagonalAdjacent(context, centre, centre, null, (short) 1, (short) 1, adjacent, false);

        assertEquals(4, adjacent.size(), "a unit on open floor must be offered every diagonal neighbour");
    }

    /** The pouf hall: floor made of seats, which is where the diagonals had gone missing. */
    @Test
    void aFloorOfSeatsOffersAllFourDiagonals() {
        Map<Long, RoomTile> cache = new HashMap<>();

        for (short x = 0; x <= 2; x++) {
            for (short y = 0; y <= 2; y++) {
                put(cache, x, y, RoomTileState.SIT);
            }
        }

        RoomTile centre = cache.get(((long) 1 << 32) | 1L);
        // Every seat is reachable as the goal, which is how a unit hops from pouf to pouf.
        PathfinderContext context =
                new PathfinderContext(roomOf(3, 3), centre, centre, null, false, true, null, false, cache);

        Set<RoomTile> adjacent = new HashSet<>();

        for (short[] step : new short[][] {{1, 1}, {-1, -1}, {-1, 1}, {1, -1}}) {
            short targetX = (short) (1 + step[0]);
            short targetY = (short) (1 + step[1]);
            RoomTile goal = cache.get(((long) targetX << 32) | (targetY & 0xFFFFFFFFL));

            context.setGoalLocation(goal);
            AdjacentTileFinder.addDiagonalAdjacent(context, centre, goal, null, (short) 1, (short) 1, adjacent, false);
        }

        assertEquals(4, adjacent.size(), "each diagonally adjacent pouf must be reachable when it is the goal");
    }
}
