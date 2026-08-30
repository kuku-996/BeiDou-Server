package org.gms.server.buyback;

import org.gms.client.Character;
import org.gms.client.Client;
import org.gms.client.inventory.Item;
import org.gms.client.inventory.manipulator.InventoryManipulator;
import org.gms.util.PacketCreator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Session-only items sold to an NPC shop.
 *
 * The client supplies only the row index and the item id shown in that row.  The
 * price, quantity and item instance always come from this server-side list.
 */
public final class SoldItemStorage {
    private static final Logger log = LoggerFactory.getLogger(SoldItemStorage.class);

    public static final int BUYBACK_PRICE = 5_000_000;
    private static final int MAX_ENTRIES = 30;
    private static final SoldItemStorage INSTANCE = new SoldItemStorage();

    private final Lock lock = new ReentrantLock();
    private final Map<Integer, List<Item>> soldItems = new HashMap<>();

    public static SoldItemStorage getInstance() {
        return INSTANCE;
    }

    public void addSoldItem(int characterId, Item item) {
        if (item == null || item.getQuantity() <= 0 || item.getPetId() >= 0) {
            return;
        }
        lock.lock();
        try {
            List<Item> items = soldItems.computeIfAbsent(characterId, ignored -> new ArrayList<>());
            items.add(item);
            while (items.size() > MAX_ENTRIES) {
                items.remove(0);
            }
        } finally {
            lock.unlock();
        }
    }

    public List<Item> getSoldItems(int characterId) {
        lock.lock();
        try {
            List<Item> items = soldItems.get(characterId);
            return items == null ? Collections.emptyList() : new ArrayList<>(items);
        } finally {
            lock.unlock();
        }
    }

    public void clear(int characterId) {
        lock.lock();
        try {
            soldItems.remove(characterId);
        } finally {
            lock.unlock();
        }
    }

    public void sendBuybackShop(Client c) {
        Character chr = c.getPlayer();
        if (chr == null || chr.getShop() == null) {
            return;
        }
        List<Item> items = getSoldItems(chr.getId());
        chr.setShopBuybackMode(true);
        c.sendPacket(PacketCreator.shopBuybackMode(true, !items.isEmpty()));
        c.sendPacket(PacketCreator.getBuybackShop(c, chr.getShop().getNpcId(), items, BUYBACK_PRICE));
        log.debug("Sent {} buyback item(s) to {}", items.size(), chr.getName());
    }

    public void sendNormalShop(Client c) {
        Character chr = c.getPlayer();
        if (chr != null && chr.getShop() != null) {
            chr.getShop().sendShop(c);
        }
    }

    public void buyBackFromShop(Client c, short index, int expectedItemId) {
        Character chr = c.getPlayer();
        if (chr == null) {
            return;
        }

        String failure = buyBack(c, index, expectedItemId);
        if (failure != null) {
            chr.dropMessage(1, failure);
            c.sendPacket(PacketCreator.shopTransaction((byte) 8));
            return;
        }

        c.sendPacket(PacketCreator.shopTransaction((byte) 0));
        refreshBuybackShop(c);
    }

    public void refreshBuybackShop(Client c) {
        Character chr = c.getPlayer();
        if (chr == null || chr.getShop() == null || !chr.isShopBuybackMode()) {
            return;
        }
        sendBuybackShop(c);
    }

    public void refreshBuybackTab(Client c) {
        Character chr = c.getPlayer();
        if (chr == null || chr.getShop() == null || chr.isShopBuybackMode()) {
            return;
        }
        boolean hasItems = !getSoldItems(chr.getId()).isEmpty();
        c.sendPacket(PacketCreator.shopBuybackMode(false, hasItems));
    }

    private String buyBack(Client c, short index, int expectedItemId) {
        Character chr = c.getPlayer();
        if (!chr.isLoggedin()) {
            return "当前无法回购物品。";
        }
        if (chr.getTrade() != null || chr.getPlayerShop() != null
                || chr.getHiredMerchant() != null || chr.getMiniGame() != null) {
            return "当前无法回购物品。";
        }

        lock.lock();
        try {
            List<Item> items = soldItems.get(chr.getId());
            if (items == null || index < 0 || index >= items.size()) {
                return "该物品已无法回购。";
            }

            Item stored = items.get(index);
            if (stored.getItemId() != expectedItemId || stored.getQuantity() <= 0) {
                return "该物品已无法回购。";
            }
            short quantity = stored.getQuantity();
            String owner = stored.getOwner() == null ? "" : stored.getOwner();

            if (chr.getMeso() < BUYBACK_PRICE) {
                return "金币不足，需要 " + BUYBACK_PRICE + " 金币。";
            }
            if (!InventoryManipulator.checkSpace(c, stored.getItemId(), quantity, owner)) {
                return "背包空间不足。";
            }

            int mesoBefore = chr.getMeso();
            chr.gainMeso(-BUYBACK_PRICE, false);
            int paid = mesoBefore - chr.getMeso();
            if (paid != BUYBACK_PRICE) {
                if (paid > 0) {
                    chr.gainMeso(paid, false);
                }
                return "金币发生变化，请稍后再试。";
            }

            Item copy = stored.copy();
            copy.setOwner(owner);
            if (!InventoryManipulator.addFromDrop(c, copy, false)) {
                chr.gainMeso(BUYBACK_PRICE, false);
                int left = Math.max(0, copy.getQuantity());
                int delivered = Math.max(0, quantity - left);
                if (delivered >= quantity) {
                    items.remove(index);
                } else if (delivered > 0) {
                    stored.setQuantity((short) (quantity - delivered));
                }
                return "背包空间不足。";
            }

            items.remove(index);
            return null;
        } finally {
            lock.unlock();
        }
    }
}
