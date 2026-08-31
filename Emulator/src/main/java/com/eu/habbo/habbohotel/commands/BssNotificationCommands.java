package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.generic.alerts.StaffAlertWithLinkComposer;
import java.util.Locale;
import java.util.Set;

/** BSS event announcements whose labels remain editable in emulator settings/housekeeping. */
final class BssNotificationCommand extends Command {
    private static final Set<String> TYPES = Set.of("arb", "staff", "pok", "evento");

    BssNotificationCommand() {
        super("cmd_bss_notification", Emulator.getTexts().getValue("commands.keys.cmd_bss_notification").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) {
        if (params.length != 2) return usage(gameClient);
        return announce(gameClient, params[1]);
    }

    static boolean announce(GameClient gameClient, String requestedType) {
        Room room = gameClient.getHabbo().getHabboInfo().getCurrentRoom();
        String type = requestedType == null ? "" : requestedType.toLowerCase(Locale.ROOT);
        if (room == null || !TYPES.contains(type)) return usage(gameClient);

        String configured = Emulator.getConfig().getValue(
                "bss.notification." + type + ".text",
                switch (type) {
                    case "arb" -> "Gli arbitri sono richiesti";
                    case "staff" -> "Lo staff e richiesto";
                    case "pok" -> "Il poker e aperto";
                    default -> "Un evento e iniziato";
                });
        String message = configured + "\r\nStanza: " + room.getName();
        String link = "event:navigator/goto/" + room.getId();
        ServerMessage alert = new StaffAlertWithLinkComposer(message, link).compose();

        for (Habbo recipient : Emulator.getGameEnvironment().getHabboManager().getOnlineHabbos().values()) {
            if (!recipient.getHabboStats().blockStaffAlerts && recipient.getClient() != null) {
                recipient.getClient().sendResponse(alert);
            }
        }

        gameClient.getHabbo().whisper(
                Emulator.getTexts().getValue("commands.success.cmd_bss_notification")
                        .replace("%type%", type)
                        .replace("%room%", room.getName()),
                RoomChatMessageBubbles.ALERT);
        return true;
    }

    private static boolean usage(GameClient gameClient) {
        gameClient.getHabbo().whisper(
                Emulator.getTexts().getValue("commands.error.cmd_bss_notification.usage"),
                RoomChatMessageBubbles.ALERT);
        return true;
    }
}

final class BssOpenPokerCommand extends Command {
    BssOpenPokerCommand() {
        super("cmd_bss_open_poker", Emulator.getTexts().getValue("commands.keys.cmd_bss_open_poker").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) {
        return BssNotificationCommand.announce(gameClient, "pok");
    }
}
