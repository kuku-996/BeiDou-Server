package org.gms.net.server.channel.handlers;

import org.gms.client.Client;
import org.gms.net.AbstractPacketHandler;
import org.gms.net.packet.InPacket;
import org.gms.server.maps.HyperTeleportRockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Receives Kaentake opcode 0x105 with exactly one target map id.
 */
public final class HyperTeleportRockHandler extends AbstractPacketHandler {
    private static final Logger log =
            LoggerFactory.getLogger(HyperTeleportRockHandler.class);

    @Override
    public void handlePacket(InPacket p, Client c) {
        if (p.available() != Integer.BYTES) {
            log.warn("[HyperTeleportRock] rejected malformed packet: bytes={}",
                    p.available());
            c.enableActions();
            return;
        }

        int targetMapId = p.readInt();
        if (!HyperTeleportRockService.tryTeleport(c, targetMapId)) {
            c.enableActions();
        }
    }
}
