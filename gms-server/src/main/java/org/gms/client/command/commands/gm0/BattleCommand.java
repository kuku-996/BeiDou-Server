package org.gms.client.command.commands.gm0;

import org.gms.client.Client;
import org.gms.client.command.Command;
import org.gms.server.BattleStatisticsService;

/** Opens the Kaentake combat statistics panel with @battle. */
public final class BattleCommand extends Command {
    {
        setDescription("打开战斗统计数据窗口。");
    }

    @Override
    public void execute(Client client, String[] params) {
        if (client.getPlayer() != null) {
            client.sendPacket(BattleStatisticsService.packet(
                    BattleStatisticsService.snapshot(client.getPlayer())));
        }
    }
}
