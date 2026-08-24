package org.gms.server.beauty;

import org.gms.net.opcodes.SendOpcode;
import org.gms.net.packet.OutPacket;
import org.gms.net.packet.Packet;

import java.util.List;

public final class BeautyPackets {
    private static final int SLOT_COUNT = 6;
    private static final int TYPE_HAIR = 0;
    private static final int TYPE_FACE = 1;
    private static final int TYPE_SKIN = 2;
    private static final int RESP_OPEN = 0;
    private static final int RESP_DATA = 1;

    private BeautyPackets() {
    }

    public static Packet beautyOpen() {
        OutPacket p = OutPacket.create(SendOpcode.BEAUTY_SALON);
        p.writeByte(RESP_OPEN);
        return p;
    }

    public static Packet beautyData(
            int unlockedSlots,
            List<BeautyData> sourceRows) {
        List<BeautyData> rows = sourceRows.stream()
                .filter(BeautyPackets::isValidRow)
                .limit(SLOT_COUNT * 3L)
                .toList();
        int[] hairId = new int[SLOT_COUNT];
        int[] faceId = new int[SLOT_COUNT];
        int[] skinId = new int[SLOT_COUNT];
        boolean[] hairSaved = new boolean[SLOT_COUNT];
        boolean[] faceSaved = new boolean[SLOT_COUNT];
        boolean[] skinSaved = new boolean[SLOT_COUNT];

        for (BeautyData data : rows) {
            if (data.type() == TYPE_HAIR) {
                hairId[data.slot()] = data.hair();
                hairSaved[data.slot()] = true;
            } else if (data.type() == TYPE_FACE) {
                faceId[data.slot()] = data.face();
                faceSaved[data.slot()] = true;
            } else if (data.type() == TYPE_SKIN) {
                skinId[data.slot()] = data.skin();
                skinSaved[data.slot()] = true;
            }
        }

        OutPacket p = OutPacket.create(SendOpcode.BEAUTY_SALON);
        p.writeByte(RESP_DATA);
        p.writeByte(Math.max(0, Math.min(SLOT_COUNT, unlockedSlots)));
        writeIds(p, hairId);
        writeIds(p, faceId);
        writeIds(p, skinId);
        writeFlags(p, hairSaved);
        writeFlags(p, faceSaved);
        writeFlags(p, skinSaved);

        p.writeByte(rows.size());
        for (BeautyData data : rows) {
            p.writeByte(data.type());
            p.writeByte(data.slot());
            p.writeByte(data.gender());
            p.writeInt(data.skin());
            p.writeInt(data.hair());
            p.writeInt(data.face());
            p.writeInt(data.hat());
            p.writeInt(data.top());
            p.writeInt(data.bottom());
            p.writeInt(data.shoes());
            p.writeInt(data.weapon());
            p.writeInt(data.cashWeapon());
        }
        return p;
    }

    private static void writeIds(OutPacket p, int[] ids) {
        p.writeByte(SLOT_COUNT);
        for (int id : ids) {
            p.writeInt(id);
        }
    }

    private static void writeFlags(OutPacket p, boolean[] flags) {
        p.writeByte(SLOT_COUNT);
        for (boolean flag : flags) {
            p.writeByte(flag ? 1 : 0);
        }
    }

    private static boolean isValidRow(BeautyData data) {
        return data.slot() >= 0 && data.slot() < SLOT_COUNT
                && data.type() >= TYPE_HAIR && data.type() <= TYPE_SKIN;
    }
}
