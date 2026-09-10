package com.eu.habbo.messages.incoming.users;

import com.eu.habbo.habbohotel.battlepass.BattlePassManager;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.users.BattlePassDataComposer;

/** CUSTOM packet 10131: int tier, bool premium — claims one reward of the ladder. Always answers with the refreshed pass. */
public class BattlePassClaimEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int tier = this.packet.readInt();
        boolean premium = this.packet.readBoolean();
        Habbo habbo = this.client.getHabbo();
        if (habbo == null) return;

        BattlePassManager.claim(habbo, tier, premium);
        this.client.sendResponse(new BattlePassDataComposer(habbo));
    }
}
