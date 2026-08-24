package org.gms.net.server.channel.handlers;

import org.gms.client.Character;
import org.gms.client.Client;
import org.gms.net.AbstractPacketHandler;
import org.gms.net.packet.InPacket;
import org.gms.server.DailyCheckinConfigService;
import org.gms.server.DailyCheckinService;

/** Handles Kaentake daily check-in open/claim requests on opcode 0x11A. */
public final class DailyCheckinHandler extends AbstractPacketHandler {
    private static final int OPEN = 0;
    private static final int CLAIM = 1;

    @Override
    public void handlePacket(InPacket p, Client c) {
        Character player = c.getPlayer();
        if (!DailyCheckinService.eligible(player) || p.available() < 1) {
            if (DailyCheckinConfigService.isEnabled()
                    && player != null && player.getLevel() < DailyCheckinConfigService.minLevel()) {
                player.dropMessage(6, "每日签到将在角色达到 "
                        + DailyCheckinConfigService.minLevel() + " 级后开放。");
            }
            return;
        }

        int action = p.readByte() & 0xFF;
        if (action == OPEN && p.available() == 0) {
            DailyCheckinService.Snapshot snapshot = DailyCheckinService.snapshot(player);
            c.sendPacket(DailyCheckinService.packet(snapshot, 0));
            return;
        }

        if (action == CLAIM && p.available() == 1) {
            int day = p.readByte() & 0xFF;
            DailyCheckinService.ClaimResult result = DailyCheckinService.claim(c, day);
            c.sendPacket(DailyCheckinService.packet(result.snapshot(), result.claimedDay()));
            if (!result.granted()) {
                player.dropMessage(6, "签到失败：尚未到领取时间、日期不正确或背包空间不足。");
            }
        }
    }
}
