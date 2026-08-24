/*
	This file is part of the OdinMS Maple Story Server
    Copyright (C) 2008 Patrick Huy <patrick.huy@frz.cc>
		       Matthias Butz <matze@odinms.de>
		       Jan Christian Meyer <vimes@odinms.de>

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as
    published by the Free Software Foundation version 3 as published by
    the Free Software Foundation. You may not use, modify or distribute
    this program under any other version of the GNU Affero General Public
    License.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package org.gms.net.server.channel.handlers;

import org.gms.client.Client;
import org.gms.client.inventory.InventoryType;
import org.gms.constants.id.ItemId;
import org.gms.net.AbstractPacketHandler;
import org.gms.net.packet.InPacket;
import org.gms.server.TimerManager;

public final class UseChairHandler extends AbstractPacketHandler {
    @Override
    public final void handlePacket(InPacket p, Client c) {
        int itemId = p.readInt();
        final var player = c.getPlayer();
        if (player == null) {
            return;
        }

        // Always release the client action lock when validation rejects a
        // chair request.  Without this reply, expanded/newer chair IDs leave
        // the client unable to move or open any UI until @dispose is used.
        if (!ItemId.isChair(itemId)
                || player.getInventory(InventoryType.SETUP).findById(itemId) == null) {
            player.enableActions();
            return;
        }

        if (!c.tryacquireClient()) {
            // A concurrent action must never strand the client in its local
            // sitting state while the request waits for the next input.
            TimerManager.getInstance().schedule(player::enableActions, 50);
            return;
        }

        try {
            player.sitChair(itemId);

            /*
             * Some post-v83 chairs finish their local mount/body-offset
             * animation after the normal ENABLE_ACTIONS packet has been
             * processed, and lock input again.  Re-send the packet after
             * the animation hand-off, but only while this exact chair is
             * still active so a later map change or chair switch is not
             * affected.
             */
            TimerManager.getInstance().schedule(() -> {
                if (c.getPlayer() == player && player.isLoggedInWorld()
                        && player.getChair() == itemId) {
                    player.enableActions();
                }
            }, 900);
        } finally {
            // sitChair normally sends this itself.  Keep the handler-level
            // reply as a final guard for any future data/validation change.
            player.enableActions();
            c.releaseClient();
        }
    }
}
