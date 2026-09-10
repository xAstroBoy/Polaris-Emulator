package com.eu.habbo.messages.incoming.friends;

import com.eu.habbo.habbohotel.calls.CallManager;
import com.eu.habbo.messages.incoming.MessageHandler;

/** CUSTOM packet 10137: int callId, bool accept — the callee answers. */
public class CallAnswerEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int callId = this.packet.readInt();
        boolean accept = this.packet.readBoolean();

        if (this.client.getHabbo() == null) return;

        CallManager.answer(this.client.getHabbo(), callId, accept);
    }
}
