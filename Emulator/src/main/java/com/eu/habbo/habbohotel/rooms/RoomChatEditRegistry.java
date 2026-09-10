package com.eu.habbo.habbohotel.rooms;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Remembers who said which room chat message (by the id RoomChatMessage serialises) so a later edit or delete can
 * be checked against the author and the age. Bounded: the oldest entries fall off once the cap is reached, and
 * an entry older than the edit window is refused anyway.
 */
public final class RoomChatEditRegistry {
    public static final long EDIT_WINDOW_MS = 5 * 60 * 1000L;
    private static final int MAX_ENTRIES = 5000;
    private static final AtomicInteger NEXT_ID = new AtomicInteger(1);

    public record Entry(int messageId, int habboId, int roomUnitId, int roomId, long sentAt) {
    }

    private static final LinkedHashMap<Integer, Entry> ENTRIES = new LinkedHashMap<>();

    private RoomChatEditRegistry() {
    }

    public static int nextId() {
        return NEXT_ID.getAndIncrement();
    }

    public static void register(int messageId, int habboId, int roomUnitId, int roomId) {
        if (messageId <= 0) return;

        synchronized (ENTRIES) {
            ENTRIES.put(messageId, new Entry(messageId, habboId, roomUnitId, roomId, System.currentTimeMillis()));

            if (ENTRIES.size() > MAX_ENTRIES) {
                Iterator<Integer> iterator = ENTRIES.keySet().iterator();
                iterator.next();
                iterator.remove();
            }
        }
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
