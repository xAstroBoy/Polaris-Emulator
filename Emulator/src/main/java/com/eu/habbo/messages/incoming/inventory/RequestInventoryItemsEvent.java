package com.eu.habbo.messages.incoming.inventory;

import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.inventory.InventoryItemsComposer;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;

public class RequestInventoryItemsEvent extends MessageHandler {
    private static final int ITEMS_PER_FRAGMENT = 1000;

    @Override
    public int getRatelimit() {
        return 500;
    }

    /**
     * The fragment count and the fragments themselves have to describe the same inventory. The size used to be read
     * outside the lock, so a catalog purchase landing in between made the header promise two fragments while only one
     * was sent - the client renders nothing until every fragment arrives, so the inventory stayed empty until the next
     * login. Everything is now counted from one snapshot taken under the lock.
     */
    @Override
    public void handle() throws Exception {
        Int2ObjectMap<HabboItem> inventory = this.client.getHabbo().getInventory().getItemsComponent().getItems();
        List<HabboItem> snapshot;

        synchronized (inventory) {
            snapshot = new ArrayList<>(inventory.values());
        }

        if (snapshot.isEmpty()) {
            // Fragment 1 of 1: the composer subtracts one, so the client reads fragment 0 of 1 and stops waiting.
            this.client.sendResponse(new InventoryItemsComposer(1, 1, new Int2ObjectOpenHashMap<>()));
            return;
        }

        int totalFragments = (snapshot.size() + ITEMS_PER_FRAGMENT - 1) / ITEMS_PER_FRAGMENT;
        int fragmentNumber = 0;
        Int2ObjectMap<HabboItem> items = new Int2ObjectOpenHashMap<>();

        for (HabboItem item : snapshot) {
            items.put(item.getId(), item);

            if (items.size() == ITEMS_PER_FRAGMENT) {
                this.client.sendResponse(new InventoryItemsComposer(++fragmentNumber, totalFragments, items));
                items = new Int2ObjectOpenHashMap<>();
            }
        }

        if (!items.isEmpty()) {
            this.client.sendResponse(new InventoryItemsComposer(++fragmentNumber, totalFragments, items));
        }
    }
}
