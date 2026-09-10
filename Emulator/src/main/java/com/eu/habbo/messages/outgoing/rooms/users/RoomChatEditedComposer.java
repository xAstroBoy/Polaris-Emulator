package com.eu.habbo.messages.outgoing.rooms.users;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/** CUSTOM packet 10107: a room chat bubble changed text or was deleted. int unitId, int messageId, string text, bool deleted. */
public class RoomChatEditedComposer extends MessageComposer {
    private final int roomUnitId;
    private final int messageId;
    private final String text;
    private final boolean deleted;

    public RoomChatEditedComposer(int roomUnitId, int messageId, String text, boolean deleted) {
        this.roomUnitId = roomUnitId;
        this.messageId = messageId;
        this.text = text == null ? "" : text;
        this.deleted = deleted;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RoomChatEditedComposer);
        this.response.appendInt(this.roomUnitId);
        this.response.appendInt(this.messageId);
        this.response.appendString(this.text);
        this.response.appendBoolean(this.deleted);
        return this.response;
    }
}
