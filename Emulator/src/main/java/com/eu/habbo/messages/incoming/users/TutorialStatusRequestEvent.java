package com.eu.habbo.messages.incoming.users;

import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.users.TutorialStatusComposer;

/** CUSTOM packet 10119: asks whether the tutorial is done. No payload. */
public class TutorialStatusRequestEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        if (habbo == null || habbo.getHabboStats() == null) return;

        this.client.sendResponse(new TutorialStatusComposer(!habbo.getHabboStats().nux));
    }
}
