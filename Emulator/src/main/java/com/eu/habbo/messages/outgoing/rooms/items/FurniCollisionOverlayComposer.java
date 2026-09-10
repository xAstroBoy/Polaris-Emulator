package com.eu.habbo.messages.outgoing.rooms.items;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomLayout;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The tiles every furni in the room actually blocks, so the client can draw them over the floor.
 *
 * <p>The tiles are the ones {@link com.eu.habbo.habbohotel.rooms.RoomLayout#getTilesAt} hands the
 * movement checks - the same set the engine walks a unit around - rather than a width x length
 * rectangle recomputed for display. A furni whose artwork does not sit over its collision shows up
 * as exactly that: markers beside the picture instead of under it.
 */
public class FurniCollisionOverlayComposer extends MessageComposer {

    private final boolean active;
    private final Map<HabboItem, Set<RoomTile>> tiles;
    private final Room room;

    public FurniCollisionOverlayComposer(boolean active, Map<HabboItem, Set<RoomTile>> tiles) {
        this(active, tiles, null);
    }

    /**
     * @param room the room whose floor grid travels with the furni tiles, so the client can also draw
     *     what is walkable and how high each tile sits. Null sends the furni tiles alone.
     */
    public FurniCollisionOverlayComposer(boolean active, Map<HabboItem, Set<RoomTile>> tiles, Room room) {
        this.active = active;
        this.tiles = tiles;
        this.room = room;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.FurniCollisionOverlayComposer);
        this.response.appendBoolean(this.active);

        int count = 0;
        for (Set<RoomTile> occupied : this.tiles.values()) {
            count += occupied.size();
        }

        this.response.appendInt(count);

        for (Map.Entry<HabboItem, Set<RoomTile>> entry : this.tiles.entrySet()) {
            HabboItem item = entry.getKey();
            String name = item.getBaseItem() != null ? item.getBaseItem().getName() : "";
            int width = item.getBaseItem() != null ? item.getBaseItem().getWidth() : 1;
            int length = item.getBaseItem() != null ? item.getBaseItem().getLength() : 1;
            // Only a seat gets a direction arrow drawn for it: on a seat the rotation decides which way
            // the avatar ends up facing, which is the thing worth checking. On a wall or a floor tile it
            // is one more mark over an already busy floor.
            boolean seat = item.getBaseItem() != null && item.getBaseItem().allowSit();

            for (RoomTile tile : entry.getValue()) {
                this.response.appendInt(item.getId());
                this.response.appendString(name);
                this.response.appendInt(tile.x);
                this.response.appendInt(tile.y);
                this.response.appendString(String.valueOf(tile.getStackHeight()));
                // The furni's own tile is drawn differently: it is the one the client thinks it is on.
                this.response.appendBoolean(tile.x == item.getX() && tile.y == item.getY());
                this.response.appendInt(item.getRotation());
                this.response.appendInt(width);
                this.response.appendInt(length);
                this.response.appendBoolean(seat);
            }
        }

        this.appendFloor();

        return this.response;
    }

    /**
     * The room's own floor, tile by tile: whether a unit may stand there and how high it sits.
     *
     * <p>The furni tiles above answer "what does this furni block"; this answers "what does the floor
     * itself allow", which is the other half of every collision question - a hole in the model, a
     * tile the layout calls invalid, a step too tall to climb. Sent as a grid rather than a list so
     * the client can index it directly, and the whole room fits in a few kilobytes.
     */
    private void appendFloor() {
        RoomLayout layout = this.room == null ? null : this.room.getLayout();

        if (layout == null) {
            this.response.appendInt(0);
            this.response.appendInt(0);
            return;
        }

        int sizeX = layout.getMapSizeX();
        int sizeY = layout.getMapSizeY();

        this.response.appendInt(sizeX);
        this.response.appendInt(sizeY);

        for (int y = 0; y < sizeY; y++) {
            for (int x = 0; x < sizeX; x++) {
                RoomTile tile = layout.getTile((short) x, (short) y);

                if (tile == null) {
                    this.response.appendInt(RoomTileState.INVALID.ordinal());
                    this.response.appendBoolean(false);
                    this.response.appendString("0");
                    continue;
                }

                this.response.appendInt(tile.state.ordinal());
                this.response.appendBoolean(tile.isWalkable());
                this.response.appendString(String.valueOf(tile.getStackHeight()));
            }
        }
    }

    /** An empty overlay, which is how the client is told to clear what it drew. */
    public static FurniCollisionOverlayComposer cleared() {
        return new FurniCollisionOverlayComposer(false, Map.of());
    }

    /** @return the item ids carried by this overlay, for logging. */
    public List<Integer> itemIds() {
        return this.tiles.keySet().stream().map(HabboItem::getId).toList();
    }
}
