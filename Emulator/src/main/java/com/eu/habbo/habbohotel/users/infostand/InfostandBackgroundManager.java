package com.eu.habbo.habbohotel.users.infostand;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The library of everything a user can put on their infostand / profile: the legacy CSS-class items (ids below
 * 1000, drawn by the client from bundled images) and the named items imported from HSmile (ids from 1001,
 * drawn from {@code <image.library.url>/<folder>/<name>.gif}). Rows live in {@code infostand_backgrounds};
 * the client receives the usable subset through {@link com.eu.habbo.messages.outgoing.users.UserLookCatalogComposer}.
 */
public class InfostandBackgroundManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(InfostandBackgroundManager.class);

    public enum Category {
        BACKGROUND("background", 1),
        STAND("stand", 2),
        OVERLAY("overlay", 3),
        CARD("card", 4),
        BORDER("border", 5),
        ORNAMENT("ornament", 6),
        NAME_EFFECT("name_effect", 7),
        NAME_ICON("name_icon", 8),
        PROFILE_BG("profile_bg", 9);

        public final String dbValue;
        /** Wire code shared with the client (UserLookCategory). */
        public final int code;

        Category(String dbValue, int code) {
            this.dbValue = dbValue;
            this.code = code;
        }

        public static Category fromDbValue(String value) {
            for (Category category : values()) {
                if (category.dbValue.equalsIgnoreCase(value)) return category;
            }
            return null;
        }

        public static Category fromCode(int code) {
            for (Category category : values()) {
                if (category.code == code) return category;
            }
            return null;
        }
    }

    private final Map<Category, Map<Integer, Entry>> entries = new EnumMap<>(Category.class);
    private boolean enforce = false;

    public InfostandBackgroundManager() {
        for (Category category : Category.values()) {
            this.entries.put(category, Collections.emptyMap());
        }

        this.reload();
    }

    public void reload() {
        Map<Category, Map<Integer, Entry>> next = new EnumMap<>(Category.class);
        for (Category category : Category.values()) {
            next.put(category, new HashMap<>());
        }

        int loaded = 0;
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT id, category, name, min_rank, is_hc_only, is_ambassador_only, width, height, margin_top, name_color FROM infostand_backgrounds");
             ResultSet set = statement.executeQuery()) {
            while (set.next()) {
                Category category = Category.fromDbValue(set.getString("category"));
                if (category == null) continue;

                int id = set.getInt("id");
                Entry entry = new Entry(
                        id,
                        set.getString("name"),
                        set.getInt("min_rank"),
                        set.getBoolean("is_hc_only"),
                        set.getBoolean("is_ambassador_only"),
                        set.getInt("width"),
                        set.getInt("height"),
                        set.getInt("margin_top"),
                        set.getString("name_color"));

                next.get(category).put(id, entry);
                loaded++;
            }
        } catch (SQLException e) {
            this.enforce = false;
            for (Category category : Category.values()) {
                this.entries.put(category, Collections.emptyMap());
            }
            LOGGER.error("InfostandBackgroundManager -> Failed to load infostand_backgrounds, server-side validation disabled.", e);
            return;
        }

        for (Category category : Category.values()) {
            this.entries.put(category, next.get(category));
        }

        this.enforce = loaded > 0;

        if (this.enforce) {
            LOGGER.info(summary(
                    this.entries.get(Category.BACKGROUND).size(),
                    this.entries.get(Category.STAND).size(),
                    this.entries.get(Category.OVERLAY).size(),
                    this.entries.get(Category.CARD).size(),
                    this.entries.get(Category.BORDER).size()));
            LOGGER.debug("Infostand Background Manager assets: {} bg, {} stands, {} overlays, {} cards, {} borders, {} ornaments, {} name effects, {} name icons, {} profile backgrounds",
                    this.entries.get(Category.BACKGROUND).size(),
                    this.entries.get(Category.STAND).size(),
                    this.entries.get(Category.OVERLAY).size(),
                    this.entries.get(Category.CARD).size(),
                    this.entries.get(Category.BORDER).size(),
                    this.entries.get(Category.ORNAMENT).size(),
                    this.entries.get(Category.NAME_EFFECT).size(),
                    this.entries.get(Category.NAME_ICON).size(),
                    this.entries.get(Category.PROFILE_BG).size());
        } else {
            LOGGER.info("InfostandBackgroundManager -> infostand_backgrounds is empty, server-side validation disabled (only range clamp will apply).");
        }
    }

    static String summary(int backgrounds, int stands, int overlays, int cards, int borders) {
        int total = backgrounds + stands + overlays + cards + borders;
        return String.format("Infostand Background Manager -> Loaded! (%d assets)", total);
    }

    public boolean canUse(Habbo habbo, Category category, int id) {
        if (id == 0) return true;
        if (!this.enforce) return true;
        if (habbo == null) return false;

        Map<Integer, Entry> categoryEntries = this.entries.get(category);
        if (categoryEntries == null) return false;

        Entry entry = categoryEntries.get(id);
        if (entry == null) return false;

        return this.allows(habbo, entry);
    }

    public boolean allows(Habbo habbo, Entry entry) {
        if (habbo == null || entry == null) return false;
        HabboInfo info = habbo.getHabboInfo();
        int rankId = (info != null && info.getRank() != null) ? info.getRank().getId() : 0;
        HabboStats stats = habbo.getHabboStats();
        boolean hasClub = stats != null && stats.hasActiveClub();

        if (entry.isHcOnly && !hasClub) return false;
        if (entry.isAmbassadorOnly && !habbo.hasPermission(Permission.ACC_AMBASSADOR)) return false;
        if (rankId < entry.minRank) return false;

        return true;
    }

    /** Every entry of a category the user may pick, sorted by id (legacy items first, then the named libraries). */
    public List<Entry> usableEntries(Habbo habbo, Category category) {
        Map<Integer, Entry> categoryEntries = this.entries.get(category);
        if (categoryEntries == null || categoryEntries.isEmpty() || habbo == null) return Collections.emptyList();

        List<Entry> result = new ArrayList<>();
        for (Entry entry : categoryEntries.values()) {
            if (entry.id == 0) continue;
            if (this.allows(habbo, entry)) result.add(entry);
        }
        result.sort(Comparator.comparingInt(entry -> entry.id));
        return result;
    }

    /** Every entry of a category, usable or not, sorted by id; the client needs the names of items other users wear. */
    public List<Entry> allEntries(Category category) {
        Map<Integer, Entry> categoryEntries = this.entries.get(category);
        if (categoryEntries == null || categoryEntries.isEmpty()) return Collections.emptyList();

        List<Entry> result = new ArrayList<>();
        for (Entry entry : categoryEntries.values()) {
            if (entry.id == 0) continue;
            result.add(entry);
        }
        result.sort(Comparator.comparingInt(entry -> entry.id));
        return result;
    }

    public Entry getEntry(Category category, int id) {
        Map<Integer, Entry> categoryEntries = this.entries.get(category);
        return categoryEntries == null ? null : categoryEntries.get(id);
    }

    public boolean isEnforcing() {
        return this.enforce;
    }

    public static final class Entry {
        public final int id;
        /** Asset name (file stem) for the named libraries; empty for the legacy CSS-class items. */
        public final String name;
        public final int minRank;
        public final boolean isHcOnly;
        public final boolean isAmbassadorOnly;
        public final int width;
        public final int height;
        public final int marginTop;
        public final String nameColor;

        public Entry(int minRank, boolean isHcOnly, boolean isAmbassadorOnly) {
            this(0, "", minRank, isHcOnly, isAmbassadorOnly, 0, 0, 0, "");
        }

        public Entry(int id, String name, int minRank, boolean isHcOnly, boolean isAmbassadorOnly, int width, int height, int marginTop, String nameColor) {
            this.id = id;
            this.name = name == null ? "" : name;
            this.minRank = minRank;
            this.isHcOnly = isHcOnly;
            this.isAmbassadorOnly = isAmbassadorOnly;
            this.width = width;
            this.height = height;
            this.marginTop = marginTop;
            this.nameColor = nameColor == null ? "" : nameColor;
        }
    }
}
