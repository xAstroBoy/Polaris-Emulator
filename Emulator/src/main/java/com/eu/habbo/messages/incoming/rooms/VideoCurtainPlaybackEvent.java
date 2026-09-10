package com.eu.habbo.messages.incoming.rooms;

import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.VideoCurtainManager;
import com.eu.habbo.habbohotel.rooms.VideoCurtainManager.State;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.VideoCurtainStateComposer;

/** CUSTOM packet 10124: bool playing, int position (seconds). Pauses / resumes / seeks the curtain for the room. */
public class VideoCurtainPlaybackEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        boolean playing = this.packet.readBoolean();
        int position = this.packet.readInt();
        Habbo habbo = this.client.getHabbo();

        if (habbo == null || habbo.getHabboInfo().getCurrentRoom() == null) return;

        Room room = habbo.getHabboInfo().getCurrentRoom();
        if (!room.hasRights(habbo) && !habbo.hasPermission(Permission.ACC_ANYROOMOWNER)) return;

        State state = VideoCurtainManager.playback(room.getId(), playing, position, habbo.getHabboInfo().getId());
        if (state == null) return;

        room.sendComposer(new VideoCurtainStateComposer(room.getId(), state).compose());
    }
}
