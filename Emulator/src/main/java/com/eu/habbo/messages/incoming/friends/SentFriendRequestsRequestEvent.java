package com.eu.habbo.messages.incoming.friends;

import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.friends.SentFriendRequestsComposer;

/** CUSTOM packet 10112: asks for the list of friend requests this user sent. No payload. */
public class SentFriendRequestsRequestEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getHabbo() == null) return;

        this.client.sendResponse(new SentFriendRequestsComposer(this.client.getHabbo().getHabboInfo().getId()));
    }
}
