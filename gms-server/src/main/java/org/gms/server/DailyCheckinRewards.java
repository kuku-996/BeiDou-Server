package org.gms.server;

import org.gms.client.Client;
import org.gms.client.inventory.manipulator.InventoryManipulator;

/**
 * The single reward table for the 28-day daily check-in cycle.
 *
 * <p>The bundled values intentionally match the source package's safe sample:
 * every day grants one meso and uses Red Potion (2000000) as its display icon.
 * Production rewards are maintained through the web console and persisted as JSON.</p>
 */
public final class DailyCheckinRewards {
    public static final int CYCLE_DAYS = 28;

    private DailyCheckinRewards() {
    }

    public static int iconItemId(int day) {
        DailyCheckinConfig.Reward reward = DailyCheckinConfigService.reward(day);
        return reward == null ? 0 : reward.getIconItemId();
    }

    public static String tooltip(int day) {
        if (!validDay(day)) {
            return "";
        }

        DailyCheckinConfig.Reward reward = DailyCheckinConfigService.reward(day);
        StringBuilder tooltip = new StringBuilder("第 ").append(day).append(" 天");
        for (DailyCheckinConfig.Grant grant : reward.getGrants()) {
            tooltip.append("\n- 道具 ").append(grant.getItemId()).append(" x").append(grant.getQuantity());
        }
        if (reward.getMesos() > 0) {
            tooltip.append("\n- ").append(reward.getMesos()).append(" 金币");
        }
        return tooltip.toString();
    }

    /** Returns false before changing the character when any item has no inventory space. */
    public static boolean grant(Client client, int day) {
        if (!validDay(day) || client == null || client.getPlayer() == null) {
            return false;
        }

        DailyCheckinConfig.Reward reward = DailyCheckinConfigService.reward(day);
        for (DailyCheckinConfig.Grant grant : reward.getGrants()) {
            if (!InventoryManipulator.checkSpace(client, grant.getItemId(), (short) grant.getQuantity(), "")) {
                return false;
            }
        }

        for (DailyCheckinConfig.Grant grant : reward.getGrants()) {
            if (!InventoryManipulator.addById(client, grant.getItemId(), (short) grant.getQuantity())) {
                return false;
            }
        }
        if (reward.getMesos() != 0) {
            client.getPlayer().gainMeso(reward.getMesos(), true);
        }
        return true;
    }

    private static boolean validDay(int day) {
        return day >= 1 && day <= CYCLE_DAYS;
    }
}
