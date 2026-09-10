package com.eu.habbo.messages.incoming.users;

import com.eu.habbo.habbohotel.battlepass.BattlePassManager;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.users.BattlePassDataComposer;

/** CUSTOM packet 10132: buys the premium track of the active season. No payload. */
public class BattlePassBuyPremiumEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        if (habbo == null) return;

        synchronized (habbo) {
            BattlePassManager.buyPremium(habbo);
        }

        this.client.sendResponse(new BattlePassDataComposer(habbo));
    }
}
