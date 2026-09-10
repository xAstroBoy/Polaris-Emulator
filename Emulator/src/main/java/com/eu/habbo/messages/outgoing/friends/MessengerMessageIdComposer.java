package com.eu.habbo.messages.outgoing.friends;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/** CUSTOM packet 10109: tells the sender the live id of the console message it just sent. int peerId, int messageId. */
public class MessengerMessageIdComposer extends MessageComposer {
    private final int peerId;
    private final int messageId;

    public MessengerMessageIdComposer(int peerId, int messageId) {
        this.peerId = peerId;
        this.messageId = messageId;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.MessengerMessageIdComposer);
        this.response.appendInt(this.peerId);
        this.response.appendInt(this.messageId);
        return this.response;
    }
}
