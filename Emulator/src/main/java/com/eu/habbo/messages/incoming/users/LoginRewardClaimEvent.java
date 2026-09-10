package com.eu.habbo.messages.incoming.users;

import com.eu.habbo.habbohotel.rewards.LoginRewardsManager;
import com.eu.habbo.habbohotel.rewards.LoginRewardsManager.Progress;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.users.LoginRewardDataComposer;

/** CUSTOM packet 10116: claims today's login reward. No payload. Always answers with the refreshed ladder. */
public class LoginRewardClaimEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        if (habbo == null) return;

        synchronized (habbo) {
            Progress progress = LoginRewardsManager.claim(habbo);
            if (progress == null) progress = LoginRewardsManager.getProgress(habbo.getHabboInfo().getId());
            this.client.sendResponse(new LoginRewardDataComposer(habbo, progress));
        }
    }
}
