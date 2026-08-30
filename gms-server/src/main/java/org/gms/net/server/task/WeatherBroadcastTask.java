package org.gms.net.server.task;

import org.gms.server.weather.WeatherPackets;

/** Corrects custom-client weather and clock drift once per minute. */
public final class WeatherBroadcastTask implements Runnable {
    @Override public void run() { WeatherPackets.broadcastAll(); }
}
