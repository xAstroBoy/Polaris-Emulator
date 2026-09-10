package com.eu.habbo.messages.outgoing.friends;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/** CUSTOM packet 10110: a console message changed. int peerId (thread), int messageId, string text, bool deleted. */
public class MessengerChatEditedComposer extends MessageComposer {
    private final int peerId;
    private final int messageId;
    private final String text;
    private final boolean deleted;

    public MessengerChatEditedComposer(int peerId, int messageId, String text, boolean deleted) {
        this.peerId = peerId;
        this.messageId = messageId;
        this.text = text == null ? "" : text;
        this.deleted = deleted;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.MessengerChatEditedComposer);
        this.response.appendInt(this.peerId);
        this.response.appendInt(this.messageId);
        this.response.appendString(this.text);
        this.response.appendBoolean(this.deleted);
        return this.response;
    }
}
