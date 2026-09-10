package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;

final class RoomFunCommandAccess {
    private static final int STAFF_RANK = 4;

    private RoomFunCommandAccess() {}

    /**
     * Room ownership no longer grants these events: they move real furniture and
     * lock avatars in place, so only staff (rank 4+) may fire them. The room
     * parameter stays for the call sites that already hold it.
     */
    static boolean requireStaff(Habbo habbo, Room room) {
        boolean staff = habbo.getHabboInfo().getRank() != null
                && habbo.getHabboInfo().getRank().getId() >= STAFF_RANK;
        if (staff) return true;

        habbo.whisper(
                Emulator.getTexts().getValue(
                        "commands.error.cmd_fun_room.permission",
                        "Solo lo staff può usare questo comando."),
                RoomChatMessageBubbles.ALERT);
        return false;
    }
}
