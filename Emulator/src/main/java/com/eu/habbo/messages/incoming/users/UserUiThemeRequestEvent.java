package com.eu.habbo.messages.incoming.users;

import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.users.UserUiThemeComposer;

/** CUSTOM packet 10127: asks for the saved client theme. No payload. */
public class UserUiThemeRequestEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getHabbo() == null) return;

        this.client.sendResponse(new UserUiThemeComposer(UserUiThemeComposer.load(this.client.getHabbo().getHabboInfo().getId())));
    }
}
