package com.eu.habbo.messages.outgoing.generic.alerts;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Nitro notification dialog (header 1992): a type plus a parameter map.
 * Recognised parameters: {@code title}, {@code message}, {@code image}, {@code linkUrl},
 * {@code linkTitle} and {@code display} ({@code BUBBLE} or {@code ALERT}).
 */
public class NotificationDialogComposer extends MessageComposer {
    private final String type;
    private final Map<String, String> parameters;

    public NotificationDialogComposer(String type, Map<String, String> parameters) {
        this.type = type == null ? "" : type;
        this.parameters = parameters == null ? new LinkedHashMap<>() : parameters;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.NotificationDialogComposer);
        this.response.appendString(this.type);
        this.response.appendInt(this.parameters.size());
        for (Map.Entry<String, String> entry : this.parameters.entrySet()) {
            this.response.appendString(entry.getKey());
            this.response.appendString(entry.getValue() == null ? "" : entry.getValue());
        }
        return this.response;
    }
}
