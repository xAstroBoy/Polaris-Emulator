package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import java.util.Comparator;
import java.util.List;

public class OnlineCommand extends Command {
    public OnlineCommand() {
        super("cmd_online", new String[] {"online", "on", "utenti", "connessi"});
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) {
        List<Habbo> online = Emulator.getGameEnvironment()
                .getHabboManager()
                .getOnlineHabbos()
                .values()
                .stream()
                .filter(habbo -> habbo != null && habbo.isOnline())
                .sorted(Comparator.comparing(
                        habbo -> habbo.getHabboInfo().getUsername(),
                        String.CASE_INSENSITIVE_ORDER))
                .toList();

        StringBuilder message = new StringBuilder("<b>Utenti online (")
                .append(online.size())
                .append("):</b>\r");

        if (online.isEmpty()) {
            message.append("Nessun utente online.");
        } else {
            for (Habbo habbo : online) {
                Room room = habbo.getHabboInfo().getCurrentRoom();
                message.append("- ")
                        .append(habbo.getHabboInfo().getUsername())
                        .append(" | ")
                        .append(habbo.getHabboInfo().getRank().getName())
                        .append(" | ");

                if (room == null) {
                    message.append("Hotel View");
                } else {
                    message.append(room.getName())
                            .append(" (#")
                            .append(room.getId())
                            .append(")");
                }

                message
                        .append("\r");
            }
        }

        gameClient.getHabbo().alert(message.toString());
        return true;
    }
}
