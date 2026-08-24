package org.gms.net.server.channel.handlers;

import org.gms.client.Client;
import org.gms.net.AbstractPacketHandler;
import org.gms.net.packet.InPacket;
import org.gms.server.MonsterBookQueryService;
import org.gms.util.PacketCreator;

import java.util.LinkedHashMap;
import java.util.Map;

/** Handles Kaentake's read-only extended Monster Book queries (0x372B). */
public final class MonsterBookQueryHandler extends AbstractPacketHandler {
    private static final int MAX_RESULTS = 200;

    @Override
    public void handlePacket(InPacket packet, Client client) {
        if (client.getPlayer() == null || packet.available() < 1) {
            return;
        }

        switch (packet.readUnsignedByte()) {
            case 0 -> {
                if (packet.available() != Integer.BYTES) return;
                int mobId = packet.readInt();
                client.sendPacket(PacketCreator.monsterBookDropTable(mobId,
                        cap(MonsterBookQueryService.mobDropChances(client.getPlayer(), mobId))));
            }
            case 1 -> {
                if (packet.available() < Short.BYTES) return;
                String query = packet.readString();
                if (packet.available() != 0) return;
                int[] hits = MonsterBookQueryService.findBookItems(query);
                if (hits.length > MAX_RESULTS) {
                    int[] capped = new int[MAX_RESULTS];
                    System.arraycopy(hits, 0, capped, 0, capped.length);
                    hits = capped;
                }
                client.sendPacket(PacketCreator.monsterBookItemHits(query, hits));
            }
            case 2 -> {
                if (packet.available() != Integer.BYTES) return;
                int itemId = packet.readInt();
                client.sendPacket(PacketCreator.monsterBookItemDroppers(itemId,
                        cap(MonsterBookQueryService.itemDroppers(client.getPlayer(), itemId))));
            }
            default -> {
            }
        }
    }

    private static LinkedHashMap<Integer, Integer> cap(LinkedHashMap<Integer, Integer> values) {
        if (values.size() <= MAX_RESULTS) return values;
        LinkedHashMap<Integer, Integer> result = new LinkedHashMap<>();
        for (Map.Entry<Integer, Integer> entry : values.entrySet()) {
            if (result.size() == MAX_RESULTS) break;
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }
}
