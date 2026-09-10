package com.eu.habbo.messages.incoming.users;

import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.users.UserLookCatalogComposer;

/** CUSTOM packet 10104: the client (re)asks for its look library and current selection. No payload. */
public class UserLookRequestEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getHabbo() == null) return;

        this.client.sendResponse(new UserLookCatalogComposer(this.client.getHabbo(), true));
    }
}
