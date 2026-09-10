package com.eu.habbo.messages.incoming.friends;

import com.eu.habbo.habbohotel.calls.CallManager;
import com.eu.habbo.messages.incoming.MessageHandler;

/** CUSTOM packet 10138: int callId — hang up (or cancel while ringing). */
public class CallEndEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int callId = this.packet.readInt();

        if (this.client.getHabbo() == null) return;

        CallManager.end(this.client.getHabbo(), callId);
    }
}
