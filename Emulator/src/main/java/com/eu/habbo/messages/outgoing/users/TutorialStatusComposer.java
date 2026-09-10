package com.eu.habbo.messages.outgoing.users;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/** CUSTOM packet 10120: bool completed — whether the first-login tutorial was already finished (users_settings.nux). */
public class TutorialStatusComposer extends MessageComposer {
    private final boolean completed;

    public TutorialStatusComposer(boolean completed) {
        this.completed = completed;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.TutorialStatusComposer);
        this.response.appendBoolean(this.completed);
        return this.response;
    }
}
