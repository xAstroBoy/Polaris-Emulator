package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.inventory.InventoryItemsComposer;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import com.eu.habbo.threading.runnables.QueryDeleteHabboItems;
import java.util.ArrayList;
import java.util.List;

public class EmptyInventoryCommand extends Command {
    public EmptyInventoryCommand() {
        super(
                "cmd_empty",
                Emulator.getTexts().getValue("commands.keys.cmd_empty").split(";"));
    }

    // Confirmation words accepted next to the configured generic.yes, so every alias of the
    // command (":empty yes", ":pulisci si") works without the hint pointing at a different alias.
    private static final String[] CONFIRM_WORDS = {"yes", "y", "si", "sì", "ok"};

    static boolean isConfirmation(String word) {
        if (word == null) return false;
        if (word.equalsIgnoreCase(Emulator.getTexts().getValue("generic.yes"))) return true;
        for (String candidate : CONFIRM_WORDS) {
            if (candidate.equalsIgnoreCase(word)) return true;
        }
        return false;
    }

    private static String verifyMessage(String[] params) {
        String alias = params.length > 0 && params[0] != null && !params[0].isEmpty() ? params[0] : "empty";
        return Emulator.getTexts()
                .getValue("commands.succes.cmd_empty.verify")
                .replace("%generic.yes%", Emulator.getTexts().getValue("generic.yes"))
                .replace("%command%", alias)
                .replace(":empty ", ":" + alias + " ");
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (params.length == 1 || (params.length == 2 && !isConfirmation(params[1]))) {
            if (gameClient.getHabbo().getHabboInfo().getCurrentRoom() != null) {
                if (gameClient.getHabbo().getHabboInfo().getCurrentRoom().getUserCount() > 10) {
                    gameClient.getHabbo().alert(verifyMessage(params));
                } else {
                    gameClient.getHabbo().whisper(verifyMessage(params), RoomChatMessageBubbles.ALERT);
                }
            }

            return true;
        }

        if (params.length >= 2 && isConfirmation(params[1])) {

            Habbo habbo = (params.length == 3 && gameClient.getHabbo().hasPermission(Permission.ACC_EMPTY_OTHERS))
                    ? Emulator.getGameEnvironment().getHabboManager().getHabbo(params[2])
                    : gameClient.getHabbo();

            if (habbo != null) {
                List<HabboItem> items = new ArrayList<>(
                        habbo.getInventory().getItemsComponent().getItems().values());
                habbo.getInventory().getItemsComponent().getItems().clear();
                Emulator.getThreading().runPersistence(new QueryDeleteHabboItems(items));

                habbo.getClient().sendResponse(new InventoryRefreshComposer());
                habbo.getClient()
                        .sendResponse(new InventoryItemsComposer(
                                0,
                                1,
                                gameClient
                                        .getHabbo()
                                        .getInventory()
                                        .getItemsComponent()
                                        .getItems()));

                gameClient
                        .getHabbo()
                        .whisper(
                                Emulator.getTexts()
                                        .getValue("commands.succes.cmd_empty.cleared")
                                        .replace(
                                                "%username%",
                                                habbo.getHabboInfo().getUsername()),
                                RoomChatMessageBubbles.ALERT);
            } else {
                gameClient
                        .getHabbo()
                        .whisper(
                                Emulator.getTexts().getValue("commands.error.cmd_empty"), RoomChatMessageBubbles.ALERT);
            }
        }

        return true;
    }
}
