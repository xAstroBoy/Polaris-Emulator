package com.eu.habbo.messages.outgoing.friends;

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
import java.util.ArrayList;
import java.util.List;

/** CUSTOM packet 10111: the friend requests this user sent and the other side has not answered. int count, per: int userId, string name, string look. */
public class SentFriendRequestsComposer extends MessageComposer {
    private static final Logger LOGGER = LoggerFactory.getLogger(SentFriendRequestsComposer.class);

    private final int userId;

    public SentFriendRequestsComposer(int userId) {
        this.userId = userId;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.SentFriendRequestsComposer);

        List<String[]> rows = new ArrayList<>();
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT users.id, users.username, users.look FROM messenger_friendrequests INNER JOIN users ON users.id = messenger_friendrequests.user_to_id WHERE user_from_id = ? ORDER BY messenger_friendrequests.id DESC LIMIT 200")) {
            statement.setInt(1, this.userId);
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    rows.add(new String[]{String.valueOf(set.getInt("id")), set.getString("username"), set.getString("look")});
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to load sent friend requests for {}", this.userId, e);
        }

        this.response.appendInt(rows.size());
        for (String[] row : rows) {
            this.response.appendInt(Integer.parseInt(row[0]));
            this.response.appendString(row[1]);
            this.response.appendString(row[2] == null ? "" : row[2]);
        }

        return this.response;
    }
}
