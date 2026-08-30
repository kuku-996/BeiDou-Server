package org.gms.server.weather;

import org.gms.client.Character;
import org.gms.net.opcodes.SendOpcode;
import org.gms.net.packet.OutPacket;
import org.gms.net.packet.Packet;
import org.gms.net.server.Server;
import org.gms.net.server.world.World;

/** Wire format for Kaentake's client-only 0x373D weather state packet. */
public final class WeatherPackets {
    private WeatherPackets() { }

    public static Packet weatherSync(int mapId, boolean snap) {
        OutPacket packet = OutPacket.create(SendOpcode.WEATHER_SYNC);
        packet.writeShort(WeatherService.minuteOfDay());
        packet.writeInt(WeatherService.msPerGameMinute());
        packet.writeByte(WeatherService.skyForMap(mapId));
        packet.writeByte(snap ? 0x01 : 0x00);
        packet.writeInt(WeatherService.skyElapsedMillis() / 1000);
        int tint = WeatherService.tintForMap(mapId);
        packet.writeByte((tint >>> 16) & 0xFF);
        packet.writeByte((tint >>> 8) & 0xFF);
        packet.writeByte(tint & 0xFF);
        packet.writeShort(0);
        packet.writeByte(WeatherService.paletteForMap(mapId));
        packet.writeInt(WeatherService.skyElapsedMillis());
        packet.writeInt((int) (System.currentTimeMillis() / WeatherService.SKY_LENGTH_MS));
        return packet;
    }

    public static void sendTo(Character character) {
        if (character != null && character.getClient() != null) character.sendPacket(weatherSync(character.getMapId(), true));
    }

    public static void broadcastAll() {
        for (World world : Server.getInstance().getWorlds()) {
            if (world == null || world.getPlayerStorage() == null) continue;
            for (Character character : world.getPlayerStorage().getAllCharacters()) {
                if (character != null && character.isLoggedInWorld()) character.sendPacket(weatherSync(character.getMapId(), false));
            }
        }
    }
}
