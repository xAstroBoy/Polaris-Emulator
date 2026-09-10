package com.eu.habbo.messages.outgoing.users;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.battlepass.BattlePassManager;
import com.eu.habbo.habbohotel.battlepass.BattlePassManager.Quest;
import com.eu.habbo.habbohotel.battlepass.BattlePassManager.Season;
import com.eu.habbo.habbohotel.battlepass.BattlePassManager.Tier;
import com.eu.habbo.habbohotel.battlepass.BattlePassManager.UserState;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

import java.util.List;

/**
 * CUSTOM packet 10130: the whole battle pass for one player.
 * bool hasSeason; [int seasonId, string name, int startsAt, int endsAt, int premiumPrice, int pointsType, int xpPerTier,
 * int tierCount, int xp, bool premium, int tiersReached, int balance,
 * int tiers × {int tier, string freeType, string freeData, int freeAmount, string premType, string premData, int premAmount, bool freeClaimed, bool premClaimed},
 * int quests × {int id, string period, string metric, int target, int xp, string title, int progress, bool completed}]; bool canEdit.
 */
public class BattlePassDataComposer extends MessageComposer {
    private final Habbo habbo;

    public BattlePassDataComposer(Habbo habbo) {
        this.habbo = habbo;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.BattlePassDataComposer);

        Season season = BattlePassManager.getSeason();
        boolean hasSeason = season != null && season.isRunning(Emulator.getIntUnixTimestamp());
        this.response.appendBoolean(hasSeason);

        if (hasSeason) {
            UserState state = BattlePassManager.getUserState(this.habbo.getHabboInfo().getId());
            List<Tier> tiers = BattlePassManager.getTiers();
            List<Quest> quests = BattlePassManager.getQuests();
            int balance = season.pointsType() == 0 ? this.habbo.getHabboInfo().getCredits() : this.habbo.getHabboInfo().getCurrencyAmount(season.pointsType());

            this.response.appendInt(season.id());
            this.response.appendString(season.name());
            this.response.appendInt(season.startsAt());
            this.response.appendInt(season.endsAt());
            this.response.appendInt(season.premiumPrice());
            this.response.appendInt(season.pointsType());
            this.response.appendInt(season.xpPerTier());
            this.response.appendInt(season.tierCount());
            this.response.appendInt(state.xp);
            this.response.appendBoolean(state.premium);
            this.response.appendInt(BattlePassManager.tierReached(state));
            this.response.appendInt(balance);

            this.response.appendInt(tiers.size());
            for (Tier tier : tiers) {
                this.response.appendInt(tier.tier());
                this.response.appendString(tier.freeType());
                this.response.appendString(tier.freeData());
                this.response.appendInt(tier.freeAmount());
                this.response.appendString(tier.premiumType());
                this.response.appendString(tier.premiumData());
                this.response.appendInt(tier.premiumAmount());
                this.response.appendBoolean(state.claimedFree.contains(tier.tier()));
                this.response.appendBoolean(state.claimedPremium.contains(tier.tier()));
            }

            this.response.appendInt(quests.size());
            for (Quest quest : quests) {
                String key = quest.id() + ":" + BattlePassManager.periodKey(quest);
                this.response.appendInt(quest.id());
                this.response.appendString(quest.period());
                this.response.appendString(quest.metric());
                this.response.appendInt(quest.target());
                this.response.appendInt(quest.xp());
                this.response.appendString(quest.title());
                this.response.appendInt(Math.min(quest.target(), state.progress.getOrDefault(key, 0)));
                this.response.appendBoolean(state.completed.contains(key));
            }
        }

        this.response.appendBoolean(this.habbo.hasPermission(Permission.ACC_SUPPORTTOOL));
        return this.response;
    }
}
