package com.eu.habbo.messages.incoming.users;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.users.TutorialStatusComposer;

/** CUSTOM packet 10121: the user finished (or skipped) the tutorial. No payload. Clears users_settings.nux. */
public class TutorialCompleteEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        if (habbo == null || habbo.getHabboStats() == null) return;

        if (habbo.getHabboStats().nux) {
            habbo.getHabboStats().nux = false;
            Emulator.getThreading().run(habbo.getHabboStats());
        }

        this.client.sendResponse(new TutorialStatusComposer(true));
    }
}
