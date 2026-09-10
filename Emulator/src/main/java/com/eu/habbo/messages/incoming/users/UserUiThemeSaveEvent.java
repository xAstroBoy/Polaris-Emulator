package com.eu.habbo.messages.incoming.users;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.users.UserUiThemeComposer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * CUSTOM packet 10126: string json. Stores the user's client theme (accent, surfaces, per-window overrides) so
 * it follows them across devices. Must be a JSON object under 16 KB; "" or "{}" clears it.
 */
public class UserUiThemeSaveEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserUiThemeSaveEvent.class);
    private static final int MAX_LENGTH = 16 * 1024;

    @Override
    public void handle() throws Exception {
        String json = this.packet.readString();
        Habbo habbo = this.client.getHabbo();
        if (habbo == null) return;

        if (json == null) json = "";
        json = json.trim();
        if (json.length() > MAX_LENGTH) return;

        if (!json.isEmpty()) {
            try {
                JsonElement element = JsonParser.parseString(json);
                if (!element.isJsonObject()) return;
                if (element.getAsJsonObject().size() == 0) json = "";
            } catch (RuntimeException e) {
                return;
            }
        }

        int userId = habbo.getHabboInfo().getId();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            if (json.isEmpty()) {
                try (PreparedStatement statement = connection.prepareStatement("DELETE FROM user_ui_theme WHERE user_id = ?")) {
                    statement.setInt(1, userId);
                    statement.executeUpdate();
                }
            } else {
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO user_ui_theme (user_id, theme_json, updated_at) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE theme_json = VALUES(theme_json), updated_at = VALUES(updated_at)")) {
                    statement.setInt(1, userId);
                    statement.setString(2, json);
                    statement.setInt(3, Emulator.getIntUnixTimestamp());
                    statement.executeUpdate();
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to save UI theme for {}", userId, e);
        }

        this.client.sendResponse(new UserUiThemeComposer(json));
    }
}
