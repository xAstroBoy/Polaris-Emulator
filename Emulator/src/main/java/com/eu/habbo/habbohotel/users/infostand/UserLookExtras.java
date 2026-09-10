package com.eu.habbo.habbohotel.users.infostand;

import com.eu.habbo.Emulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Set;

/**
 * The HSmile-style look fields that did not exist before 2026-09-07: name-tag ornament, animated name effect,
 * library name icon, profile header background, name border colour and the posed-avatar string shown on the
 * banner. Stored in {@code user_look_extras}; the five legacy ids (banner, tile, overlay, wallpaper, border)
 * stay on the {@code users} row.
 */
public class UserLookExtras {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserLookExtras.class);

    /** posture:expression:handitem:gesture:sign:dance:direction — the same shape HSmile's client parses. */
    public static final int AVATAR_STRING_MAX = 64;
    private static final Set<String> POSTURES = Set.of("", "std", "sit", "lay", "mv");
    private static final Set<String> GESTURES = Set.of("", "agr", "sad", "sml", "srp");
    private static final Set<String> EXPRESSIONS = Set.of("", "wave", "blow", "laugh", "respect", "idle", "cry");

    private final int userId;
    private int ornamentId;
    private int nameEffectId;
    private int nameIconId;
    private int profileBgId;
    private String nameBorder = "";
    private String avatarString = "";
    private boolean hideOrnaments;
    private int mentionPrivacy; // 0 everyone, 1 friends only, 2 nobody
    private int callPrivacy; // 0 friends, 1 nobody

    private UserLookExtras(int userId) {
        this.userId = userId;
    }

    public static UserLookExtras load(int userId) {
        UserLookExtras extras = new UserLookExtras(userId);

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT ornament_id, name_effect_id, name_icon_id, profile_bg_id, name_border, avatar_string, hide_ornaments, mention_privacy, call_privacy FROM user_look_extras WHERE user_id = ? LIMIT 1")) {
            statement.setInt(1, userId);

            try (ResultSet set = statement.executeQuery()) {
                if (set.next()) {
                    extras.ornamentId = set.getInt("ornament_id");
                    extras.nameEffectId = set.getInt("name_effect_id");
                    extras.nameIconId = set.getInt("name_icon_id");
                    extras.profileBgId = set.getInt("profile_bg_id");
                    extras.nameBorder = sanitizeHex(set.getString("name_border"));
                    extras.avatarString = sanitizeAvatarString(set.getString("avatar_string"));
                    extras.hideOrnaments = set.getInt("hide_ornaments") == 1;
                    extras.mentionPrivacy = clamp(set.getInt("mention_privacy"), 0, 2);
                    extras.callPrivacy = clamp(set.getInt("call_privacy"), 0, 1);
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception while loading user look extras", e);
        }

        return extras;
    }

    public static UserLookExtras empty(int userId) {
        return new UserLookExtras(userId);
    }

    public void save() {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO user_look_extras (user_id, ornament_id, name_effect_id, name_icon_id, profile_bg_id, name_border, avatar_string, hide_ornaments, mention_privacy, call_privacy) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                             + "ON DUPLICATE KEY UPDATE ornament_id = VALUES(ornament_id), name_effect_id = VALUES(name_effect_id), name_icon_id = VALUES(name_icon_id), "
                             + "profile_bg_id = VALUES(profile_bg_id), name_border = VALUES(name_border), avatar_string = VALUES(avatar_string), hide_ornaments = VALUES(hide_ornaments), mention_privacy = VALUES(mention_privacy), call_privacy = VALUES(call_privacy)")) {
            statement.setInt(1, this.userId);
            statement.setInt(2, this.ornamentId);
            statement.setInt(3, this.nameEffectId);
            statement.setInt(4, this.nameIconId);
            statement.setInt(5, this.profileBgId);
            statement.setString(6, this.nameBorder);
            statement.setString(7, this.avatarString);
            statement.setInt(8, this.hideOrnaments ? 1 : 0);
            statement.setInt(9, this.mentionPrivacy);
            statement.setInt(10, this.callPrivacy);
            statement.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception while saving user look extras", e);
        }
    }

    /** Accepts "#RRGGBB" / "RRGGBB"; anything else clears the value. */
    public static String sanitizeHex(String value) {
        if (value == null) return "";
        String hex = value.trim();
        if (hex.startsWith("#")) hex = hex.substring(1);
        if (!hex.matches("(?i)[0-9a-f]{6}")) return "";
        return "#" + hex.toUpperCase(Locale.ROOT);
    }

    /**
     * Keeps only what the client can render: known posture / expression / gesture names, hand item 0..2000,
     * sign 0..17, dance 0..4, direction 0..7. Everything else collapses to the default so a crafted string can
     * never reach other clients.
     */
    public static String sanitizeAvatarString(String value) {
        if (value == null || value.isBlank()) return "";

        String[] parts = value.trim().split(":", -1);
        String posture = parts.length > 0 ? parts[0].trim().toLowerCase(Locale.ROOT) : "";
        String expression = parts.length > 1 ? parts[1].trim().toLowerCase(Locale.ROOT) : "";
        int handItem = parts.length > 2 ? parseInt(parts[2], -1) : -1;
        String gesture = parts.length > 3 ? parts[3].trim().toLowerCase(Locale.ROOT) : "";
        int sign = parts.length > 4 ? parseInt(parts[4], -1) : -1;
        int dance = parts.length > 5 ? parseInt(parts[5], 0) : 0;
        int direction = parts.length > 6 ? parseInt(parts[6], 2) : 2;

        if (!POSTURES.contains(posture)) posture = "";
        if (!EXPRESSIONS.contains(expression)) expression = "";
        if (!GESTURES.contains(gesture)) gesture = "";
        if (handItem < -1 || handItem > 2000) handItem = -1;
        if (sign < -1 || sign > 17) sign = -1;
        if (dance < 0 || dance > 4) dance = 0;
        if (direction < 0 || direction > 7) direction = 2;

        boolean isDefault = posture.isEmpty() && expression.isEmpty() && handItem == -1 && gesture.isEmpty() && sign == -1 && dance == 0 && direction == 2;
        if (isDefault) return "";

        String result = posture + ":" + expression + ":" + handItem + ":" + gesture + ":" + sign + ":" + dance + ":" + direction;
        return result.length() > AVATAR_STRING_MAX ? "" : result;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public int getUserId() { return this.userId; }
    public int getOrnamentId() { return this.ornamentId; }
    public void setOrnamentId(int ornamentId) { this.ornamentId = Math.max(0, ornamentId); }
    public int getNameEffectId() { return this.nameEffectId; }
    public void setNameEffectId(int nameEffectId) { this.nameEffectId = Math.max(0, nameEffectId); }
    public int getNameIconId() { return this.nameIconId; }
    public void setNameIconId(int nameIconId) { this.nameIconId = Math.max(0, nameIconId); }
    public int getProfileBgId() { return this.profileBgId; }
    public void setProfileBgId(int profileBgId) { this.profileBgId = Math.max(0, profileBgId); }
    public String getNameBorder() { return this.nameBorder; }
    public void setNameBorder(String nameBorder) { this.nameBorder = sanitizeHex(nameBorder); }
    public String getAvatarString() { return this.avatarString; }
    public void setAvatarString(String avatarString) { this.avatarString = sanitizeAvatarString(avatarString); }
    public boolean isHideOrnaments() { return this.hideOrnaments; }
    public void setHideOrnaments(boolean hideOrnaments) { this.hideOrnaments = hideOrnaments; }
    public int getMentionPrivacy() { return this.mentionPrivacy; }
    public void setMentionPrivacy(int mentionPrivacy) { this.mentionPrivacy = clamp(mentionPrivacy, 0, 2); }
    public int getCallPrivacy() { return this.callPrivacy; }
    public void setCallPrivacy(int callPrivacy) { this.callPrivacy = clamp(callPrivacy, 0, 1); }
}
