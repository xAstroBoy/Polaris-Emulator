package com.eu.habbo.messages.incoming.rooms;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.VideoCurtainManager;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.VideoCurtainStateComposer;

/** CUSTOM packet 10125: asks for the current room's curtain state. No payload. */
public class VideoCurtainStateRequestEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        if (habbo == null || habbo.getHabboInfo().getCurrentRoom() == null) return;

        Room room = habbo.getHabboInfo().getCurrentRoom();
        this.client.sendResponse(new VideoCurtainStateComposer(room.getId(), VideoCurtainManager.get(room.getId())));
    }
}
