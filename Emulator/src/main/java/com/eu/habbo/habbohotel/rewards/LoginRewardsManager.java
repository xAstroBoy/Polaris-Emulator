package com.eu.habbo.habbohotel.rewards;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HSmile-style daily login rewards: a seven day ladder, one claim per calendar day, the streak resets when a day
 * is skipped. The ladder lives in {@code login_rewards} (editable in-game by staff), the per-user progress in
 * {@code user_login_rewards}.
 */
public final class LoginRewardsManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoginRewardsManager.class);

    public static final int DAYS = 7;

    /** One rung of the ladder. */
    public record Reward(int day, String type, String data, int amount) {}

    /** Snapshot of a user's ladder position for the composer. */
    public record Progress(boolean claimable, int streak, int record, int currentDay) {}

    private static final Map<Integer, Reward> REWARDS = new ConcurrentHashMap<>();
    private static volatile boolean loaded = false;

    private LoginRewardsManager() {}

    public static Map<Integer, Reward> getRewards() {
        ensureLoaded();
        return Collections.unmodifiableMap(new TreeMap<>(REWARDS));
    }

    public static Reward getReward(int day) {
        ensureLoaded();
        return REWARDS.get(day);
    }

    public static synchronized void reload() {
        Map<Integer, Reward> rewards = new TreeMap<>();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT day, reward_type, reward_data, amount FROM login_rewards ORDER BY day ASC");
             ResultSet set = statement.executeQuery()) {
            while (set.next()) {
                int day = set.getInt("day");
                if (day < 1 || day > DAYS) continue;
                rewards.put(day, new Reward(day, set.getString("reward_type"), set.getString("reward_data") == null ? "" : set.getString("reward_data"), set.getInt("amount")));
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to load login rewards", e);
        }

        REWARDS.clear();
        REWARDS.putAll(rewards);
        loaded = true;
    }

    private static void ensureLoaded() {
        if (!loaded) reload();
    }

    public static boolean save(int day, String type, String data, int amount) {
        if (day < 1 || day > DAYS || !RewardGranter.isKnownType(type)) return false;

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO login_rewards (day, reward_type, reward_data, amount) VALUES (?, ?, ?, ?) "
                             + "ON DUPLICATE KEY UPDATE reward_type = VALUES(reward_type), reward_data = VALUES(reward_data), amount = VALUES(amount)")) {
            statement.setInt(1, day);
            statement.setString(2, type);
            statement.setString(3, data == null ? "" : data);
            statement.setInt(4, Math.max(0, amount));
            statement.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Failed to save login reward day {}", day, e);
            return false;
        }

        reload();
        return true;
    }

    public static boolean delete(int day) {
        if (day < 1 || day > DAYS) return false;

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM login_rewards WHERE day = ?")) {
            statement.setInt(1, day);
            statement.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Failed to delete login reward day {}", day, e);
            return false;
        }

        reload();
        return true;
    }

    private static LocalDate today() {
        return LocalDate.now(ZoneId.systemDefault());
    }

    /** Reads the user's row and turns it into "what can be claimed today". */
    public static Progress getProgress(int userId) {
        int currentDay = 0;
        int streak = 0;
        int record = 0;
        LocalDate lastClaim = null;

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT current_day, streak, record, last_claim_date FROM user_login_rewards WHERE user_id = ? LIMIT 1")) {
            statement.setInt(1, userId);
            try (ResultSet set = statement.executeQuery()) {
                if (set.next()) {
                    currentDay = set.getInt("current_day");
                    streak = set.getInt("streak");
                    record = set.getInt("record");
                    Date date = set.getDate("last_claim_date");
                    lastClaim = date == null ? null : date.toLocalDate();
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to load login reward progress for {}", userId, e);
        }

        LocalDate today = today();

        if (lastClaim != null && lastClaim.equals(today)) {
            return new Progress(false, streak, record, Math.max(1, currentDay));
        }

        if (lastClaim != null && lastClaim.equals(today.minusDays(1))) {
            int next = currentDay >= DAYS || currentDay < 1 ? 1 : currentDay + 1;
            return new Progress(true, streak, record, next);
        }

        return new Progress(true, 0, record, 1);
    }

    /** Grants today's reward if it is claimable. Returns the refreshed progress, or null when nothing was claimable. */
    public static Progress claim(Habbo habbo) {
        if (habbo == null) return null;

        int userId = habbo.getHabboInfo().getId();
        Progress progress = getProgress(userId);
        if (!progress.claimable()) return null;

        Reward reward = getReward(progress.currentDay());
        if (reward != null) {
            RewardGranter.grant(habbo, reward.type(), reward.data(), reward.amount());
        }

        int streak = progress.streak() + 1;
        int record = Math.max(progress.record(), streak);

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO user_login_rewards (user_id, current_day, streak, record, last_claim_date) VALUES (?, ?, ?, ?, ?) "
                             + "ON DUPLICATE KEY UPDATE current_day = VALUES(current_day), streak = VALUES(streak), record = VALUES(record), last_claim_date = VALUES(last_claim_date)")) {
            statement.setInt(1, userId);
            statement.setInt(2, progress.currentDay());
            statement.setInt(3, streak);
            statement.setInt(4, record);
            statement.setDate(5, Date.valueOf(today()));
            statement.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Failed to store login reward claim for {}", userId, e);
        }

        return new Progress(false, streak, record, progress.currentDay());
    }
}
