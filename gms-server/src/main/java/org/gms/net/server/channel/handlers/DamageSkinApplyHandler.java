package org.gms.net.server.channel.handlers;

import org.gms.client.Character;
import org.gms.client.Client;
import org.gms.net.AbstractPacketHandler;
import org.gms.net.packet.InPacket;
import org.gms.server.DamageSkinService;
import org.gms.util.PacketCreator;

public final class DamageSkinApplyHandler extends AbstractPacketHandler {
    private static final int OP_APPLY = 1;

    @Override
    public void handlePacket(InPacket p, Client c) {
        Character player = c.getPlayer();
        if (player == null || p.available() != Integer.BYTES) {
            c.enableActions();
            return;
        }

        int skinId = p.readInt();
        boolean success = DamageSkinService.apply(player.getId(), skinId);
        c.sendPacket(PacketCreator.damageSkinResult(
                OP_APPLY, success, skinId, player.getMeso()));
        if (success && player.getMap() != null) {
            player.getMap().broadcastMessage(
                    player,
                    PacketCreator.damageSkinBroadcast(player.getId(), skinId),
                    true);
        }
    }
}
