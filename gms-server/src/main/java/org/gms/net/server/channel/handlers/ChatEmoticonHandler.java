package org.gms.net.server.channel.handlers;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.gms.client.Character;
import org.gms.client.Client;
import org.gms.net.AbstractPacketHandler;
import org.gms.net.packet.InPacket;
import org.gms.util.PacketCreator;

/** Validates and broadcasts Kaentake animated chat emoticons. */
public final class ChatEmoticonHandler extends AbstractPacketHandler {
    private static final long COOLDOWN_MILLIS = 1500L;
    private static final ConcurrentMap<Integer, Long> LAST_USE = new ConcurrentHashMap<>();

    @Override
    public void handlePacket(InPacket packet, Client client) {
        final Character player = client.getPlayer();
        if (player == null || player.getMap() == null || packet.available() != Integer.BYTES) {
            return;
        }

        final int emoticonId = packet.readInt();
        if (!isSupported(emoticonId)) {
            return;
        }

        final long now = System.currentTimeMillis();
        final Long previous = LAST_USE.put(player.getId(), now);
        if (previous != null && now - previous < COOLDOWN_MILLIS) {
            return;
        }

        player.getMap().broadcastMessage(
                player,
                PacketCreator.chatEmoticon(player.getId(), emoticonId),
                true);
    }

    private static boolean isSupported(int id) {
        return inRange(id, 100001, 100010)
                || inRange(id, 101001, 101010)
                || inRange(id, 102001, 102024)
                || inRange(id, 103001, 103007)
                || inRange(id, 104001, 104005)
                || inRange(id, 105001, 105004)
                || inRange(id, 106001, 106006)
                || inRange(id, 107001, 107006)
                || inRange(id, 108000, 108027)
                || inRange(id, 200001, 200005)
                || inRange(id, 201001, 201004)
                || inRange(id, 202001, 202004)
                || inRange(id, 203001, 203006)
                || inRange(id, 204001, 204006)
                || inRange(id, 205001, 205006)
                || inRange(id, 206001, 206006)
                || inRange(id, 207001, 207006)
                || inRange(id, 208000, 208003)
                || inRange(id, 300001, 300120)
                || inRange(id, 301000, 301002);
    }

    private static boolean inRange(int value, int first, int last) {
        return value >= first && value <= last;
    }
}
