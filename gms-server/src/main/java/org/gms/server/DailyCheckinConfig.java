package org.gms.server;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** Editable, persisted settings for the fixed 28-day client check-in protocol. */
@Data
public class DailyCheckinConfig {
    private boolean enabled = true;
    private boolean autoPopup = true;
    private int minLevel = 10;
    private int claimIntervalHours = 24;
    private int resetAfterHours = 48;
    private List<Reward> rewards = new ArrayList<>();

    @Data
    public static class Reward {
        private int day;
        private int iconItemId = 2000000;
        private int mesos = 1;
        private List<Grant> grants = new ArrayList<>();
    }

    @Data
    public static class Grant {
        private int itemId;
        private int quantity = 1;
    }
}
