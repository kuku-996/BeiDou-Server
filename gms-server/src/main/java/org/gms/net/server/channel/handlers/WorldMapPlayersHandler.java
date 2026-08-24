package org.gms.net.server.channel.handlers;

import org.gms.client.Character;
import org.gms.client.Client;
import org.gms.net.AbstractPacketHandler;
import org.gms.net.opcodes.SendOpcode;
import org.gms.net.packet.InPacket;
import org.gms.net.packet.OutPacket;
import org.gms.net.server.channel.Channel;
import org.gms.net.server.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Returns the players currently in a world-map location across all channels.
 *
 * Request 0x115: int mapId
 * Reply   0x178: int mapId, short count, then name/level/channel entries
 */
public final class WorldMapPlayersHandler extends AbstractPacketHandler {

    private static final int MAX_PLAYERS = 40;
    private static final long CACHE_TTL_MS = 750L;
    private static final ConcurrentHashMap<String, CacheEntry> CACHE = new ConcurrentHashMap<>();

    private record Found(String name, int level, int channel) {
    }

    private record CacheEntry(long expiresAt, List<Found> found) {
    }

    @Override
    public void handlePacket(InPacket p, Client c) {
        int mapId = p.readInt();
        Character self = c.getPlayer();
        if (self == null || mapId <= 0) {
            return;
        }

        World world = c.getWorldServer();
        if (world == null) {
            return;
        }

        boolean requesterIsGM = self.isGM();
        long now = System.currentTimeMillis();
        String cacheKey = world.getId() + ":" + mapId + ":" + (requesterIsGM ? 1 : 0);
        CacheEntry cached = CACHE.get(cacheKey);
        List<Found> found;

        if (cached != null && now < cached.expiresAt()) {
            found = cached.found();
        } else {
            found = collectPlayers(world, mapId, requesterIsGM);
            CACHE.entrySet().removeIf(entry -> now >= entry.getValue().expiresAt());
            CACHE.put(cacheKey, new CacheEntry(now + CACHE_TTL_MS, List.copyOf(found)));
        }

        OutPacket out = OutPacket.create(SendOpcode.WORLD_MAP_PLAYERS);
        out.writeInt(mapId);
        out.writeShort(found.size());
        for (Found player : found) {
            out.writeString(player.name());
            out.writeShort(player.level());
            out.writeByte(player.channel());
        }
        c.sendPacket(out);
    }

    private static List<Found> collectPlayers(World world, int mapId, boolean requesterIsGM) {
        List<Found> found = new ArrayList<>();
        try {
            outer:
            for (Channel channel : world.getChannels()) {
                for (Character player : channel.getPlayerStorage().getAllCharacters()) {
                    if (player == null || player.getMapId() != mapId) {
                        continue;
                    }
                    if (player.isHidden() && !requesterIsGM) {
                        continue;
                    }

                    found.add(new Found(player.getName(), player.getLevel(), channel.getId()));
                    if (found.size() >= MAX_PLAYERS) {
                        break outer;
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // A channel can change while the informational roster is being collected.
        }
        return found;
    }
}
