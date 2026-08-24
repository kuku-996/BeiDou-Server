package org.gms.client;

import org.gms.net.PacketProcessor;

/**
 * Headless client marker used by the SoloMapling bot loader. It reuses the
 * normal BeiDou client routing and deliberately has no network channel.
 */
public final class BotClient extends Client {
    public BotClient(int world, int channel) {
        super(Type.CHANNEL, -1L, "solo-mapling-bot", (PacketProcessor) null, world, channel);
    }
}
