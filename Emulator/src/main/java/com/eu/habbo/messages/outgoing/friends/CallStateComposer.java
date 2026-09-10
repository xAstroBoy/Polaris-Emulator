package com.eu.habbo.messages.outgoing.friends;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/**
 * CUSTOM packet 10136: the state of a call as seen by the receiver.
 * int callId, string state (ringing/connected/declined/ended/missed/busy/unavailable/failed), int peerId,
 * string peerName, string peerLook, bool video, string livekitUrl, string token, string room, string reason.
 */
public class CallStateComposer extends MessageComposer {
    private final int callId;
    private final String state;
    private final int peerId;
    private final String peerName;
    private final String peerLook;
    private final boolean video;
    private final String url;
    private final String token;
    private final String room;
    private final String reason;

    public CallStateComposer(int callId, String state, int peerId, String peerName, String peerLook, boolean video, String url, String token, String room, String reason) {
        this.callId = callId;
        this.state = state == null ? "" : state;
        this.peerId = peerId;
        this.peerName = peerName == null ? "" : peerName;
        this.peerLook = peerLook == null ? "" : peerLook;
        this.video = video;
        this.url = url == null ? "" : url;
        this.token = token == null ? "" : token;
        this.room = room == null ? "" : room;
        this.reason = reason == null ? "" : reason;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CallStateComposer);
        this.response.appendInt(this.callId);
        this.response.appendString(this.state);
        this.response.appendInt(this.peerId);
        this.response.appendString(this.peerName);
        this.response.appendString(this.peerLook);
        this.response.appendBoolean(this.video);
        this.response.appendString(this.url);
        this.response.appendString(this.token);
        this.response.appendString(this.room);
        this.response.appendString(this.reason);
        return this.response;
    }
}
