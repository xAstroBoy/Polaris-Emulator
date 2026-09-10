package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.commands.BssCommandPreferences.Flag;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.messages.outgoing.users.InClientLinkComposer;

final class BssPreferenceCommand extends Command {
    private final Flag flag;

    BssPreferenceCommand(String permission, Flag flag) {
        super(permission, Emulator.getTexts().getValue("commands.keys." + permission).split(";"));
        this.flag = flag;
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) {
        int userId = gameClient.getHabbo().getHabboInfo().getId();
        boolean enabled = BssCommandPreferences.toggle(userId, flag);
        gameClient.getHabbo().whisper(
                Emulator.getTexts().getValue("commands.success." + permission + "." + (enabled ? "enabled" : "disabled")),
                RoomChatMessageBubbles.ALERT);

        if (flag == Flag.USER_CLICK_ENABLED) {
            // ":tc" turns off clicking people, and until now the server only said so once the click had
            // already happened ("block-menu"), by which point the avatar had swallowed it: the click never
            // reached the floor, so you could not walk to a tile an avatar was standing on or in front of.
            // Telling the client the state up front lets it stop avatars catching the pointer at all.
            gameClient.sendResponse(
                    new InClientLinkComposer("avatar-info/click-through/" + (enabled ? "off" : "on")));
        }

        return true;
    }
}
