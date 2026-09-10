package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.Emulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * HSmile "video curtain": a room-wide YouTube video that covers the room for everyone inside, controlled by the
 * owner / rights holders. State is kept in memory per room and mirrored to {@code room_video_curtain} so a
 * restart does not drop a running screening.
 */
public final class VideoCurtainManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(VideoCurtainManager.class);
    private static final Pattern VIDEO_ID = Pattern.compile("^[A-Za-z0-9_-]{11}$");

    /** position = seconds into the video at updatedAt; a playing curtain advances from there. */
    public record State(String videoId, boolean playing, int position, int updatedAt, int setterId) {}

    private static final ConcurrentHashMap<Integer, State> STATES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, Boolean> LOADED = new ConcurrentHashMap<>();

    private VideoCurtainManager() {}

    public static boolean isValidVideoId(String videoId) {
        return videoId != null && VIDEO_ID.matcher(videoId).matches();
    }

    public static State get(int roomId) {
        if (LOADED.putIfAbsent(roomId, Boolean.TRUE) == null) {
            load(roomId);
        }
        return STATES.get(roomId);
    }

    public static State set(int roomId, String videoId, int setterId) {
        if (!isValidVideoId(videoId)) return null;

        State state = new State(videoId, true, 0, Emulator.getIntUnixTimestamp(), setterId);
        STATES.put(roomId, state);
        LOADED.put(roomId, Boolean.TRUE);
        persist(roomId, state);
        return state;
    }

    public static State playback(int roomId, boolean playing, int position, int setterId) {
        State current = get(roomId);
        if (current == null) return null;

        State state = new State(current.videoId(), playing, Math.max(0, position), Emulator.getIntUnixTimestamp(), setterId);
        STATES.put(roomId, state);
        persist(roomId, state);
        return state;
    }

    public static void clear(int roomId) {
        STATES.remove(roomId);
        LOADED.put(roomId, Boolean.TRUE);

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM room_video_curtain WHERE room_id = ?")) {
            statement.setInt(1, roomId);
            statement.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Failed to clear video curtain for room {}", roomId, e);
        }
    }

    private static void load(int roomId) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT video_id, playing, position, updated_at, setter_id FROM room_video_curtain WHERE room_id = ? LIMIT 1")) {
            statement.setInt(1, roomId);
            try (ResultSet set = statement.executeQuery()) {
                if (set.next()) {
                    String videoId = set.getString("video_id");
                    if (isValidVideoId(videoId)) {
                        STATES.put(roomId, new State(videoId, set.getInt("playing") == 1, set.getInt("position"), set.getInt("updated_at"), set.getInt("setter_id")));
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to load video curtain for room {}", roomId, e);
        }
    }

    private static void persist(int roomId, State state) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO room_video_curtain (room_id, video_id, playing, position, updated_at, setter_id) VALUES (?, ?, ?, ?, ?, ?) "
                             + "ON DUPLICATE KEY UPDATE video_id = VALUES(video_id), playing = VALUES(playing), position = VALUES(position), updated_at = VALUES(updated_at), setter_id = VALUES(setter_id)")) {
            statement.setInt(1, roomId);
            statement.setString(2, state.videoId());
            statement.setInt(3, state.playing() ? 1 : 0);
            statement.setInt(4, state.position());
            statement.setInt(5, state.updatedAt());
            statement.setInt(6, state.setterId());
            statement.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Failed to persist video curtain for room {}", roomId, e);
        }
    }
}
