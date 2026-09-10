package com.eu.habbo.messages.incoming.friends;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.messenger.FriendRequest;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.friends.LoadFriendRequestsComposer;
import com.eu.habbo.messages.outgoing.friends.SentFriendRequestsComposer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/** CUSTOM packet 10113: withdraw a friend request you sent. int targetUserId. Refreshes both sides' lists. */
public class CancelFriendRequestEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(CancelFriendRequestEvent.class);

    @Override
    public void handle() throws Exception {
        int targetId = this.packet.readInt();
        Habbo habbo = this.client.getHabbo();

        if (habbo == null || targetId <= 0) return;

        int selfId = habbo.getHabboInfo().getId();
        int removed = 0;

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM messenger_friendrequests WHERE user_from_id = ? AND user_to_id = ?")) {
            statement.setInt(1, selfId);
            statement.setInt(2, targetId);
            removed = statement.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Failed to cancel friend request {} -> {}", selfId, targetId, e);
        }

        if (removed > 0) {
            Habbo target = Emulator.getGameEnvironment().getHabboManager().getHabbo(targetId);

            if (target != null && target.getMessenger() != null) {
                synchronized (target.getMessenger().getFriendRequests()) {
                    target.getMessenger().getFriendRequests().removeIf((FriendRequest request) -> request.getId() == selfId);
                }
                if (target.getClient() != null) target.getClient().sendResponse(new LoadFriendRequestsComposer(target));
            }
        }

        this.client.sendResponse(new SentFriendRequestsComposer(selfId));
    }
}
