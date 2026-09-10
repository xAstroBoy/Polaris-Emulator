package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWired;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.plugin.PluginManager;
import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Standing where a hidden wired stack is must leave you on the floor, not on top of the boxes.
 *
 * <p>Rooms in this hotel really do stack wired four deep - "Caffetteria Floreale" has one at (27,14)
 * running z=0 to z=1.95. With ":hidewired" on, the boxes are gone from the screen; if the tile keeps
 * their height the avatar walks up onto nothing, which is what kept being reported.
 *
 * <p>The item manager is simulated rather than mocked blind: its answer honours the skip predicate the
 * way the real one does, so the thing actually under test is whether {@code getStackHeight} passes that
 * predicate at all.
 */
class HiddenWiredStackHeightTest {

    private static final short X = 27;
    private static final short Y = 14;
    private static final double FLOOR = 0.0;

    private Field pluginManagerField;
    private Object originalPluginManager;

    @BeforeEach
    void setUp() throws Exception {
        this.pluginManagerField = Emulator.class.getDeclaredField("pluginManager");
        this.pluginManagerField.setAccessible(true);
        this.originalPluginManager = this.pluginManagerField.get(null);
        this.pluginManagerField.set(null, mock(PluginManager.class));
    }

    @AfterEach
    void tearDown() throws Exception {
        this.pluginManagerField.set(null, this.originalPluginManager);
    }

    private static HabboItem wiredBoxAt(double z) {
        Item baseItem = mock(Item.class);

        lenient().when(baseItem.allowSit()).thenReturn(false);
        lenient().when(baseItem.allowLay()).thenReturn(false);
        lenient().when(baseItem.allowWalk()).thenReturn(false);
        lenient().when(baseItem.allowStack()).thenReturn(true);
        lenient().when(baseItem.getHeight()).thenReturn(0.65);

        HabboItem item = mock(InteractionWired.class);

        lenient().when(item.getBaseItem()).thenReturn(baseItem);
        lenient().when(item.getZ()).thenReturn(z);
        lenient().when(item.isWalkable()).thenReturn(false);

        return item;
    }

    private static double stackHeightWithWiredHidden(boolean hidden) {
        // The real stack from Caffetteria Floreale: four boxes, the top one at 1.95.
        Set<HabboItem> stack = new LinkedHashSet<>();
        stack.add(wiredBoxAt(0.00));
        stack.add(wiredBoxAt(0.65));
        stack.add(wiredBoxAt(1.30));
        stack.add(wiredBoxAt(1.95));

        RoomLayout layout = mock(RoomLayout.class);
        RoomItemManager itemManager = mock(RoomItemManager.class);
        Room room = mock(Room.class);

        lenient().when(layout.getHeightAtSquare(X, Y)).thenReturn((short) FLOOR);
        lenient().when(room.getLayout()).thenReturn(layout);
        lenient().when(room.isAllowUnderpass()).thenReturn(false);
        lenient().when(room.isWiredHidden()).thenReturn(hidden);
        lenient().when(room.getItemManager()).thenReturn(itemManager);

        // No stack helpers on this tile.
        lenient().when(itemManager.getItemsAt(org.mockito.ArgumentMatchers.<Class<HabboItem>>any(), anyInt(), anyInt()))
                .thenReturn(new LinkedHashSet<>());

        // Behave like the real getTopItemAt: drop whatever the caller says to skip, keep the tallest.
        lenient().when(itemManager.getTopItemAt(anyInt(), anyInt(), any(), any())).thenAnswer(invocation -> {
            Predicate<HabboItem> skip = invocation.getArgument(3);

            return stack.stream()
                    .filter(item -> skip == null || !skip.test(item))
                    .max(Comparator.comparingDouble(item -> item.getZ() + Item.getCurrentHeight(item)))
                    .orElse(null);
        });

        return new RoomTileManager(room).getStackHeight(X, Y, false);
    }

    @Test
    void visibleWiredStackRaisesTheTile() {
        assertEquals(
                2.60,
                stackHeightWithWiredHidden(false),
                0.0001,
                "four visible boxes of 0.65 stand 2.60 tall, and the tile has to say so");
    }

    @Test
    void hiddenWiredStackLeavesTheTileOnTheFloor() {
        assertEquals(
                FLOOR,
                stackHeightWithWiredHidden(true),
                0.0001,
                "with :hidewired on the boxes are not there, so the tile is floor height and nobody "
                        + "ends up standing in the air on top of them");
    }
}
