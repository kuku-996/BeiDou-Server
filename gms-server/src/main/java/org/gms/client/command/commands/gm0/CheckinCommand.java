package org.gms.client.command.commands.gm0;

import org.gms.client.Character;
import org.gms.client.Client;
import org.gms.client.command.Command;
import org.gms.server.DailyCheckinConfigService;
import org.gms.server.DailyCheckinService;

/** Opens the daily check-in window through @daily or @checkin. */
public final class CheckinCommand extends Command {
    {
        setDescription("打开每日签到窗口。");
    }

    @Override
    public void execute(Client client, String[] params) {
        Character player = client.getPlayer();
        if (!DailyCheckinConfigService.isEnabled()) {
            player.dropMessage(6, "每日签到功能当前未开放。");
            return;
        }
        if (!DailyCheckinService.eligible(player)) {
            player.dropMessage(6, "每日签到将在角色达到 "
                    + DailyCheckinConfigService.minLevel() + " 级后开放。");
            return;
        }

        DailyCheckinService.Snapshot snapshot = DailyCheckinService.snapshot(player);
        client.sendPacket(DailyCheckinService.packet(snapshot, 0));
        if (snapshot.claimableDay() == 0) {
            long hours = snapshot.cooldownSeconds() / 3600;
            long minutes = (snapshot.cooldownSeconds() % 3600) / 60;
            player.dropMessage(6, "下一次签到奖励将在 " + hours + " 小时 " + minutes + " 分钟后开放。");
        }
    }
}
