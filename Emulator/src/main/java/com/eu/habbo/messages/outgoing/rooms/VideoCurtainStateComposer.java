package com.eu.habbo.messages.outgoing.rooms;

import com.eu.habbo.habbohotel.rooms.VideoCurtainManager.State;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/**
 * CUSTOM packet 10122: the room's video curtain. int roomId, bool active, string videoId, bool playing,
 * int position, int updatedAt, int setterId.
 */
public class VideoCurtainStateComposer extends MessageComposer {
    private final int roomId;
    private final State state;

    public VideoCurtainStateComposer(int roomId, State state) {
        this.roomId = roomId;
        this.state = state;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.VideoCurtainStateComposer);
        this.response.appendInt(this.roomId);
        this.response.appendBoolean(this.state != null);
        this.response.appendString(this.state == null ? "" : this.state.videoId());
        this.response.appendBoolean(this.state != null && this.state.playing());
        this.response.appendInt(this.state == null ? 0 : this.state.position());
        this.response.appendInt(this.state == null ? 0 : this.state.updatedAt());
        this.response.appendInt(this.state == null ? 0 : this.state.setterId());
        return this.response;
    }
}
