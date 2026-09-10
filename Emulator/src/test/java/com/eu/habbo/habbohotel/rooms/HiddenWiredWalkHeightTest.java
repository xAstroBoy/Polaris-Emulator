package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWired;
import com.eu.habbo.habbohotel.users.HabboItem;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * How high a walking unit stands on a tile whose only furni are hidden wired.
 *
 * <p>Room 1 ("test") is exactly this: two wired boxes stacked at (9,18), z=0.00 and z=0.65, each 0.65
 * tall and {@code allow_walk = 1}. With ":hidewired" on, the tile correctly reported height 0.00 - the
 * collision overlay showed 0.00 on every tile - and the avatar still stood at 1.30, on top of boxes
 * that were not on screen.
 *
 * <p>The two numbers came from two different lookups. The tile's height goes through
 * {@code RoomTileManager.getStackHeight}, which passes a hidden-wired filter to {@code getTopItemAt}.
 * A walking unit goes through {@code RoomUnit} to {@code getWalkableItemAt}, which did not. One tile,
 * two answers.
 */
class HiddenWiredWalkHeightTest {

    private static final int X = 9;
    private static final int Y = 18;

    /** A wired box as room 1 has them: 0.65 tall, walkable, stackable. */
    private static HabboItem wiredBoxAt(double z) {
        Item baseItem = mock(Item.class);

        lenient().when(baseItem.allowSit()).thenReturn(false);
        lenient().when(baseItem.allowLay()).thenReturn(false);
        lenient().when(baseItem.allowWalk()).thenReturn(true);
        lenient().when(baseItem.allowStack()).thenReturn(true);
        lenient().when(baseItem.getHeight()).thenReturn(0.65);

        HabboItem item = mock(InteractionWired.class);

        lenient().when(item.getBaseItem()).thenReturn(baseItem);
        lenient().when(item.getZ()).thenReturn(z);
        lenient().when(item.isWalkable()).thenReturn(true);

        return item;
    }

    private static RoomItemManager managerWithStack(boolean wiredHidden, Set<HabboItem> stack) {
        RoomLayout layout = mock(RoomLayout.class);
        Room room = mock(Room.class);

        lenient().when(layout.getHeightAtSquare(X, Y)).thenReturn((short) 0);
        lenient().when(layout.getTile((short) X, (short) Y))
                .thenReturn(new RoomTile((short) X, (short) Y, (short) 0, RoomTileState.OPEN, true));
        lenient().when(room.getLayout()).thenReturn(layout);
        lenient().when(room.isAllowUnderpass()).thenReturn(false);
        lenient().when(room.isWiredHidden()).thenReturn(wiredHidden);

        // A RoomItemManager whose only job here is to hand back the tile's items; everything else the
        // methods under test do is real.
        return new RoomItemManager(room) {
            @Override
            public Set<HabboItem> getItemsAt(int x, int y) {
                return stack;
            }
        };
    }

    private static Set<HabboItem> roomOneStack() {
        Set<HabboItem> stack = new LinkedHashSet<>();

        stack.add(wiredBoxAt(0.00));
        stack.add(wiredBoxAt(0.65));

        return stack;
    }

    @Test
    void aWalkerStandsOnVisibleWired() {
        HabboItem walkable = managerWithStack(false, roomOneStack()).getWalkableItemAt(X, Y);

        assertEquals(0.65, walkable.getZ(), 0.0001, "the top visible box is what you stand on");
    }

    @Test
    void aWalkerIgnoresHiddenWiredAndStaysOnTheFloor() {
        assertNull(
                managerWithStack(true, roomOneStack()).getWalkableItemAt(X, Y),
                "with :hidewired on there is nothing to stand on, so the walk height falls back to the "
                        + "floor - the same answer the tile already gave");
    }

    @Test
    void botsDoNotPerchOnHiddenWiredEither() {
        assertEquals(
                0.0,
                managerWithStack(true, roomOneStack()).getTopHeightAt(X, Y),
                0.0001,
                "getTopHeightAt places bots, and it has to agree with the tile as well");
    }
}
