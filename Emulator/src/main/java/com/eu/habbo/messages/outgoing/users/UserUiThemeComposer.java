package com.eu.habbo.messages.outgoing.users;

import com.eu.habbo.Emulator;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** CUSTOM packet 10128: string json — the user's saved client theme ("" when none). */
public class UserUiThemeComposer extends MessageComposer {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserUiThemeComposer.class);

    private final String json;

    public UserUiThemeComposer(String json) {
        this.json = json == null ? "" : json;
    }

    public static String load(int userId) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT theme_json FROM user_ui_theme WHERE user_id = ? LIMIT 1")) {
            statement.setInt(1, userId);
            try (ResultSet set = statement.executeQuery()) {
                if (set.next()) {
                    String json = set.getString("theme_json");
                    return json == null ? "" : json;
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to load UI theme for {}", userId, e);
        }
        return "";
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.UserUiThemeComposer);
        this.response.appendString(this.json);
        return this.response;
    }
}
