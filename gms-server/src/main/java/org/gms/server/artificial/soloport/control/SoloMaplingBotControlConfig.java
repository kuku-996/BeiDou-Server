package org.gms.server.artificial.soloport.control;

/** Persistent settings exposed by the SoloMapling robot-control console. */
public class SoloMaplingBotControlConfig {
    private boolean masterEnabled = true;
    private int targetBotCount = 23;
    private boolean autoCombatEnabled = true;
    private boolean autoShopEnabled = true;
    private boolean socialEnabled = true;
    private int trainingBotPercent = 35;

    public boolean isMasterEnabled() {
        return masterEnabled;
    }

    public void setMasterEnabled(boolean masterEnabled) {
        this.masterEnabled = masterEnabled;
    }

    public int getTargetBotCount() {
        return targetBotCount;
    }

    public void setTargetBotCount(int targetBotCount) {
        this.targetBotCount = targetBotCount;
    }

    public boolean isAutoCombatEnabled() {
        return autoCombatEnabled;
    }

    public void setAutoCombatEnabled(boolean autoCombatEnabled) {
        this.autoCombatEnabled = autoCombatEnabled;
    }

    public boolean isAutoShopEnabled() {
        return autoShopEnabled;
    }

    public void setAutoShopEnabled(boolean autoShopEnabled) {
        this.autoShopEnabled = autoShopEnabled;
    }

    public boolean isSocialEnabled() {
        return socialEnabled;
    }

    public void setSocialEnabled(boolean socialEnabled) {
        this.socialEnabled = socialEnabled;
    }

    public int getTrainingBotPercent() {
        return trainingBotPercent;
    }

    public void setTrainingBotPercent(int trainingBotPercent) {
        this.trainingBotPercent = trainingBotPercent;
    }
}
