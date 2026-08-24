package org.gms.net.server.channel.handlers;

import org.gms.client.Character;
import org.gms.client.Client;
import org.gms.net.AbstractPacketHandler;
import org.gms.net.packet.InPacket;
import org.gms.server.setitem.SetItemPackets;

/** Handles Kaentake set catalog and equipped-piece requests. */
public final class SetItemHandler extends AbstractPacketHandler {
    private static final int REQUEST_CATALOG = 1;
    private static final int REQUEST_PIECE_COUNTS = 2;

    @Override
    public void handlePacket(InPacket p, Client c) {
        Character player = c.getPlayer();
        if (player == null || p.available() != 1) {
            return;
        }
        int action = p.readByte() & 0xFF;
        if (action == REQUEST_CATALOG) {
            c.sendPacket(SetItemPackets.catalog());
        } else if (action == REQUEST_PIECE_COUNTS) {
            c.sendPacket(SetItemPackets.pieceCounts(player));
        }
    }
}
