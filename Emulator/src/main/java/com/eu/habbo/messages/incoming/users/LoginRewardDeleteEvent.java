package com.eu.habbo.messages.incoming.users;

import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rewards.LoginRewardsManager;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.users.LoginRewardDataComposer;

/** CUSTOM packet 10118 (staff): int day. Empties one rung of the ladder. */
public class LoginRewardDeleteEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        if (habbo == null || !habbo.hasPermission(Permission.ACC_SUPPORTTOOL)) return;

        LoginRewardsManager.delete(this.packet.readInt());
        this.client.sendResponse(new LoginRewardDataComposer(habbo));
    }
}
