package com.eu.habbo.habbohotel.battlepass;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rewards.RewardGranter;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.users.BattlePassDataComposer;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HSmile-style battle pass: one active season with a ladder of tiers (free + premium reward per tier) and a set of
 * daily / weekly / season quests that award XP. Game code reports metrics with {@link #track(Habbo, String, int)};
 * everything else (progress, XP, claims, the premium purchase, the staff editor) lives here.
 *
 * Metrics: chat, rooms, online (minutes), respect, furni, friends, login (once per day).
 */
public final class BattlePassManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(BattlePassManager.class);

    public static final Set<String> METRICS = Set.of("chat", "rooms", "online", "respect", "furni", "friends", "login");
    public static final Set<String> PERIODS = Set.of("daily", "weekly", "season");
    private static final long ONLINE_TICK_MS = 60_000L;

    public record Season(int id, String name, int startsAt, int endsAt, int premiumPrice, int pointsType, int xpPerTier, int tierCount) {
        public boolean isRunning(int now) {
            return now >= this.startsAt && now <= this.endsAt;
        }
    }

    public record Tier(int tier, String freeType, String freeData, int freeAmount, String premiumType, String premiumData, int premiumAmount) {}

    public record Quest(int id, String period, String metric, int target, int xp, String title) {}

    /** Per-user cached state for the active season. */
    public static final class UserState {
        public int xp;
        public boolean premium;
        public final Set<Integer> claimedFree = new HashSet<>();
        public final Set<Integer> claimedPremium = new HashSet<>();
        /** questId + ":" + periodKey -> progress */
        public final Map<String, Integer> progress = new ConcurrentHashMap<>();
        public final Set<String> completed = ConcurrentHashMap.newKeySet();
        public String lastLoginDay = "";
    }

    private static volatile Season season;
    private static final Map<Integer, Tier> TIERS = new ConcurrentHashMap<>();
    private static final Map<Integer, Quest> QUESTS = new ConcurrentHashMap<>();
    private static final Map<Integer, UserState> USERS = new ConcurrentHashMap<>();
    private static volatile boolean loaded = false;
    private static volatile boolean tickerStarted = false;

    private BattlePassManager() {}

    // ---------------------------------------------------------------- configuration

    public static synchronized void reload() {
        Season next = null;
        Map<Integer, Tier> tiers = new LinkedHashMap<>();
        Map<Integer, Quest> quests = new LinkedHashMap<>();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT id, name, starts_at, ends_at, premium_price, points_type, xp_per_tier, tier_count FROM battle_pass_seasons WHERE active = 1 ORDER BY id DESC LIMIT 1");
                 ResultSet set = statement.executeQuery()) {
                if (set.next()) {
                    next = new Season(set.getInt("id"), set.getString("name"), set.getInt("starts_at"), set.getInt("ends_at"), set.getInt("premium_price"),
                            set.getInt("points_type"), Math.max(1, set.getInt("xp_per_tier")), Math.max(1, set.getInt("tier_count")));
                }
            }

            if (next != null) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT tier, free_type, free_data, free_amount, premium_type, premium_data, premium_amount FROM battle_pass_tiers WHERE season_id = ? ORDER BY tier ASC")) {
                    statement.setInt(1, next.id());
                    try (ResultSet set = statement.executeQuery()) {
                        while (set.next()) {
                            tiers.put(set.getInt("tier"), new Tier(set.getInt("tier"), nz(set.getString("free_type")), nz(set.getString("free_data")), set.getInt("free_amount"),
                                    nz(set.getString("premium_type")), nz(set.getString("premium_data")), set.getInt("premium_amount")));
                        }
                    }
                }

                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT id, period, metric, target, xp, title FROM battle_pass_quests WHERE season_id = ? ORDER BY FIELD(period, 'daily', 'weekly', 'season'), id ASC")) {
                    statement.setInt(1, next.id());
                    try (ResultSet set = statement.executeQuery()) {
                        while (set.next()) {
                            quests.put(set.getInt("id"), new Quest(set.getInt("id"), set.getString("period"), set.getString("metric"), Math.max(1, set.getInt("target")),
                                    Math.max(0, set.getInt("xp")), nz(set.getString("title"))));
                        }
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to load the battle pass", e);
        }

        season = next;
        TIERS.clear();
        TIERS.putAll(tiers);
        QUESTS.clear();
        QUESTS.putAll(quests);
        USERS.clear();
        loaded = true;

        startTicker();
    }

    private static void ensureLoaded() {
        if (!loaded) reload();
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }

    public static Season getSeason() {
        ensureLoaded();
        return season;
    }

    public static List<Tier> getTiers() {
        ensureLoaded();
        return new ArrayList<>(TIERS.values());
    }

    public static List<Quest> getQuests() {
        ensureLoaded();
        return new ArrayList<>(QUESTS.values());
    }

    private static synchronized void startTicker() {
        if (tickerStarted) return;
        tickerStarted = true;
        Emulator.getThreading().run(BattlePassManager::onlineTick, ONLINE_TICK_MS);
    }

    /** Every minute: one "online" minute for every player currently inside a room. */
    private static void onlineTick() {
        try {
            if (season != null && season.isRunning(Emulator.getIntUnixTimestamp())) {
                for (Habbo habbo : Emulator.getGameEnvironment().getHabboManager().getOnlineHabbos().values()) {
                    if (habbo == null || habbo.getHabboInfo() == null || habbo.getHabboInfo().getCurrentRoom() == null) continue;
                    track(habbo, "online", 1);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Battle pass online tick failed", e);
        } finally {
            if (!Emulator.isShuttingDown) Emulator.getThreading().run(BattlePassManager::onlineTick, ONLINE_TICK_MS);
        }
    }

    // ---------------------------------------------------------------- periods

    private static String today() {
        return LocalDate.now(ZoneId.systemDefault()).toString();
    }

    public static String periodKey(Quest quest) {
        LocalDate now = LocalDate.now(ZoneId.systemDefault());

        return switch (quest.period()) {
            case "daily" -> now.toString();
            case "weekly" -> now.get(WeekFields.ISO.weekBasedYear()) + "-W" + String.format(Locale.ROOT, "%02d", now.get(WeekFields.ISO.weekOfWeekBasedYear()));
            default -> "s" + (season == null ? 0 : season.id());
        };
    }

    // ---------------------------------------------------------------- user state

    public static UserState getUserState(int userId) {
        ensureLoaded();

        UserState cached = USERS.get(userId);
        if (cached != null) return cached;

        UserState state = new UserState();

        if (season != null) {
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT xp, premium, claimed_free, claimed_premium FROM user_battle_pass WHERE user_id = ? AND season_id = ? LIMIT 1")) {
                    statement.setInt(1, userId);
                    statement.setInt(2, season.id());
                    try (ResultSet set = statement.executeQuery()) {
                        if (set.next()) {
                            state.xp = set.getInt("xp");
                            state.premium = set.getInt("premium") == 1;
                            parseTiers(set.getString("claimed_free"), state.claimedFree);
                            parseTiers(set.getString("claimed_premium"), state.claimedPremium);
                        }
                    }
                }

                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT quest_id, period_key, progress, completed_at FROM user_battle_pass_quests WHERE user_id = ?")) {
                    statement.setInt(1, userId);
                    try (ResultSet set = statement.executeQuery()) {
                        while (set.next()) {
                            String key = set.getInt("quest_id") + ":" + set.getString("period_key");
                            state.progress.put(key, set.getInt("progress"));
                            if (set.getInt("completed_at") > 0) state.completed.add(key);
                        }
                    }
                }
            } catch (SQLException e) {
                LOGGER.error("Failed to load battle pass state for {}", userId, e);
            }
        }

        USERS.put(userId, state);
        return state;
    }

    public static void forget(int userId) {
        USERS.remove(userId);
    }

    private static void parseTiers(String csv, Set<Integer> into) {
        if (csv == null || csv.isBlank()) return;
        for (String part : csv.split(",")) {
            try {
                into.add(Integer.parseInt(part.trim()));
            } catch (NumberFormatException ignored) {
                // skip
            }
        }
    }

    private static String joinTiers(Set<Integer> tiers) {
        List<Integer> sorted = new ArrayList<>(tiers);
        Collections.sort(sorted);
        StringBuilder builder = new StringBuilder();
        for (Integer tier : sorted) {
            if (builder.length() > 0) builder.append(',');
            builder.append(tier);
        }
        return builder.toString();
    }

    private static void saveUser(int userId, UserState state) {
        if (season == null) return;

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO user_battle_pass (user_id, season_id, xp, premium, claimed_free, claimed_premium) VALUES (?, ?, ?, ?, ?, ?) "
                             + "ON DUPLICATE KEY UPDATE xp = VALUES(xp), premium = VALUES(premium), claimed_free = VALUES(claimed_free), claimed_premium = VALUES(claimed_premium)")) {
            statement.setInt(1, userId);
            statement.setInt(2, season.id());
            statement.setInt(3, state.xp);
            statement.setInt(4, state.premium ? 1 : 0);
            statement.setString(5, joinTiers(state.claimedFree));
            statement.setString(6, joinTiers(state.claimedPremium));
            statement.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Failed to save battle pass state for {}", userId, e);
        }
    }

    private static void saveQuestProgress(int userId, Quest quest, String periodKey, int progress, boolean completed) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO user_battle_pass_quests (user_id, quest_id, period_key, progress, completed_at) VALUES (?, ?, ?, ?, ?) "
                             + "ON DUPLICATE KEY UPDATE progress = VALUES(progress), completed_at = IF(VALUES(completed_at) > 0, VALUES(completed_at), completed_at)")) {
            statement.setInt(1, userId);
            statement.setInt(2, quest.id());
            statement.setString(3, periodKey);
            statement.setInt(4, progress);
            statement.setInt(5, completed ? Emulator.getIntUnixTimestamp() : 0);
            statement.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Failed to save battle pass quest progress for {}", userId, e);
        }
    }

    // ---------------------------------------------------------------- tracking

    public static int tierReached(UserState state) {
        if (season == null) return 0;
        return Math.min(season.tierCount(), state.xp / season.xpPerTier());
    }

    /**
     * Reports a metric for a player. Every quest of the running season on that metric advances; on reaching its
     * target the quest completes once per period and its XP lands on the pass. The client gets a refreshed pass
     * when a quest completes or a tier is reached.
     */
    public static void track(Habbo habbo, String metric, int amount) {
        if (habbo == null || habbo.getHabboInfo() == null || amount <= 0) return;

        ensureLoaded();
        Season current = season;
        if (current == null || !current.isRunning(Emulator.getIntUnixTimestamp())) return;

        int userId = habbo.getHabboInfo().getId();
        UserState state = getUserState(userId);

        if ("login".equals(metric)) {
            String day = today();
            if (day.equals(state.lastLoginDay)) return;
            state.lastLoginDay = day;
        }

        boolean changed = false;
        boolean tierUp = false;
        int tierBefore = tierReached(state);

        for (Quest quest : QUESTS.values()) {
            if (!quest.metric().equals(metric)) continue;

            String periodKey = periodKey(quest);
            String key = quest.id() + ":" + periodKey;
            if (state.completed.contains(key)) continue;

            int progress = Math.min(quest.target(), state.progress.getOrDefault(key, 0) + amount);
            state.progress.put(key, progress);

            boolean completed = progress >= quest.target();
            if (completed) {
                state.completed.add(key);
                state.xp += quest.xp();
                changed = true;
            }

            saveQuestProgress(userId, quest, periodKey, progress, completed);
        }

        if (changed) {
            saveUser(userId, state);
            tierUp = tierReached(state) > tierBefore;

            if (habbo.getClient() != null) {
                habbo.getClient().sendResponse(new BattlePassDataComposer(habbo));
                if (tierUp) {
                    habbo.getClient().sendResponse(new com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer(
                            "battlepass.tier", java.util.Collections.singletonMap("tier", String.valueOf(tierReached(state)))));
                }
            }
        }
    }

    // ---------------------------------------------------------------- actions

    /** Claims one tier's reward. Returns false when it is not claimable (not reached, premium missing, already taken). */
    public static boolean claim(Habbo habbo, int tier, boolean premium) {
        ensureLoaded();
        if (habbo == null || season == null) return false;

        Tier ladder = TIERS.get(tier);
        if (ladder == null) return false;

        UserState state = getUserState(habbo.getHabboInfo().getId());
        if (tier > tierReached(state)) return false;
        if (premium && !state.premium) return false;

        Set<Integer> claimed = premium ? state.claimedPremium : state.claimedFree;
        if (claimed.contains(tier)) return false;

        String type = premium ? ladder.premiumType() : ladder.freeType();
        String data = premium ? ladder.premiumData() : ladder.freeData();
        int amount = premium ? ladder.premiumAmount() : ladder.freeAmount();

        if (type.isEmpty()) return false;

        synchronized (state) {
            if (claimed.contains(tier)) return false;
            claimed.add(tier);
            saveUser(habbo.getHabboInfo().getId(), state);
        }

        RewardGranter.grant(habbo, type, data, amount);
        return true;
    }

    /** Buys the premium track with the season's currency (diamonds by default). */
    public static boolean buyPremium(Habbo habbo) {
        ensureLoaded();
        if (habbo == null || season == null) return false;

        UserState state = getUserState(habbo.getHabboInfo().getId());
        if (state.premium) return false;

        int price = Math.max(0, season.premiumPrice());
        int type = season.pointsType();

        if (price > 0) {
            int balance = type == 0 ? habbo.getHabboInfo().getCredits() : habbo.getHabboInfo().getCurrencyAmount(type);
            if (balance < price) return false;

            if (type == 0) habbo.giveCredits(-price, "battlepass.premium");
            else habbo.givePoints(type, -price, "battlepass.premium");
        }

        state.premium = true;
        saveUser(habbo.getHabboInfo().getId(), state);
        return true;
    }

    /**
     * Staff editor: replaces the active season, its tiers and quests from one JSON document
     * {season:{id,name,startsAt,endsAt,premiumPrice,pointsType,xpPerTier,tierCount}, tiers:[...], quests:[...]}.
     * id 0 creates a new season (and deactivates the others).
     */
    public static boolean adminSave(String json) {
        JsonObject root;
        try {
            JsonElement element = JsonParser.parseString(json);
            if (!element.isJsonObject()) return false;
            root = element.getAsJsonObject();
        } catch (RuntimeException e) {
            return false;
        }

        JsonObject seasonJson = root.has("season") && root.get("season").isJsonObject() ? root.getAsJsonObject("season") : null;
        if (seasonJson == null) return false;

        int id = intOf(seasonJson, "id", 0);
        String name = strOf(seasonJson, "name", "Stagione").trim();
        if (name.isEmpty() || name.length() > 64) return false;
        int startsAt = intOf(seasonJson, "startsAt", Emulator.getIntUnixTimestamp());
        int endsAt = intOf(seasonJson, "endsAt", startsAt + 60 * 86400);
        int premiumPrice = Math.max(0, intOf(seasonJson, "premiumPrice", 0));
        int pointsType = intOf(seasonJson, "pointsType", 5);
        int xpPerTier = Math.max(1, intOf(seasonJson, "xpPerTier", 500));
        int tierCount = Math.max(1, Math.min(200, intOf(seasonJson, "tierCount", 30)));

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            connection.setAutoCommit(false);
            try {
                if (id <= 0) {
                    try (PreparedStatement statement = connection.prepareStatement("UPDATE battle_pass_seasons SET active = 0")) {
                        statement.executeUpdate();
                    }
                    try (PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO battle_pass_seasons (name, starts_at, ends_at, premium_price, points_type, xp_per_tier, tier_count, active) VALUES (?, ?, ?, ?, ?, ?, ?, 1)",
                            Statement.RETURN_GENERATED_KEYS)) {
                        statement.setString(1, name);
                        statement.setInt(2, startsAt);
                        statement.setInt(3, endsAt);
                        statement.setInt(4, premiumPrice);
                        statement.setInt(5, pointsType);
                        statement.setInt(6, xpPerTier);
                        statement.setInt(7, tierCount);
                        statement.executeUpdate();
                        try (ResultSet keys = statement.getGeneratedKeys()) {
                            if (keys.next()) id = keys.getInt(1);
                        }
                    }
                } else {
                    try (PreparedStatement statement = connection.prepareStatement(
                            "UPDATE battle_pass_seasons SET name = ?, starts_at = ?, ends_at = ?, premium_price = ?, points_type = ?, xp_per_tier = ?, tier_count = ?, active = 1 WHERE id = ?")) {
                        statement.setString(1, name);
                        statement.setInt(2, startsAt);
                        statement.setInt(3, endsAt);
                        statement.setInt(4, premiumPrice);
                        statement.setInt(5, pointsType);
                        statement.setInt(6, xpPerTier);
                        statement.setInt(7, tierCount);
                        statement.setInt(8, id);
                        if (statement.executeUpdate() == 0) {
                            connection.rollback();
                            return false;
                        }
                    }
                }

                try (PreparedStatement statement = connection.prepareStatement("DELETE FROM battle_pass_tiers WHERE season_id = ?")) {
                    statement.setInt(1, id);
                    statement.executeUpdate();
                }

                JsonArray tiers = root.has("tiers") && root.get("tiers").isJsonArray() ? root.getAsJsonArray("tiers") : new JsonArray();
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO battle_pass_tiers (season_id, tier, free_type, free_data, free_amount, premium_type, premium_data, premium_amount) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                    Set<Integer> seen = new HashSet<>();
                    for (JsonElement element : tiers) {
                        if (!element.isJsonObject()) continue;
                        JsonObject tier = element.getAsJsonObject();
                        int number = intOf(tier, "tier", 0);
                        if (number < 1 || number > tierCount || !seen.add(number)) continue;
                        statement.setInt(1, id);
                        statement.setInt(2, number);
                        statement.setString(3, rewardType(strOf(tier, "freeType", "")));
                        statement.setString(4, strOf(tier, "freeData", "").trim());
                        statement.setInt(5, Math.max(0, intOf(tier, "freeAmount", 0)));
                        statement.setString(6, rewardType(strOf(tier, "premiumType", "")));
                        statement.setString(7, strOf(tier, "premiumData", "").trim());
                        statement.setInt(8, Math.max(0, intOf(tier, "premiumAmount", 0)));
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }

                JsonArray quests = root.has("quests") && root.get("quests").isJsonArray() ? root.getAsJsonArray("quests") : new JsonArray();
                Set<Integer> keptQuestIds = new HashSet<>();
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE battle_pass_quests SET period = ?, metric = ?, target = ?, xp = ?, title = ? WHERE id = ? AND season_id = ?");
                     PreparedStatement insert = connection.prepareStatement(
                             "INSERT INTO battle_pass_quests (season_id, period, metric, target, xp, title) VALUES (?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                    for (JsonElement element : quests) {
                        if (!element.isJsonObject()) continue;
                        JsonObject quest = element.getAsJsonObject();
                        String period = strOf(quest, "period", "daily").toLowerCase(Locale.ROOT);
                        String metric = strOf(quest, "metric", "chat").toLowerCase(Locale.ROOT);
                        if (!PERIODS.contains(period) || !METRICS.contains(metric)) continue;
                        int target = Math.max(1, intOf(quest, "target", 1));
                        int xp = Math.max(0, intOf(quest, "xp", 0));
                        String title = strOf(quest, "title", "").trim();
                        if (title.length() > 96) title = title.substring(0, 96);
                        int questId = intOf(quest, "id", 0);

                        if (questId > 0) {
                            update.setString(1, period);
                            update.setString(2, metric);
                            update.setInt(3, target);
                            update.setInt(4, xp);
                            update.setString(5, title);
                            update.setInt(6, questId);
                            update.setInt(7, id);
                            if (update.executeUpdate() > 0) {
                                keptQuestIds.add(questId);
                                continue;
                            }
                        }

                        insert.setInt(1, id);
                        insert.setString(2, period);
                        insert.setString(3, metric);
                        insert.setInt(4, target);
                        insert.setInt(5, xp);
                        insert.setString(6, title);
                        insert.executeUpdate();
                        try (ResultSet keys = insert.getGeneratedKeys()) {
                            if (keys.next()) keptQuestIds.add(keys.getInt(1));
                        }
                    }
                }

                try (PreparedStatement statement = connection.prepareStatement("SELECT id FROM battle_pass_quests WHERE season_id = ?")) {
                    statement.setInt(1, id);
                    List<Integer> toDelete = new ArrayList<>();
                    try (ResultSet set = statement.executeQuery()) {
                        while (set.next()) {
                            if (!keptQuestIds.contains(set.getInt("id"))) toDelete.add(set.getInt("id"));
                        }
                    }
                    if (!toDelete.isEmpty()) {
                        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM battle_pass_quests WHERE id = ?")) {
                            for (Integer questId : toDelete) {
                                delete.setInt(1, questId);
                                delete.addBatch();
                            }
                            delete.executeBatch();
                        }
                    }
                }

                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to save the battle pass", e);
            return false;
        }

        reload();
        return true;
    }

    private static String rewardType(String type) {
        String value = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
        return RewardGranter.isKnownType(value) ? value : "";
    }

    private static int intOf(JsonObject object, String key, int fallback) {
        try {
            return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsInt() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static String strOf(JsonObject object, String key, String fallback) {
        try {
            return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}
