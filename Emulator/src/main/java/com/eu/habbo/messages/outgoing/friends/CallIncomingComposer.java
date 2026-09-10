package com.eu.habbo.messages.outgoing.friends;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/** CUSTOM packet 10135: a friend is calling. int callId, int fromId, string fromName, string fromLook, bool video. */
public class CallIncomingComposer extends MessageComposer {
    private final int callId;
    private final int fromId;
    private final String fromName;
    private final String fromLook;
    private final boolean video;

    public CallIncomingComposer(int callId, int fromId, String fromName, String fromLook, boolean video) {
        this.callId = callId;
        this.fromId = fromId;
        this.fromName = fromName == null ? "" : fromName;
        this.fromLook = fromLook == null ? "" : fromLook;
        this.video = video;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CallIncomingComposer);
        this.response.appendInt(this.callId);
        this.response.appendInt(this.fromId);
        this.response.appendString(this.fromName);
        this.response.appendString(this.fromLook);
        this.response.appendBoolean(this.video);
        return this.response;
    }
}
