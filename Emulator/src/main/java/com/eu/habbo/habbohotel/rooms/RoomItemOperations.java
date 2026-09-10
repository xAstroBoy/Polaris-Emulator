package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.interactions.InteractionMultiHeight;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.rooms.items.FloorItemUpdateComposer;
import com.eu.habbo.messages.outgoing.rooms.items.ItemStateComposer;
import com.eu.habbo.messages.outgoing.rooms.items.WallItemUpdateComposer;
import java.util.List;

final class RoomItemOperations {

    private final Room room;

    RoomItemOperations(Room room) {
        this.room = room;
    }

    void updateItem(HabboItem item) {
        if (!this.room.isLoaded()
                || item == null
                || item.getRoomId() != this.room.getId()
                || item.getBaseItem() == null) {
            return;
        }

        if (item.getBaseItem().getType() == FurnitureType.FLOOR) {
            this.room.sendComposer(new FloorItemUpdateComposer(item).compose());
            this.room.updateTiles(this.room
                    .getLayout()
                    .getTilesAt(this.room.currentLayout().getTile(item.getX(), item.getY()), item));

            if (RoomAreaHideSupport.isControllerItem(item)) {
                RoomAreaHideSupport.sendState(this.room, item);
            }

            // Whether wired is hidden is cached on the room, because the tile maths asks once per tile and
            // resolving it walks every floor item. The answer depends on a conf_hidewired controller's
            // extradata, and that flips from places that never told the room: a wired toggle, or a
            // controller furni that resolved to a plain switch instead of InteractionHideWiredControl.
            // The room then hid the boxes on entry - that check is live - while the cache still said
            // "visible", so the tiles kept colliding with furni nobody could see.
            if (RoomHideWiredSupport.isControllerItem(item)) {
                this.room.refreshWiredHidden();
            }

            this.room.onFurnitureTopologyChanged();
        } else if (item.getBaseItem().getType() == FurnitureType.WALL) {
            this.room.sendComposer(new WallItemUpdateComposer(item).compose());
        }

        this.persistDirtyItem(item);
    }

    void updateItemState(HabboItem item) {
        if (item == null) {
            return;
        }

        if (RoomAreaHideSupport.isControllerItem(item)) {
            this.updateItem(item);
            return;
        }

        // Same reason as in updateItem: a controller whose state just changed has to refresh the cache,
        // and this is the path a state toggle takes.
        if (RoomHideWiredSupport.isControllerItem(item)) {
            this.room.refreshWiredHidden();
        }

        if (!item.isLimited()) {
            this.room.sendComposer(new ItemStateComposer(item).compose());
        } else {
            this.room.sendComposer(new FloorItemUpdateComposer(item).compose());
        }

        if (item.getBaseItem().getType() == FurnitureType.FLOOR) {
            if (this.room.currentLayout() == null) {
                this.persistDirtyItem(item);
                return;
            }

            this.room.updateTiles(this.room
                    .getLayout()
                    .getTilesAt(this.room.currentLayout().getTile(item.getX(), item.getY()), item));

            if (item instanceof InteractionMultiHeight multiHeight) {
                multiHeight.updateUnitsOnItem(this.room);
            }
        }

        if (item.getBaseItem().getType() == FurnitureType.FLOOR
                && (RoomConfInvisSupport.isControllerItem(item) || RoomConfInvisSupport.isTarget(item))) {
            RoomConfInvisSupport.sendState(this.room);
        }

        if (item.getBaseItem().getType() == FurnitureType.FLOOR && RoomHanditemBlockSupport.isControllerItem(item)) {
            RoomHanditemBlockSupport.sendState(this.room);
        }

        if (item.getBaseItem().getType() == FurnitureType.FLOOR) {
            this.room.onFurnitureTopologyChanged();
        }

        this.persistDirtyItem(item);
    }

    private void persistDirtyItem(HabboItem item) {
        if (item.needsUpdate()) {
            this.room.savePendingItems(List.of(item));
        }
    }
}
