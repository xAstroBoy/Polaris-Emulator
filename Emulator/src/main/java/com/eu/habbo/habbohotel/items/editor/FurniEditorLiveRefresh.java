package com.eu.habbo.habbohotel.items.editor;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.ItemManager;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.users.inventory.ItemsComponent;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Applies an items_base change to the HabboItem instances that are already alive, without reloading any room.
 *
 * <p>{@link ItemManager#loadItems()} refreshes the shared {@link Item} definition in place (flags, dimensions, vending
 * ids ...), but every furni in a loaded room or in an online inventory is an instance of the interaction class chosen
 * when it was loaded, and the room's special-type registry (pet drinks, nests, wired, rollers ...) was filled at that
 * moment. This class re-creates each affected instance from its DB row and swaps it in place: same id, position and
 * extradata, new class, registry entries refreshed, and the clients receive a normal furni update packet so tiles,
 * sprite and dimensions are redrawn.
 */
public final class FurniEditorLiveRefresh {
    private static final Logger LOGGER = LoggerFactory.getLogger(FurniEditorLiveRefresh.class);

    /** What was touched, for the editor result message. */
    public record Result(int roomsTouched, int roomItems, int inventoryItems) {
        public String describe() {
            if (this.roomItems == 0 && this.inventoryItems == 0) return "";

            StringBuilder sb = new StringBuilder(" · ");
            if (this.roomItems > 0) {
                sb.append(this.roomItems)
                        .append(" furni aggiornati in ")
                        .append(this.roomsTouched)
                        .append(this.roomsTouched == 1 ? " stanza" : " stanze");
            }
            if (this.inventoryItems > 0) {
                if (this.roomItems > 0) sb.append(", ");
                sb.append(this.inventoryItems).append(" in inventario");
            }
            return sb.toString();
        }
    }

    private FurniEditorLiveRefresh() {}

    /**
     * Refreshes every live instance of the given base item. Must be called after {@link ItemManager#loadItems()} so
     * the shared definition already carries the new values.
     */
    public static Result apply(int baseItemId) {
        ItemManager itemManager = Emulator.getGameEnvironment().getItemManager();
        Item base = itemManager.getItem(baseItemId);
        if (base == null) return new Result(0, 0, 0);

        int roomsTouched = 0;
        int roomItems = 0;
        int inventoryItems = 0;

        for (Room room : new ArrayList<>(Emulator.getGameEnvironment().getRoomManager().getActiveRooms())) {
            if (room == null || !room.isLoaded()) continue;

            List<HabboItem> affected = new ArrayList<>();
            for (HabboItem item : new ArrayList<>(room.getFloorItems())) {
                if (isInstanceOf(item, baseItemId)) affected.add(item);
            }
            for (HabboItem item : new ArrayList<>(room.getWallItems())) {
                if (isInstanceOf(item, baseItemId)) affected.add(item);
            }
            if (affected.isEmpty()) continue;

            int refreshed = 0;
            for (HabboItem old : affected) {
                try {
                    HabboItem current = old;

                    if (needsReinstantiation(old, base)) {
                        HabboItem fresh = reload(itemManager, old);
                        if (fresh != null) {
                            room.removeHabboItem(old);
                            fresh.setRoomId(room.getId());
                            room.addHabboItem(fresh);
                            current = fresh;
                        }
                    }

                    // Re-sends the furni (new sprite/dimensions/flags) and recomputes the tiles it occupies.
                    room.updateItem(current);
                    refreshed++;
                } catch (Exception e) {
                    LOGGER.error(
                            "Furni editor live refresh failed for item {} in room {}", old.getId(), room.getId(), e);
                }
            }

            if (refreshed > 0) {
                roomsTouched++;
                roomItems += refreshed;
            }
        }

        for (Habbo habbo : new ArrayList<>(Emulator.getGameEnvironment().getHabboManager().getOnlineHabbos().values())) {
            try {
                if (habbo == null || habbo.getInventory() == null) continue;

                ItemsComponent inventory = habbo.getInventory().getItemsComponent();
                if (inventory == null) continue;

                for (HabboItem old : new ArrayList<>(inventory.getItemsAsValueCollection())) {
                    if (!isInstanceOf(old, baseItemId) || !needsReinstantiation(old, base)) continue;

                    HabboItem fresh = reload(itemManager, old);
                    if (fresh == null) continue;

                    inventory.removeHabboItem(old);
                    inventory.addItem(fresh);
                    inventoryItems++;
                }
            } catch (Exception e) {
                LOGGER.error("Furni editor live refresh failed for the inventory of user {}", habbo.getHabboInfo().getId(), e);
            }
        }

        if (roomItems > 0 || inventoryItems > 0) {
            LOGGER.info(
                    "Furni editor: base item {} refreshed live ({} furni in {} rooms, {} in inventories)",
                    baseItemId,
                    roomItems,
                    roomsTouched,
                    inventoryItems);
        }

        return new Result(roomsTouched, roomItems, inventoryItems);
    }

    private static boolean isInstanceOf(HabboItem item, int baseItemId) {
        return item != null && item.getBaseItem() != null && item.getBaseItem().getId() == baseItemId;
    }

    /** True when the definition now maps to another interaction class than the live instance. */
    private static boolean needsReinstantiation(HabboItem item, Item base) {
        Class<? extends HabboItem> expected =
                base.getInteractionType() != null ? base.getInteractionType().getType() : null;

        return expected != null && !expected.equals(item.getClass());
    }

    private static HabboItem reload(ItemManager itemManager, HabboItem old) {
        // Persist pending extradata/position first so the fresh instance starts from the same row.
        if (old.needsUpdate()) old.run();

        return itemManager.loadHabboItem(old.getId());
    }
}
