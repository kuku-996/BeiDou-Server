package org.gms.client.command.commands.gm6;

import org.gms.client.Character;
import org.gms.client.Client;
import org.gms.client.command.Command;
import org.gms.server.weather.WeatherPackets;
import org.gms.server.weather.WeatherService;

/** GM6 visual-weather test command; it never changes combat, drops or monster spawns. */
public final class WeatherCommand extends Command {
    {
        setDescription("天气测试：!weather <day|night|clear|rain|snow|storm|auto|status>");
    }

    @Override
    public void execute(Client client, String[] params) {
        Character player = client.getPlayer();
        if (params.length != 1) {
            player.yellowMessage(getDescription());
            return;
        }
        switch (params[0]) {
            case "day" -> WeatherService.forceTime(12 * 60);
            case "night" -> WeatherService.forceTime(0);
            case "clear" -> WeatherService.forceSky(WeatherService.SKY_CLEAR);
            case "rain" -> WeatherService.forceSky(WeatherService.SKY_RAIN);
            case "snow" -> WeatherService.forceSky(WeatherService.SKY_SNOW);
            case "storm" -> WeatherService.forceSky(WeatherService.SKY_STORM);
            case "auto" -> WeatherService.clearOverrides();
            case "status" -> { player.yellowMessage("Weather: " + WeatherService.currentTestState()); return; }
            default -> { player.yellowMessage(getDescription()); return; }
        }
        WeatherPackets.broadcastAll();
        player.yellowMessage("Weather: " + WeatherService.currentTestState());
    }
}
