package com.eu.habbo.messages.incoming.friends;

import com.eu.habbo.habbohotel.calls.CallManager;
import com.eu.habbo.messages.incoming.MessageHandler;

/** CUSTOM packet 10134: int targetUserId, bool video — call a friend. */
public class CallStartEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int targetId = this.packet.readInt();
        boolean video = this.packet.readBoolean();

        if (this.client.getHabbo() == null) return;

        CallManager.start(this.client.getHabbo(), targetId, video);
    }
}
