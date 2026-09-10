package com.eu.habbo.messages.incoming.users;

import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.users.BattlePassDataComposer;

/** CUSTOM packet 10129: asks for the battle pass. No payload. */
public class BattlePassRequestEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getHabbo() == null) return;

        this.client.sendResponse(new BattlePassDataComposer(this.client.getHabbo()));
    }
}
