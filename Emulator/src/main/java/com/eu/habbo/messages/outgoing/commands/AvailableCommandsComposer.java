package com.eu.habbo.messages.outgoing.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.commands.Command;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AvailableCommandsComposer extends MessageComposer {
    private final List<Command> commands;

    public AvailableCommandsComposer(List<Command> commands) {
        this.commands = commands;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.AvailableCommandsComposer);

        // getCommandsForRank() has already removed commands the Habbo cannot
        // use. Publish every alias of the remaining commands so Nitro's
        // autocomplete mirrors the same rank-aware command registry as the
        // emulator instead of exposing only keys[0].
        Map<String, AvailableCommand> available = new LinkedHashMap<>();

        for (Command cmd : this.commands) {
            String description = Emulator.getTexts().getValueQuietly(
                    "commands.description." + cmd.permission,
                    cmd.permission);

            for (String rawKey : cmd.keys) {
                if (rawKey == null) continue;

                String key = rawKey.strip();
                if (key.startsWith(":")) key = key.substring(1).strip();
                if (key.isEmpty()) continue;

                available.putIfAbsent(
                        key.toLowerCase(Locale.ROOT),
                        new AvailableCommand(key, description));
            }
        }

        this.response.appendInt(available.size());

        for (AvailableCommand command : available.values()) {
            this.response.appendString(command.key());
            this.response.appendString(command.description());
        }

        return this.response;
    }

    private record AvailableCommand(String key, String description) {}
}
