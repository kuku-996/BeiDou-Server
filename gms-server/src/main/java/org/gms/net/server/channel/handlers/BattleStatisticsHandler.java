package org.gms.net.server.channel.handlers;

import org.gms.client.Client;
import org.gms.net.AbstractPacketHandler;
import org.gms.net.packet.InPacket;
import org.gms.server.BattleStatisticsService;

/** Handles the Kaentake combat-statistics open/start/stop buttons. */
public final class BattleStatisticsHandler extends AbstractPacketHandler {
    @Override
    public void handlePacket(InPacket packet, Client client) {
        if (client.getPlayer() == null || packet.available() != 1) return;
        switch (packet.readByte() & 0xFF) {
            case 0 -> client.sendPacket(BattleStatisticsService.packet(
                    BattleStatisticsService.snapshot(client.getPlayer())));
            case 1 -> BattleStatisticsService.start(client.getPlayer());
            case 2 -> BattleStatisticsService.stop(client.getPlayer());
            default -> {
            }
        }
    }
}
