package com.eu.habbo.messages.incoming.rooms.users;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomChatEditRegistry;
import com.eu.habbo.habbohotel.rooms.RoomChatMessage;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.users.RoomChatEditedComposer;

/**
 * CUSTOM packet 10106: edit or delete one of your own room chat messages (empty text = delete). Staff with
 * acc_supporttool may delete anyone's message. Only messages younger than five minutes can be changed; the room
 * receives RoomChatEditedComposer.
 */
public class RoomChatEditEvent extends MessageHandler {
    private static final int MAX_LENGTH = RoomChatMessage.MAXIMUM_LENGTH;

    @Override
    public void handle() throws Exception {
        int messageId = this.packet.readInt();
        String text = this.packet.readString();
        Habbo habbo = this.client.getHabbo();

        if (habbo == null || habbo.getHabboInfo().getCurrentRoom() == null) return;

        Room room = habbo.getHabboInfo().getCurrentRoom();
        RoomChatEditRegistry.Entry entry = RoomChatEditRegistry.get(messageId);

        if (entry == null || entry.roomId() != room.getId()) return;

        boolean own = entry.habboId() == habbo.getHabboInfo().getId();
        boolean staff = habbo.hasPermission("acc_supporttool");
        boolean delete = text == null || text.trim().isEmpty();

        if (!own && !(staff && delete)) return;
        if (own && !RoomChatEditRegistry.isWithinEditWindow(entry)) return;
        if (!delete && !habbo.getHabboStats().allowTalk()) return;

        String finalText = "";

        if (!delete) {
            String trimmed = text.trim();
            if (trimmed.length() > MAX_LENGTH) trimmed = trimmed.substring(0, MAX_LENGTH);
            finalText = Emulator.getGameEnvironment().getWordFilter().filter(trimmed, habbo);
            if (finalText.isEmpty()) return;
        } else {
            RoomChatEditRegistry.remove(messageId);
        }

        room.sendComposer(new RoomChatEditedComposer(entry.roomUnitId(), messageId, finalText, delete).compose());
    }
}
