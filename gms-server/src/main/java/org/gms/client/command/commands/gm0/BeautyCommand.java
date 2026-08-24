package org.gms.client.command.commands.gm0;

import org.gms.client.Client;
import org.gms.client.command.Command;
import org.gms.server.beauty.BeautyPackets;

public final class BeautyCommand extends Command {
    {
        setDescription("打开美容院窗口。");
    }

    @Override
    public void execute(Client client, String[] params) {
        if (client.getPlayer() != null) {
            client.sendPacket(BeautyPackets.beautyOpen());
        }
    }
}
