package com.eu.habbo.habbohotel.messenger;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Ids for live private messages (the legacy console path keeps no rows, chat logs are wiped), kept just long
 * enough for the sender to edit or delete them. Both participants learn the id: the recipient inside the
 * FriendChatMessage packet, the sender through MessengerMessageIdComposer.
 */
public final class MessengerLiveMessages {
    public static final long EDIT_WINDOW_MS = 5 * 60 * 1000L;
    private static final int MAX_ENTRIES = 5000;
    private static final AtomicInteger NEXT_ID = new AtomicInteger(1);

    public record Entry(int messageId, int fromId, int toId, long sentAt) {
    }

    private static final LinkedHashMap<Integer, Entry> ENTRIES = new LinkedHashMap<>();

    private MessengerLiveMessages() {
    }

    public static int register(int fromId, int toId) {
        int id = NEXT_ID.getAndIncrement();

        synchronized (ENTRIES) {
            ENTRIES.put(id, new Entry(id, fromId, toId, System.currentTimeMillis()));

            if (ENTRIES.size() > MAX_ENTRIES) {
                Iterator<Integer> iterator = ENTRIES.keySet().iterator();
                iterator.next();
                iterator.remove();
            }
        }

        return id;
    }

    public static Entry get(int messageId) {
        synchronized (ENTRIES) {
            return ENTRIES.get(messageId);
        }
    }

    public static void remove(int messageId) {
        synchronized (ENTRIES) {
            ENTRIES.remove(messageId);
        }
    }

    public static boolean isWithinEditWindow(Entry entry) {
        return entry != null && (System.currentTimeMillis() - entry.sentAt()) <= EDIT_WINDOW_MS;
    }
}
