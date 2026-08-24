package org.gms.server.setitem;

import org.gms.client.Character;
import org.gms.net.opcodes.SendOpcode;
import org.gms.net.packet.OutPacket;
import org.gms.net.packet.Packet;
import org.gms.server.ItemInformationProvider;

import java.util.Map;
import java.util.Set;

/** Packet encoder shared by the set catalog and live equipped-piece sync. */
public final class SetItemPackets {
    private static final int TYPE_CATALOG = 1;
    private static final int TYPE_PIECE_COUNTS = 2;

    private SetItemPackets() {
    }

    public static Packet catalog() {
        OutPacket p = OutPacket.create(SendOpcode.SET_ITEM_SYNC);
        p.writeByte(TYPE_CATALOG);
        Map<Integer, ItemSetInfo> sets = ItemSetInfoProvider.getInstance().getAllSets();
        p.writeShort(sets.size());
        ItemInformationProvider items = ItemInformationProvider.getInstance();
        for (ItemSetInfo set : sets.values()) {
            p.writeInt(set.getSetId());
            p.writeString(set.getName());
            p.writeShort(set.getItemIds().size());
            for (int itemId : set.getItemIds()) {
                p.writeInt(itemId);
                String itemName = items.getName(itemId);
                p.writeString(itemName != null ? itemName : ("Item " + itemId));
            }
            p.writeShort(set.getTierEffects().size());
            for (Map.Entry<Integer, Map<String, Integer>> tier : set.getTierEffects().entrySet()) {
                p.writeShort(tier.getKey());
                p.writeShort(tier.getValue().size());
                for (Map.Entry<String, Integer> stat : tier.getValue().entrySet()) {
                    p.writeString(stat.getKey());
                    p.writeInt(stat.getValue());
                }
            }
        }
        return p;
    }

    public static Packet pieceCounts(Character player) {
        OutPacket p = OutPacket.create(SendOpcode.SET_ITEM_SYNC);
        p.writeByte(TYPE_PIECE_COUNTS);
        Map<Integer, Set<Integer>> equipped =
                ItemSetInfoProvider.getInstance().getEquippedSetItemIds(player);
        p.writeShort(equipped.size());
        for (Map.Entry<Integer, Set<Integer>> set : equipped.entrySet()) {
            p.writeInt(set.getKey());
            p.writeShort(set.getValue().size());
            for (int itemId : set.getValue()) {
                p.writeInt(itemId);
            }
        }

        Map<String, Integer> bonus = player.getActiveSetBonusStats();
        int allStat = bonus.getOrDefault("incAllStat", 0);
        p.writeInt(bonus.getOrDefault("incSTR", 0) + allStat);
        p.writeInt(bonus.getOrDefault("incDEX", 0) + allStat);
        p.writeInt(bonus.getOrDefault("incINT", 0) + allStat);
        p.writeInt(bonus.getOrDefault("incLUK", 0) + allStat);
        p.writeInt(bonus.getOrDefault("incMHP", 0));
        p.writeInt(bonus.getOrDefault("incMMP", 0));
        p.writeInt(bonus.getOrDefault("incPAD", 0));
        p.writeInt(bonus.getOrDefault("incMAD", 0));
        return p;
    }
}
