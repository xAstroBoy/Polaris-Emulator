package com.eu.habbo.messages.incoming.friends;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.messenger.MessengerLiveMessages;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.friends.MessengerChatEditedComposer;

/**
 * CUSTOM packet 10108: edit or delete one of your own console messages. int peerId, int messageId, string text
 * ("" deletes). Both participants receive MessengerChatEditedComposer; five-minute window like room chat.
 */
public class MessengerChatEditEvent extends MessageHandler {
    private static final int MAX_LENGTH = 255;

    @Override
    public void handle() throws Exception {
        int peerId = this.packet.readInt();
        int messageId = this.packet.readInt();
        String text = this.packet.readString();
        Habbo habbo = this.client.getHabbo();

        if (habbo == null) return;

        MessengerLiveMessages.Entry entry = MessengerLiveMessages.get(messageId);
        int selfId = habbo.getHabboInfo().getId();

        if (entry == null || entry.fromId() != selfId || entry.toId() != peerId) return;
        if (!MessengerLiveMessages.isWithinEditWindow(entry)) return;

        boolean delete = text == null || text.trim().isEmpty();
        String finalText = "";

        if (!delete) {
            if (!habbo.getHabboStats().allowTalk()) return;
            String trimmed = text.trim();
            if (trimmed.length() > MAX_LENGTH) trimmed = trimmed.substring(0, MAX_LENGTH);
            finalText = Emulator.getGameEnvironment().getWordFilter().filter(trimmed, habbo);
            if (finalText.isEmpty()) return;
        } else {
            MessengerLiveMessages.remove(messageId);
        }

        // the sender sees the change in the thread with the peer, the peer in the thread with the sender
        this.client.sendResponse(new MessengerChatEditedComposer(peerId, messageId, finalText, delete));

        Habbo peer = Emulator.getGameEnvironment().getHabboManager().getHabbo(peerId);
        if (peer != null && peer.getClient() != null) {
            peer.getClient().sendResponse(new MessengerChatEditedComposer(selfId, messageId, finalText, delete));
        }
    }
}
