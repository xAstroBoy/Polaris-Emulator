package com.eu.habbo.messages.incoming.users;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.battlepass.BattlePassManager;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.users.BattlePassDataComposer;

/** CUSTOM packet 10133 (staff): string json — replaces the active season, its tiers and quests. Everyone online gets the new pass. */
public class BattlePassAdminSaveEvent extends MessageHandler {
    private static final int MAX_LENGTH = 200_000;

    @Override
    public void handle() throws Exception {
        String json = this.packet.readString();
        Habbo habbo = this.client.getHabbo();
        if (habbo == null || !habbo.hasPermission(Permission.ACC_SUPPORTTOOL)) return;
        if (json == null || json.length() > MAX_LENGTH) return;

        boolean saved = BattlePassManager.adminSave(json);

        if (saved) {
            for (Habbo online : Emulator.getGameEnvironment().getHabboManager().getOnlineHabbos().values()) {
                if (online != null && online.getClient() != null) online.getClient().sendResponse(new BattlePassDataComposer(online));
            }
        } else {
            this.client.sendResponse(new BattlePassDataComposer(habbo));
        }
    }
}
