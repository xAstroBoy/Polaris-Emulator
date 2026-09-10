package com.eu.habbo.messages.incoming.users;

import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.users.LoginRewardDataComposer;

/** CUSTOM packet 10115: asks for the login reward ladder. No payload. */
public class LoginRewardRequestEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getHabbo() == null) return;

        this.client.sendResponse(new LoginRewardDataComposer(this.client.getHabbo()));
    }
}
