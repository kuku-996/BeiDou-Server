package org.gms.net.server.channel.handlers;

import org.gms.client.Character;
import org.gms.client.Client;
import org.gms.net.AbstractPacketHandler;
import org.gms.net.packet.InPacket;
import org.gms.server.DamageSkinService;
import org.gms.util.PacketCreator;

public final class DamageSkinPurchaseHandler extends AbstractPacketHandler {
    private static final int OP_PURCHASE = 2;

    @Override
    public void handlePacket(InPacket p, Client c) {
        Character player = c.getPlayer();
        if (player == null || p.available() != Integer.BYTES) {
            c.enableActions();
            return;
        }

        int skinId = p.readInt();
        boolean success = DamageSkinService.purchase(player, skinId);
        c.sendPacket(PacketCreator.damageSkinResult(
                OP_PURCHASE, success, skinId, player.getMeso()));
        if (success) {
            c.sendPacket(PacketCreator.damageSkinInventory(
                    DamageSkinService.getActive(player.getId()),
                    DamageSkinService.getOwned(player.getId())));
        }
    }
}
