package com.eu.habbo.messages.incoming.users;

import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rewards.LoginRewardsManager;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.users.LoginRewardDataComposer;

/** CUSTOM packet 10117 (staff): int day, string type, string data, int amount. Rewrites one rung of the ladder. */
public class LoginRewardSaveEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        if (habbo == null || !habbo.hasPermission(Permission.ACC_SUPPORTTOOL)) return;

        int day = this.packet.readInt();
        String type = this.packet.readString();
        String data = this.packet.readString();
        int amount = this.packet.readInt();

        if (type == null || type.length() > 16) return;
        if (data != null && data.length() > 64) return;

        LoginRewardsManager.save(day, type.trim().toLowerCase(), data == null ? "" : data.trim(), amount);
        this.client.sendResponse(new LoginRewardDataComposer(habbo));
    }
}
