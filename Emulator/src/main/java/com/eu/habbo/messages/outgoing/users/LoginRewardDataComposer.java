package com.eu.habbo.messages.outgoing.users;

import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rewards.LoginRewardsManager;
import com.eu.habbo.habbohotel.rewards.LoginRewardsManager.Progress;
import com.eu.habbo.habbohotel.rewards.LoginRewardsManager.Reward;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/**
 * CUSTOM packet 10114: the login reward ladder and where the user stands on it.
 * bool claimable, int streak, int record, int currentDay, int 7, per day: int day, string type, string data,
 * int amount, bool claimed; bool canEdit.
 */
public class LoginRewardDataComposer extends MessageComposer {
    private final Habbo habbo;
    private final Progress progress;

    public LoginRewardDataComposer(Habbo habbo) {
        this(habbo, LoginRewardsManager.getProgress(habbo.getHabboInfo().getId()));
    }

    public LoginRewardDataComposer(Habbo habbo, Progress progress) {
        this.habbo = habbo;
        this.progress = progress;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.LoginRewardDataComposer);
        this.response.appendBoolean(this.progress.claimable());
        this.response.appendInt(this.progress.streak());
        this.response.appendInt(this.progress.record());
        this.response.appendInt(this.progress.currentDay());

        this.response.appendInt(LoginRewardsManager.DAYS);
        for (int day = 1; day <= LoginRewardsManager.DAYS; day++) {
            Reward reward = LoginRewardsManager.getReward(day);
            this.response.appendInt(day);
            this.response.appendString(reward == null ? "" : reward.type());
            this.response.appendString(reward == null ? "" : reward.data());
            this.response.appendInt(reward == null ? 0 : reward.amount());
            boolean claimed = day < this.progress.currentDay() || (day == this.progress.currentDay() && !this.progress.claimable());
            this.response.appendBoolean(claimed);
        }

        this.response.appendBoolean(this.habbo.hasPermission(Permission.ACC_SUPPORTTOOL));
        return this.response;
    }
}
