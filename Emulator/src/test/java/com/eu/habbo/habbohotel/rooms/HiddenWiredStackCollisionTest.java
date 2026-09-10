package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWired;
import com.eu.habbo.habbohotel.users.HabboItem;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Two wired boxes stacked on one tile: an obstacle while they are visible, nothing at all once
 * ":hidewired" is on.
 *
 * <p>This is the case that kept coming back from the room. Hiding wired is supposed to make the boxes
 * as good as absent - not drawn, not collided with, not standing on. What happened instead was a stack
 * you could not see and still could not walk through, and standing where one had been left the avatar
 * hanging in the air at the height of an invisible box.
 */
class HiddenWiredStackCollisionTest {

    private static final short X = 3;
    private static final short Y = 4;

    /** A wired box: solid, not a seat, not a bed, one unit tall. */
    private static HabboItem wiredBoxAt(double z) {
        Item baseItem = mock(Item.class);

        lenient().when(baseItem.allowSit()).thenReturn(false);
        lenient().when(baseItem.allowLay()).thenReturn(false);
        lenient().when(baseItem.allowWalk()).thenReturn(false);
        lenient().when(baseItem.allowStack()).thenReturn(true);
        lenient().when(baseItem.getHeight()).thenReturn(1.0);

        HabboItem item = mock(InteractionWired.class);

        lenient().when(item.getBaseItem()).thenReturn(baseItem);
        lenient().when(item.getZ()).thenReturn(z);
        lenient().when(item.isWalkable()).thenReturn(false);
        lenient().when(item.getOverrideTileState(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(null);

        return item;
    }

    private static RoomTileState stateWithWiredHidden(boolean hidden) {
        RoomTile tile = new RoomTile(X, Y, (short) 0, RoomTileState.OPEN, true);

        // Stacked: one on the floor, one on top of it.
        Set<HabboItem> items = new LinkedHashSet<>();
        items.add(wiredBoxAt(0.0));
        items.add(wiredBoxAt(1.0));

        RoomItemManager itemManager = mock(RoomItemManager.class);
        Room room = mock(Room.class);

        lenient().when(itemManager.getItemsAt(tile)).thenReturn(items);
        lenient().when(itemManager.getItemsAt(org.mockito.ArgumentMatchers.<Class<HabboItem>>any(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(new LinkedHashSet<>());
        lenient().when(room.getItemManager()).thenReturn(itemManager);
        lenient().when(room.isAllowUnderpass()).thenReturn(false);
        when(room.isWiredHidden()).thenReturn(hidden);

        return new RoomTileManager(room).calculateTileState(tile);
    }

    @Test
    void visibleWiredIsAnObstacle() {
        assertEquals(
                RoomTileState.BLOCKED,
                stateWithWiredHidden(false),
                "a stack of wired boxes you can see has to block the tile, the way any solid furni does");
    }

    @Test
    void hiddenWiredLeavesTheTileEmpty() {
        assertEquals(
                RoomTileState.OPEN,
                stateWithWiredHidden(true),
                "with :hidewired on the boxes are as good as absent: the tile must be plain floor again");
    }
}
