package com.eu.habbo.messages.incoming.users;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboStats;
import com.eu.habbo.habbohotel.users.infostand.InfostandBackgroundManager;
import com.eu.habbo.habbohotel.users.infostand.InfostandBackgroundManager.Category;
import com.eu.habbo.habbohotel.users.infostand.UserLookExtras;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserDataComposer;
import com.eu.habbo.messages.outgoing.users.UserLookCatalogComposer;

/**
 * CUSTOM packet 10105: one look field changes. Payload: int category, string value.
 *
 * Categories 1..9 carry an item id ("0" clears); 10 = name colour and 11 = name border colour carry
 * "#RRGGBB" ("" clears); 12 = the posed-avatar string. Ids are checked against the library and the user's
 * rank / HC / ambassador status. The room (or the user alone, outside rooms) gets the refreshed user data,
 * and the user gets its selection back so the options windows stay in sync.
 */
public class SetUserLookEvent extends MessageHandler {
    public static final int CATEGORY_NAME_COLOR = 10;
    public static final int CATEGORY_NAME_BORDER = 11;
    public static final int CATEGORY_AVATAR_STRING = 12;
    public static final int CATEGORY_HIDE_ORNAMENTS = 13;
    public static final int CATEGORY_MENTION_PRIVACY = 14;
    public static final int CATEGORY_CALL_PRIVACY = 15;

    private static final String COOLDOWN_KEY = "user_look_cooldown";
    private static final long COOLDOWN_MS = 300L;

    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        if (habbo == null) return;

        HabboInfo info = habbo.getHabboInfo();
        if (info == null) return;

        HabboStats stats = habbo.getHabboStats();
        if (stats != null) {
            long now = System.currentTimeMillis();
            Object last = stats.cache.get(COOLDOWN_KEY);
            if (last instanceof Long && (now - (Long) last) < COOLDOWN_MS) return;
            stats.cache.put(COOLDOWN_KEY, now);
        }

        int categoryCode = this.packet.readInt();
        String value = this.packet.readString();
        if (value == null) value = "";
        if (value.length() > 128) return;

        boolean changed;
        boolean broadcast = true;

        switch (categoryCode) {
            case CATEGORY_HIDE_ORNAMENTS -> {
                UserLookExtras extras = info.getLookExtras();
                extras.setHideOrnaments("1".equals(value.trim()) || "true".equalsIgnoreCase(value.trim()));
                extras.save();
                changed = true;
                broadcast = false;
            }
            case CATEGORY_MENTION_PRIVACY -> {
                UserLookExtras extras = info.getLookExtras();
                extras.setMentionPrivacy(parseIntOrZero(value));
                extras.save();
                changed = true;
                broadcast = false;
            }
            case CATEGORY_CALL_PRIVACY -> {
                UserLookExtras extras = info.getLookExtras();
                extras.setCallPrivacy(parseIntOrZero(value));
                extras.save();
                changed = true;
                broadcast = false;
            }
            case CATEGORY_NAME_COLOR -> {
                if (habbo.getInventory() == null || habbo.getInventory().getUserVisualSettingsComponent() == null) return;
                habbo.getInventory().getUserVisualSettingsComponent().setNameColor(value);
                changed = true;
            }
            case CATEGORY_NAME_BORDER -> {
                UserLookExtras extras = info.getLookExtras();
                extras.setNameBorder(value);
                extras.save();
                changed = true;
            }
            case CATEGORY_AVATAR_STRING -> {
                UserLookExtras extras = info.getLookExtras();
                extras.setAvatarString(value);
                extras.save();
                changed = true;
            }
            default -> {
                Category category = Category.fromCode(categoryCode);
                if (category == null) return;

                int id;
                try {
                    id = Integer.parseInt(value.trim());
                } catch (NumberFormatException e) {
                    return;
                }
                if (id < 0 || id > 999999) return;

                InfostandBackgroundManager manager = Emulator.getGameEnvironment().getInfostandBackgroundManager();
                if (manager != null && !manager.canUse(habbo, category, id)) return;

                changed = apply(info, category, id);
            }
        }

        if (!changed) return;

        if (broadcast && info.getCurrentRoom() != null) {
            info.getCurrentRoom().sendComposer(new RoomUserDataComposer(habbo).compose());
        } else if (broadcast) {
            this.client.sendResponse(new RoomUserDataComposer(habbo));
        }

        this.client.sendResponse(new UserLookCatalogComposer(habbo, false));
    }

    private static int parseIntOrZero(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static boolean apply(HabboInfo info, Category category, int id) {
        switch (category) {
            case BACKGROUND -> {
                if (info.getInfostandBg() == id) return false;
                info.setInfostandBg(id);
                info.run();
            }
            case STAND -> {
                if (info.getInfostandStand() == id) return false;
                info.setInfostandStand(id);
                info.run();
            }
            case OVERLAY -> {
                if (info.getInfostandOverlay() == id) return false;
                info.setInfostandOverlay(id);
                info.run();
            }
            case CARD -> {
                if (info.getInfostandCardBg() == id) return false;
                info.setInfostandCardBg(id);
                info.run();
            }
            case BORDER -> {
                if (info.getInfostandBorder() == id) return false;
                info.setInfostandBorder(id);
                info.run();
            }
            case ORNAMENT -> {
                UserLookExtras extras = info.getLookExtras();
                if (extras.getOrnamentId() == id) return false;
                extras.setOrnamentId(id);
                extras.save();
            }
            case NAME_EFFECT -> {
                UserLookExtras extras = info.getLookExtras();
                if (extras.getNameEffectId() == id) return false;
                extras.setNameEffectId(id);
                extras.save();
            }
            case NAME_ICON -> {
                UserLookExtras extras = info.getLookExtras();
                if (extras.getNameIconId() == id) return false;
                extras.setNameIconId(id);
                extras.save();
            }
            case PROFILE_BG -> {
                UserLookExtras extras = info.getLookExtras();
                if (extras.getProfileBgId() == id) return false;
                extras.setProfileBgId(id);
                extras.save();
            }
            default -> {
                return false;
            }
        }

        return true;
    }
}
