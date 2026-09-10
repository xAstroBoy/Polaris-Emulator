package com.eu.habbo.habbohotel.rewards;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboBadge;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.inventory.AddHabboItemComposer;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import com.eu.habbo.messages.outgoing.users.AddUserBadgeComposer;
import com.eu.habbo.messages.outgoing.users.UserClubComposer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Locale;

/**
 * One place that hands out a reward described as (type, data, amount): login rewards, battle pass and any future
 * HSmile-style feature use it so credits, duckets, diamonds, badges, furni, HC days and effects are granted the
 * same way everywhere. Types match HSmile's reward vocabulary.
 */
public final class RewardGranter {
    private static final Logger LOGGER = LoggerFactory.getLogger(RewardGranter.class);

    public static final String CREDITS = "credits";
    public static final String DUCKETS = "duckets";
    public static final String DIAMONDS = "diamonds";
    public static final String BADGE = "badge";
    public static final String FURNI = "furni";
    public static final String HC_DAYS = "hc_days";
    public static final String EFFECT = "effect";

    private static final int DIAMONDS_POINTS_TYPE = 5;

    private RewardGranter() {
    }

    public static boolean isKnownType(String type) {
        if (type == null) return false;
        switch (type.toLowerCase(Locale.ROOT)) {
            case CREDITS, DUCKETS, DIAMONDS, BADGE, FURNI, HC_DAYS, EFFECT -> {
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    /** Grants the reward to an online user. Returns false when the definition could not be applied. */
    public static boolean grant(Habbo habbo, String type, String data, int amount) {
        if (habbo == null || type == null) return false;

        String kind = type.toLowerCase(Locale.ROOT);
        int quantity = Math.max(1, amount);
        String payload = data == null ? "" : data.trim();

        try {
            switch (kind) {
                case CREDITS -> habbo.giveCredits(quantity);
                case DUCKETS -> habbo.givePixels(quantity);
                case DIAMONDS -> habbo.givePoints(DIAMONDS_POINTS_TYPE, quantity);
                case BADGE -> {
                    if (payload.isEmpty()) return false;
                    if (habbo.getInventory().getBadgesComponent().hasBadge(payload)) return true;
                    HabboBadge badge = new HabboBadge(0, payload, 0, habbo);
                    badge.run();
                    habbo.getInventory().getBadgesComponent().addBadge(badge);
                    if (habbo.getClient() != null) habbo.getClient().sendResponse(new AddUserBadgeComposer(badge));
                }
                case FURNI -> {
                    int baseItemId = parseInt(payload);
                    Item baseItem = baseItemId > 0 ? Emulator.getGameEnvironment().getItemManager().getItem(baseItemId) : null;
                    if (baseItem == null) return false;
                    for (int i = 0; i < quantity; i++) {
                        HabboItem item = Emulator.getGameEnvironment().getItemManager().createItem(habbo.getHabboInfo().getId(), baseItem, 0, 0, "");
                        if (item == null) return false;
                        habbo.getInventory().getItemsComponent().addItem(item);
                        if (habbo.getClient() != null) habbo.getClient().sendResponse(new AddHabboItemComposer(item));
                    }
                    if (habbo.getClient() != null) habbo.getClient().sendResponse(new InventoryRefreshComposer());
                }
                case HC_DAYS -> grantHcDays(habbo, quantity);
                case EFFECT -> {
                    int effectId = parseInt(payload);
                    if (effectId <= 0) return false;
                    habbo.getInventory().getEffectsComponent().createEffect(effectId, quantity * 86400);
                }
                default -> {
                    return false;
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to grant reward {} {} x{} to {}", type, data, amount, habbo.getHabboInfo().getUsername(), e);
            return false;
        }

        return true;
    }

    private static void grantHcDays(Habbo habbo, int days) throws SQLException {
        int now = Emulator.getIntUnixTimestamp();
        int current = habbo.getHabboStats().getClubExpireTimestamp();
        int newExpire = (current > now ? current : now) + (days * 86400);

        habbo.getHabboStats().setClubExpireTimestamp(newExpire);

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("UPDATE users_settings SET club_expire_timestamp = ? WHERE user_id = ? LIMIT 1")) {
            statement.setInt(1, newExpire);
            statement.setInt(2, habbo.getHabboInfo().getId());
            statement.executeUpdate();
        }

        if (habbo.getClient() != null) {
            habbo.getClient().sendResponse(new UserClubComposer(habbo));
        }
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return 0;
        }
    }
}
