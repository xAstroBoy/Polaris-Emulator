package com.eu.habbo.messages.incoming.rooms;

import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.VideoCurtainManager;
import com.eu.habbo.habbohotel.rooms.VideoCurtainManager.State;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.VideoCurtainStateComposer;

/** CUSTOM packet 10123: string videoId ("" closes the curtain). Owner, rights holders and staff only. */
public class VideoCurtainSetEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        String videoId = this.packet.readString();
        Habbo habbo = this.client.getHabbo();

        if (habbo == null || habbo.getHabboInfo().getCurrentRoom() == null) return;

        Room room = habbo.getHabboInfo().getCurrentRoom();
        if (!room.hasRights(habbo) && !habbo.hasPermission(Permission.ACC_ANYROOMOWNER)) return;

        State state;
        if (videoId == null || videoId.trim().isEmpty()) {
            VideoCurtainManager.clear(room.getId());
            state = null;
        } else {
            state = VideoCurtainManager.set(room.getId(), videoId.trim(), habbo.getHabboInfo().getId());
            if (state == null) return;
        }

        room.sendComposer(new VideoCurtainStateComposer(room.getId(), state).compose());
    }
}
