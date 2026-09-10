package com.eu.habbo.messages.outgoing.rooms.users;

import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class RoomUserRespectComposer extends MessageComposer {
    private final Habbo habbo;
    private final Habbo giver;

    /**
     * The signature plugins were built against, before the sender could be named. Kept so an existing
     * plugin jar keeps linking; it sends no sender, which is what the packet carried back then.
     */
    public RoomUserRespectComposer(Habbo habbo) {
        this(habbo, null);
    }

    public RoomUserRespectComposer(Habbo habbo, Habbo giver) {
        this.habbo = habbo;
        this.giver = giver;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RoomUserRespectComposer);
        this.response.appendInt(this.habbo.getHabboInfo().getId());
        this.response.appendInt(this.habbo.getHabboStats().respectPointsReceived);
        // Optional trailing field: legacy clients keep reading the original
        // two fields, while Polaris Nitro can name the sender.
        this.response.appendString(this.giver == null ? "" : this.giver.getHabboInfo().getUsername());
        return this.response;
    }

    public Habbo getHabbo() {
        return habbo;
    }
}
