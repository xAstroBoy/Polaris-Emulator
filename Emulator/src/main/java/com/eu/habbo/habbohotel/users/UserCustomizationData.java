package com.eu.habbo.habbohotel.users;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.infostand.UserLookExtras;
import com.eu.habbo.habbohotel.users.inventory.UserVisualSettingsComponent;
import com.eu.habbo.messages.ServerMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class UserCustomizationData {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserCustomizationData.class);

    /** Library name icons travel as "hs_<id>" in the nickIcon slot; the client resolves the prefix to name_icons/<id>.png. */
    public static final String LIBRARY_NICK_ICON_PREFIX = "hs_";

    public final String nickIcon;
    public final String displayOrder;
    public final String prefixText;
    public final String prefixColor;
    public final String prefixIcon;
    public final String prefixEffect;
    public final String prefixFont;
    /** "#RRGGBB" username colour or "" for the default. Always serialized right after displayOrder. */
    public final String nameColor;
    /** HSmile look extras, always serialized as the trailing block: int ornament, int nameEffect, string nameBorder, string avatarString, int profileBg. */
    public final int ornamentId;
    public final int nameEffectId;
    public final String nameBorder;
    public final String avatarString;
    public final int profileBgId;

    private UserCustomizationData(String nickIcon, String displayOrder, String prefixText, String prefixColor, String prefixIcon, String prefixEffect, String prefixFont, String nameColor, UserLookExtras extras) {
        String resolvedNickIcon = nickIcon != null ? nickIcon : "";
        if (extras != null && extras.getNameIconId() > 0) {
            resolvedNickIcon = LIBRARY_NICK_ICON_PREFIX + extras.getNameIconId();
        }

        this.nickIcon = resolvedNickIcon;
        this.displayOrder = UserVisualSettingsComponent.sanitizeDisplayOrder(displayOrder);
        this.prefixText = prefixText != null ? prefixText : "";
        this.prefixColor = prefixColor != null ? prefixColor : "";
        this.prefixIcon = prefixIcon != null ? prefixIcon : "";
        this.prefixEffect = prefixEffect != null ? prefixEffect : "";
        this.prefixFont = prefixFont != null ? prefixFont : "";
        this.nameColor = UserVisualSettingsComponent.sanitizeNameColor(nameColor);
        this.ornamentId = extras != null ? extras.getOrnamentId() : 0;
        this.nameEffectId = extras != null ? extras.getNameEffectId() : 0;
        this.nameBorder = extras != null ? extras.getNameBorder() : "";
        this.avatarString = extras != null ? extras.getAvatarString() : "";
        this.profileBgId = extras != null ? extras.getProfileBgId() : 0;
    }

    public static UserCustomizationData fromHabbo(Habbo habbo) {
        if (habbo == null) {
            return empty();
        }

        String nickIcon = "";
        String displayOrder = UserVisualSettingsComponent.DEFAULT_DISPLAY_ORDER;
        String prefixText = "";
        String prefixColor = "";
        String prefixIcon = "";
        String prefixEffect = "";
        String prefixFont = "";
        String nameColor = "";

        if (habbo.getInventory() != null) {
            if (habbo.getInventory().getNickIconsComponent() != null) {
                UserNickIcon activeNickIcon = habbo.getInventory().getNickIconsComponent().getActiveNickIcon();

                if (activeNickIcon != null && activeNickIcon.getIconKey() != null) {
                    nickIcon = activeNickIcon.getIconKey();
                }
            }

            if (habbo.getInventory().getPrefixesComponent() != null) {
                UserPrefix activePrefix = habbo.getInventory().getPrefixesComponent().getActivePrefix();

                if (activePrefix != null) {
                    prefixText = activePrefix.getText();
                    prefixColor = activePrefix.getColor();
                    prefixIcon = activePrefix.getIcon();
                    prefixEffect = activePrefix.getEffect();
                    prefixFont = activePrefix.getFont();
                }
            }

            if (habbo.getInventory().getUserVisualSettingsComponent() != null) {
                displayOrder = habbo.getInventory().getUserVisualSettingsComponent().getDisplayOrder();
                nameColor = habbo.getInventory().getUserVisualSettingsComponent().getNameColor();
            }
        }

        UserLookExtras extras = habbo.getHabboInfo() != null ? habbo.getHabboInfo().getLookExtras() : null;

        return new UserCustomizationData(nickIcon, displayOrder, prefixText, prefixColor, prefixIcon, prefixEffect, prefixFont, nameColor, extras);
    }

    public static UserCustomizationData fromUserId(int userId) {
        String nickIcon = "";
        String prefixText = "";
        String prefixColor = "";
        String prefixIcon = "";
        String prefixEffect = "";
        String prefixFont = "";
        String displayOrder = UserVisualSettingsComponent.loadDisplayOrder(userId);
        String nameColor = UserVisualSettingsComponent.loadNameColor(userId);

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            try (PreparedStatement nickStatement = connection.prepareStatement(
                "SELECT icon_key FROM user_nick_icons WHERE user_id = ? AND active = 1 LIMIT 1")) {
                nickStatement.setInt(1, userId);

                try (ResultSet set = nickStatement.executeQuery()) {
                    if (set.next()) {
                        nickIcon = set.getString("icon_key");
                    }
                }
            }

            try (PreparedStatement prefixStatement = connection.prepareStatement(
                "SELECT text, color, icon, effect, font FROM user_prefixes WHERE user_id = ? AND active = 1 LIMIT 1")) {
                prefixStatement.setInt(1, userId);

                try (ResultSet set = prefixStatement.executeQuery()) {
                    if (set.next()) {
                        prefixText = set.getString("text");
                        prefixColor = set.getString("color");
                        prefixIcon = set.getString("icon");
                        prefixEffect = set.getString("effect");
                        prefixFont = set.getString("font");
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception while loading user customization data", e);
        }

        return new UserCustomizationData(nickIcon, displayOrder, prefixText, prefixColor, prefixIcon, prefixEffect, prefixFont, nameColor, UserLookExtras.load(userId));
    }

    public static UserCustomizationData empty() {
        return new UserCustomizationData("", UserVisualSettingsComponent.DEFAULT_DISPLAY_ORDER, "", "", "", "", "", "", null);
    }

    /** The trailing look block every user record carries (RoomUsers, RoomUserData, UserProfile). */
    public void appendLookExtras(ServerMessage message) {
        message.appendInt(this.ornamentId);
        message.appendInt(this.nameEffectId);
        message.appendString(this.nameBorder);
        message.appendString(this.avatarString);
        message.appendInt(this.profileBgId);
    }

    /** Bots and pets carry the same block, empty, so the client can read it unconditionally per record. */
    public static void appendEmptyLookExtras(ServerMessage message) {
        message.appendInt(0);
        message.appendInt(0);
        message.appendString("");
        message.appendString("");
        message.appendInt(0);
    }
}
