package com.eu.habbo.habbohotel.commands;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import org.junit.jupiter.api.Test;

/**
 * "Room owner" has to mean the owner.
 *
 * <p>A permission whose setting is ROOM_OWNER is granted when the command handler tells the rank it
 * is dealing with the room's owner. That answer used to be yes for anyone holding rights in the
 * room, for anyone carrying acc_placefurni anywhere in the hotel, and for any group member at
 * GUILD_RIGHTS - so every ROOM_OWNER permission was in reach of all three. The room event commands
 * were exactly that: set to ROOM_OWNER for members, and open to every rights-holder in any room
 * that had them.
 *
 * <p>Rights are not ownership. An owner hands out rights so friends can move furni; it is not a
 * transfer of the room. Staff who are meant to act as the owner of any room carry acc_anyroomowner,
 * and that stays.
 */
class RoomOwnerPermissionBypassTest {

    @Test
    void theOwnerOfTheRoomCounts() {
        Habbo habbo = habbo();
        Room room = room(habbo, true);

        when(habbo.getHabboInfo().getCurrentRoom()).thenReturn(room);

        assertTrue(CommandHandler.isRoomOwner(clientFor(habbo)));
    }

    @Test
    void aRightsHolderIsNotTheOwner() {
        Habbo habbo = habbo();
        Room room = room(habbo, false);

        // The classic bypass: rights in the room, no ownership.
        when(room.hasRights(habbo)).thenReturn(true);
        when(habbo.getHabboInfo().getCurrentRoom()).thenReturn(room);

        assertFalse(CommandHandler.isRoomOwner(clientFor(habbo)));
    }

    /**
     * acc_placefurni is a staff furni permission. Treating it as ownership handed every ROOM_OWNER
     * permission in the hotel to whoever held it, in rooms they had nothing to do with.
     */
    @Test
    void placingFurniAsStaffIsNotOwningTheRoom() {
        Habbo habbo = habbo();
        Room room = room(habbo, false);

        when(habbo.hasPermission(Permission.ACC_PLACEFURNI)).thenReturn(true);
        when(habbo.getHabboInfo().getCurrentRoom()).thenReturn(room);

        assertFalse(CommandHandler.isRoomOwner(clientFor(habbo)));
    }

    @Test
    void staffWithAnyRoomOwnerStillCount() {
        Habbo habbo = habbo();
        Room room = room(habbo, false);

        when(habbo.hasPermission(Permission.ACC_ANYROOMOWNER)).thenReturn(true);
        when(habbo.getHabboInfo().getCurrentRoom()).thenReturn(room);

        assertTrue(CommandHandler.isRoomOwner(clientFor(habbo)));
    }

    @Test
    void standingInNoRoomIsNotOwningOne() {
        Habbo habbo = habbo();

        when(habbo.getHabboInfo().getCurrentRoom()).thenReturn(null);

        assertFalse(CommandHandler.isRoomOwner(clientFor(habbo)));
    }

    private static Habbo habbo() {
        Habbo habbo = mock(Habbo.class);
        HabboInfo info = mock(HabboInfo.class);

        when(habbo.getHabboInfo()).thenReturn(info);

        return habbo;
    }

    private static Room room(Habbo habbo, boolean owned) {
        Room room = mock(Room.class);

        when(room.isOwner(habbo)).thenReturn(owned);

        return room;
    }

    private static GameClient clientFor(Habbo habbo) {
        GameClient client = mock(GameClient.class);

        when(client.getHabbo()).thenReturn(habbo);

        return client;
    }
}
