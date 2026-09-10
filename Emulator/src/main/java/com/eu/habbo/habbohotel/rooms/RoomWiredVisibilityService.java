package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.habbohotel.items.interactions.InteractionWired;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.rooms.items.RemoveFloorItemComposer;
import com.eu.habbo.messages.outgoing.rooms.items.RoomFloorItemsComposer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Keeps the two halves of "wired is hidden" together: what the clients are shown, and what the
 * room's tiles collide with. Hiding used to be visuals only, which left an invisible wall on every
 * tile a wired box stood on and floated avatars over the stacked ones.
 */
final class RoomWiredVisibilityService {

    private final Room room;

    /** What the clients and the tile states currently reflect. */
    private boolean applied;

    RoomWiredVisibilityService(Room room) {
        this.room = room;
    }

    void setHidden(boolean hidden) {
        this.room.updateHideWiredState(hidden);
        this.refresh();
    }

    /**
     * Recomputes whether wired is hidden and, when that changed, republishes or removes the boxes
     * and recalculates the tiles they stand on, so walking matches what the room looks like.
     */
    synchronized void refresh() {
        boolean hidden = this.room.isHideWired() || RoomHideWiredSupport.isActive(this.room);
        boolean changed = hidden != this.applied;

        this.applied = hidden;
        // Set before the tiles are recalculated: the tile maths reads this flag.
        this.room.setWiredHiddenFlag(hidden);

        List<HabboItem> wired = this.wiredItems();

        // Only the visuals care whether this is a change: republishing boxes the clients already have,
        // or removing ones they never got, is noise on the wire.
        if (changed) {
            if (hidden) {
                this.remove(wired);
            } else {
                this.publish(wired);
            }
        }

        // The tiles are recalculated every time, change or not. Recalculating is idempotent, and the two
        // halves of "wired is hidden" have drifted apart before: the room decides what to draw live, this
        // cache decides what collides, and any path that moved one without the other left tiles blocking
        // furni nobody could see. Cheap insurance against the next such path.
        Set<RoomTile> touched = this.tilesUnder(wired);

        this.room.updateTiles(touched);
        this.settleOccupants(touched);
    }

    /**
     * Brings whoever is standing on those tiles down to the new floor, or back up onto the boxes.
     *
     * <p>Recalculating the tiles is only half of it: a unit keeps the height it arrived with until
     * something moves it. Hide a stack of wired from under someone and the boxes vanish while the
     * avatar stays in the air, standing on nothing - which is what this looked like from inside the
     * room. Every other place that makes furniture appear or disappear under people does this too;
     * see {@link RoomItemMovementService}, which pairs updateTiles with updateHabbosAt/updateBotsAt.
     */
    private void settleOccupants(Set<RoomTile> tiles) {
        for (RoomTile tile : tiles) {
            this.room.updateHabbosAt(tile.x, tile.y, this.room.getHabbosAt(tile.x, tile.y));
            this.room.updateBotsAt(tile.x, tile.y);
        }
    }

    /**
     * Every wired box on the floor, found the same way the room decides what to hide.
     *
     * <p>This used to read the four {@link RoomSpecialTypes} collections - triggers, effects,
     * conditions, extras - while {@code RoomManager} hides anything that is {@code instanceof
     * InteractionWired}. The two disagreed for any wired box missing from those collections: the room
     * stopped drawing it, and this list never named it, so its tiles were never recalculated. It went
     * invisible and kept colliding - a stack you could not see and could not walk through.
     *
     * <p>One predicate for both halves now, so what disappears and what stops colliding cannot drift.
     */
    private List<HabboItem> wiredItems() {
        List<HabboItem> items = new ArrayList<>();

        for (HabboItem item : this.room.getFloorItems()) {
            if (item instanceof InteractionWired) {
                items.add(item);
            }
        }

        return items;
    }

    private Set<RoomTile> tilesUnder(Collection<? extends HabboItem> items) {
        Set<RoomTile> tiles = new HashSet<>();
        RoomLayout layout = this.room.getLayout();

        if (layout == null) {
            return tiles;
        }

        for (HabboItem item : items) {
            tiles.addAll(item.getOccupyingTiles(layout));
        }

        return tiles;
    }

    private void remove(Collection<? extends HabboItem> items) {
        for (HabboItem item : items) {
            this.room.sendComposer(new RemoveFloorItemComposer(item).compose());
        }
    }

    private void publish(Collection<? extends HabboItem> items) {
        this.room.sendComposer(new RoomFloorItemsComposer(this.room.getFurniOwnerNames(), items).compose());
    }
}
