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

import org.gms.client.Character;
import org.gms.client.Client;
import org.gms.net.AbstractPacketHandler;
import org.gms.net.packet.InPacket;

public final class CancelChairHandler extends AbstractPacketHandler {

    @Override
    public final void handlePacket(InPacket p, Client c) {
        int id = p.readShort();
        Character mc = c.getPlayer();

        /*
         * GMS083 clients do not use a single, consistent value when they
         * leave an inventory chair.  Most send -1, but several chairs send
         * a zero seat id.  Treating that zero as a map-seat request leaves
         * the inventory chair active server-side and the client stays action
         * locked until a manual @dispose sends ENABLE_ACTIONS.
         *
         * A real map seat is only valid while the player is not already using
         * an inventory chair.  Therefore an incoming seat id for a character
         * sitting on an item chair must always mean "stand up".
         */
        boolean isSittingOnItemChair = mc.getChair() >= 1_000_000;
        if (isSittingOnItemChair && id >= 0) {
            if (c.tryacquireClient()) {
                try {
                    mc.sitChair(-1);
                    mc.enableActions();
                } finally {
                    c.releaseClient();
                }
            }
            return;
        }

        if (id >= mc.getMap().getSeats()) {
            return;
        }

        if (c.tryacquireClient()) {
            try {
                mc.sitChair(id);
                // Keep the client input state in sync after both item and map chairs.
                mc.enableActions();
            } finally {
                c.releaseClient();
            }
        }
    }
}
