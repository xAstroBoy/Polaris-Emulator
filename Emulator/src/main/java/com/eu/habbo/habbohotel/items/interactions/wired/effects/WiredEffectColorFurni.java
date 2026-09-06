package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.WiredPlatform;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.ItemManager;
import com.eu.habbo.habbohotel.items.interactions.FurnitureCustomColors;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import com.eu.habbo.messages.outgoing.rooms.items.AddFloorItemComposer;
import com.eu.habbo.messages.outgoing.rooms.items.RemoveFloorItemComposer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * WIRED effect "wf_act_furni_color" (client layout code 115): sets a colour on colourable furni.
 *
 * Colourable furni are the ones that exist as colour variants in items_base, i.e. rows named
 * {@code <classname>*<index>} (Habbo's own convention: rare_colourable_icecream*3, throne*2, ...). The effect swaps
 * every target to the variant with the chosen index — the row in {@code items} is repointed to that base item and the
 * furni is re-sent to the room — so the new colour is what everybody sees and it survives pick-ups and restarts.
 * Furni without variants are left untouched.
 */
public class WiredEffectColorFurni extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.COLOR_FURNI;
    private static final Logger LOGGER = LoggerFactory.getLogger(WiredEffectColorFurni.class);
    static final int MINIMUM_COLOR_INDEX = 0;
    static final int MAXIMUM_COLOR_INDEX = 99;

    private final List<Integer> itemIds = new ArrayList<>();
    private int colorIndex = 1;
    private int furniSource = WiredSourceUtil.SOURCE_SELECTED;
    /** Custom colours (0xRRGGBB, -1 = none) for guild-customised furni: the recolourable hlive_clr25 / habbox_clr lines. */
    private int customColorOne = -1;
    private int customColorTwo = -1;

    public WiredEffectColorFurni(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectColorFurni(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    /** Classname without the {@code *index} colour suffix. */
    public static String baseName(String name) {
        if (name == null) return "";
        int star = name.indexOf('*');
        return star >= 0 ? name.substring(0, star) : name;
    }

    /** The items_base row of the same furni in colour {@code colorIndex}, or null when that variant does not exist. */
    public static Item findVariant(ItemManager itemManager, Item current, int colorIndex) {
        if (itemManager == null || current == null || current.getName() == null) return null;

        String base = baseName(current.getName());
        if (base.isEmpty()) return null;

        Item variant = itemManager.getItem(base + "*" + colorIndex);
        if (variant == null && colorIndex == 0) variant = itemManager.getItem(base);
        if (variant == null || variant.getType() != current.getType()) return null;

        return variant;
    }

    @Override
    public void execute(WiredContext context) {
        if (context == null || context.room() == null) return;

        Room room = context.room();
        List<HabboItem> selected = new ArrayList<>();
        for (int itemId : this.itemIds) {
            HabboItem item = room.getHabboItem(itemId);
            if (item != null) selected.add(item);
        }

        List<HabboItem> targets = WiredSourceUtil.resolveItems(context, this.furniSource, selected);
        if (targets == null || targets.isEmpty()) return;

        ItemManager itemManager = Emulator.getGameEnvironment().getItemManager();
        int recolored = 0;

        for (HabboItem target : new ArrayList<>(targets)) {
            if (target == null || target.getBaseItem() == null || target.getRoomId() != room.getId()) continue;
            if (room.getHabboItem(target.getId()) != target) continue;

            // Recolourable furni (guild-customised layers COLOR1/COLOR2): tint in place, nothing to swap.
            // Any interaction qualifies - gates, rollers, teleports and vending machines are tintable too.
            if (this.customColorOne >= 0 && FurnitureCustomColors.isColorable(target.getBaseItem())) {
                String one = FurnitureCustomColors.colorToHex(this.customColorOne);
                String two = FurnitureCustomColors.colorToHex(
                        this.customColorTwo < 0 ? this.customColorOne : this.customColorTwo);
                if (target.setCustomColors(one, two)) {
                    room.updateItem(target);
                    recolored++;
                }
                continue;
            }

            Item variant = findVariant(itemManager, target.getBaseItem(), this.colorIndex);
            if (variant == null || variant.getId() == target.getBaseItem().getId()) continue;

            if (recolor(room, itemManager, target, variant)) recolored++;
        }

        if (recolored > 0) {
            LOGGER.debug("Wired furni colour: {} furni set to colour {} in room {}", recolored, this.colorIndex, room.getId());
        }
    }

    private boolean recolor(Room room, ItemManager itemManager, HabboItem old, Item variant) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement("UPDATE items SET item_id = ? WHERE id = ?")) {
            statement.setInt(1, variant.getId());
            statement.setInt(2, old.getId());
            statement.execute();
        } catch (SQLException exception) {
            LOGGER.error("Wired furni colour: could not repoint item {} to base {}", old.getId(), variant.getId(), exception);
            return false;
        }

        // Position, rotation and extradata are flushed first so the fresh instance starts from the same row.
        if (old.needsUpdate()) old.run();

        HabboItem fresh = itemManager.loadHabboItem(old.getId());
        if (fresh == null) return false;

        room.sendComposer(new RemoveFloorItemComposer(old, true).compose());
        room.removeHabboItem(old);
        fresh.setRoomId(room.getId());
        room.addHabboItem(fresh);
        room.sendComposer(new AddFloorItemComposer(fresh, room.getFurniOwnerName(fresh.getUserId())).compose());
        room.updateItem(fresh);


        return true;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        Room room = getRoom();
        int[] parameters = settings == null ? null : settings.getIntParams();
        int[] selectedIds = settings == null || settings.getFurniIds() == null ? new int[0] : settings.getFurniIds();

        if (room == null || parameters == null || parameters.length < 1) {
            throw new WiredSaveException("Invalid furni colour effect data");
        }
        if (selectedIds.length > WiredManager.MAXIMUM_FURNI_SELECTION) {
            throw new WiredSaveException("Too many furni selected");
        }

        int delay = settings.getDelay();
        int maximumDelay = WiredPlatform.configuration().getInt("hotel.wired.max_delay", 20);
        if (delay < 0 || delay > maximumDelay) {
            throw new WiredSaveException("Delay out of range");
        }

        this.itemIds.clear();
        for (int itemId : selectedIds) {
            HabboItem item = room.getHabboItem(itemId);
            if (item == null) throw new WiredSaveException("Selected furni not found");
            this.itemIds.add(item.getId());
        }

        this.colorIndex = clamp(parameters[0], MINIMUM_COLOR_INDEX, MAXIMUM_COLOR_INDEX);
        this.furniSource = parameters.length > 1 ? WiredMovementPayloadGuard.furniSource(parameters[1]) : WiredSourceUtil.SOURCE_SELECTED;
        this.customColorOne = parameters.length > 2 ? clampColor(parameters[2]) : -1;
        this.customColorTwo = parameters.length > 3 ? clampColor(parameters[3]) : -1;

        if (!this.itemIds.isEmpty() && this.furniSource == WiredSourceUtil.SOURCE_TRIGGER) {
            this.furniSource = WiredSourceUtil.SOURCE_SELECTED;
        }

        this.setDelay(delay);
        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.colorIndex, this.furniSource, this.customColorOne, this.customColorTwo, this.getDelay(), new ArrayList<>(this.itemIds)));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        reset();

        JsonData data = WiredMovementPayloadGuard.fromJson(set.getString("wired_data"), JsonData.class);
        if (data == null) return;

        this.colorIndex = clamp(data.colorIndex, MINIMUM_COLOR_INDEX, MAXIMUM_COLOR_INDEX);
        this.furniSource = WiredMovementPayloadGuard.furniSource(data.furniSource);
        this.customColorOne = clampColor(data.customColorOne);
        this.customColorTwo = clampColor(data.customColorTwo);
        this.setDelay(WiredMovementPayloadGuard.delay(data.delay));

        if (data.itemIds != null) {
            for (Integer itemId : data.itemIds) {
                if (itemId != null && room.getHabboItem(itemId) != null && this.itemIds.size() < WiredManager.MAXIMUM_FURNI_SELECTION) {
                    this.itemIds.add(itemId);
                }
            }
        }

        if (!this.itemIds.isEmpty() && this.furniSource == WiredSourceUtil.SOURCE_TRIGGER) {
            this.furniSource = WiredSourceUtil.SOURCE_SELECTED;
        }
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        List<Integer> selected = new ArrayList<>();
        for (int itemId : this.itemIds) {
            if (room != null && room.getHabboItem(itemId) != null) selected.add(itemId);
        }

        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(selected.size());
        for (int itemId : selected) message.appendInt(itemId);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(4);
        message.appendInt(this.colorIndex);
        message.appendInt(this.furniSource);
        message.appendInt(this.customColorOne);
        message.appendInt(this.customColorTwo);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    @Override
    public boolean requiresTriggeringUser() {
        return false;
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public void onPickUp() {
        reset();
    }

    private void reset() {
        this.itemIds.clear();
        this.colorIndex = 1;
        this.furniSource = WiredSourceUtil.SOURCE_SELECTED;
        this.customColorOne = -1;
        this.customColorTwo = -1;
        this.setDelay(0);
    }

    private static int clampColor(int value) {
        return value < 0 ? -1 : value & 0xFFFFFF;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    static class JsonData {
        int colorIndex = 1;
        int furniSource = WiredSourceUtil.SOURCE_SELECTED;
        int customColorOne = -1;
        int customColorTwo = -1;
        int delay;
        List<Integer> itemIds;

        JsonData() {}

        JsonData(int colorIndex, int furniSource, int customColorOne, int customColorTwo, int delay, List<Integer> itemIds) {
            this.colorIndex = colorIndex;
            this.furniSource = furniSource;
            this.customColorOne = customColorOne;
            this.customColorTwo = customColorTwo;
            this.delay = delay;
            this.itemIds = itemIds;
        }
    }
}
