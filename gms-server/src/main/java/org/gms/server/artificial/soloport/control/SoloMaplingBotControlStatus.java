package org.gms.server.artificial.soloport.control;

import java.util.Map;

/** Live status returned to the robot-control console. */
public record SoloMaplingBotControlStatus(
        SoloMaplingBotControlConfig config,
        boolean environmentReady,
        boolean applying,
        int currentBotCount,
        int runningBotCount,
        int trainingBotCount,
        int activeGrinderCount,
        int automaticShopCount,
        Map<String, Integer> typeCounts,
        String lastAction,
        String lastError,
        long updatedAt
) {
}
