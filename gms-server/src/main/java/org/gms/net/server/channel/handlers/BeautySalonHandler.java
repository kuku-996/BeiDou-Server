package org.gms.net.server.channel.handlers;

import org.gms.client.Character;
import org.gms.client.Client;
import org.gms.client.SkinColor;
import org.gms.client.Stat;
import org.gms.client.inventory.Inventory;
import org.gms.client.inventory.InventoryType;
import org.gms.client.inventory.Item;
import org.gms.client.inventory.manipulator.InventoryManipulator;
import org.gms.net.AbstractPacketHandler;
import org.gms.net.packet.InPacket;
import org.gms.server.beauty.BeautyData;
import org.gms.server.beauty.BeautyPackets;
import org.gms.server.beauty.BeautyStorage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class BeautySalonHandler extends AbstractPacketHandler {
    private static final int ACTION_REQUEST = 0;
    private static final int ACTION_SAVE = 1;
    private static final int ACTION_APPLY = 2;
    private static final int ACTION_DELETE = 3;
    private static final int ACTION_UNLOCK = 4;
    private static final int TYPE_HAIR = 0;
    private static final int TYPE_FACE = 1;
    private static final int TYPE_SKIN = 2;
    private static final int SLOT_COUNT = 6;
    private static final int BEAUTY_ITEM_ID = 5920000;
    private static final Map<Integer, Object> CHARACTER_LOCKS =
            new ConcurrentHashMap<>();

    @Override
    public void handlePacket(InPacket p, Client c) {
        Character chr = c.getPlayer();
        if (chr == null || p.available() < 1) {
            return;
        }

        int action = p.readByte() & 0xFF;
        if (action == ACTION_REQUEST && p.available() == 0) {
            sendData(c, chr);
        } else if ((action == ACTION_SAVE || action == ACTION_APPLY
                || action == ACTION_DELETE) && p.available() == 2) {
            int slot = p.readByte() & 0xFF;
            int type = p.readByte() & 0xFF;
            if (action == ACTION_SAVE) {
                handleSave(c, chr, slot, type);
            } else if (action == ACTION_APPLY) {
                handleApply(c, chr, slot, type);
            } else {
                handleDelete(c, chr, slot, type);
            }
        } else if (action == ACTION_UNLOCK && p.available() == 2) {
            handleUnlock(c, chr, p.readShort());
        }
    }

    private void sendData(Client c, Character chr) {
        c.sendPacket(BeautyPackets.beautyData(
                BeautyStorage.getUnlockedSlots(chr.getId()),
                BeautyStorage.loadAll(chr.getId())));
    }

    private void handleUnlock(Client c, Character chr, int itemPos) {
        Object lock = CHARACTER_LOCKS.computeIfAbsent(
                chr.getId(), ignored -> new Object());
        synchronized (lock) {
            int unlocked = BeautyStorage.getUnlockedSlots(chr.getId());
            Item item = chr.getInventory(InventoryType.CASH)
                    .getItem((short) itemPos);
            if (unlocked < SLOT_COUNT && item != null
                    && item.getItemId() == BEAUTY_ITEM_ID
                    && item.getQuantity() > 0
                    && BeautyStorage.setUnlockedSlots(
                            chr.getId(), unlocked + 1)) {
                InventoryManipulator.removeFromSlot(
                        c, InventoryType.CASH, (short) itemPos,
                        (short) 1, false);
            }
        }
        sendData(c, chr);
    }

    private void handleSave(
            Client c, Character chr, int slot, int type) {
        if (!isAccessible(chr, slot, type)) {
            return;
        }
        Inventory equipped = chr.getInventory(InventoryType.EQUIPPED);
        BeautyData data = new BeautyData(
                chr.getId(), slot, type, chr.getGender(),
                chr.getSkinColor().getId(), chr.getHair(), chr.getFace(),
                equippedId(equipped, -1), equippedId(equipped, -5),
                equippedId(equipped, -6), equippedId(equipped, -7),
                equippedId(equipped, -11), equippedId(equipped, -111));
        if (BeautyStorage.save(data)) {
            sendData(c, chr);
        }
    }

    private void handleApply(
            Client c, Character chr, int slot, int type) {
        if (!isAccessible(chr, slot, type)) {
            return;
        }
        BeautyData match = BeautyStorage.loadAll(chr.getId()).stream()
                .filter(data -> data.slot() == slot && data.type() == type)
                .findFirst()
                .orElse(null);
        if (match == null) {
            return;
        }
        if (type == TYPE_HAIR) {
            chr.setHair(match.hair());
            chr.updateSingleStat(Stat.HAIR, match.hair());
        } else if (type == TYPE_FACE) {
            chr.setFace(match.face());
            chr.updateSingleStat(Stat.FACE, match.face());
        } else {
            SkinColor skin = SkinColor.getById(match.skin());
            if (skin == null) {
                return;
            }
            chr.setSkinColor(skin);
            chr.updateSingleStat(Stat.SKIN, match.skin());
        }
        chr.equipChanged();
        sendData(c, chr);
    }

    private void handleDelete(
            Client c, Character chr, int slot, int type) {
        if (!isAccessible(chr, slot, type)) {
            return;
        }
        if (BeautyStorage.delete(chr.getId(), slot, type)) {
            sendData(c, chr);
        }
    }

    private static boolean isAccessible(
            Character chr, int slot, int type) {
        return isValid(slot, type)
                && slot < BeautyStorage.getUnlockedSlots(chr.getId());
    }

    private static boolean isValid(int slot, int type) {
        return slot >= 0 && slot < SLOT_COUNT
                && type >= TYPE_HAIR && type <= TYPE_SKIN;
    }

    private static int equippedId(Inventory equipped, int slot) {
        Item item = equipped.getItem((short) slot);
        return item == null ? 0 : item.getItemId();
    }
}
