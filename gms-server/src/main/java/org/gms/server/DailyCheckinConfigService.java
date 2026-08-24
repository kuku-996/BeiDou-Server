package org.gms.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** JSON-backed live configuration for daily check-in. */
@Slf4j
@Service
public class DailyCheckinConfigService {
    private static final int MAX_LEVEL = 250;
    private static final int MAX_INTERVAL_HOURS = 24 * 365;
    private static volatile DailyCheckinConfig runtimeConfig = defaults();

    private final ObjectMapper objectMapper;
    private final Path configFile = Path.of(System.getProperty("user.dir"), "config", "daily-checkin.json");

    public DailyCheckinConfigService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void loadOnStartup() {
        try {
            if (Files.exists(configFile)) {
                runtimeConfig = sanitize(objectMapper.readValue(configFile.toFile(), DailyCheckinConfig.class));
            } else {
                runtimeConfig = defaults();
                persist(runtimeConfig);
            }
        } catch (Exception e) {
            log.warn("Unable to load daily check-in settings; defaults will be used.", e);
            runtimeConfig = defaults();
        }
        log.info("Loaded daily check-in settings from {} (enabled={}, minLevel={}).",
                configFile, runtimeConfig.isEnabled(), runtimeConfig.getMinLevel());
    }

    public synchronized DailyCheckinConfig save(DailyCheckinConfig requested) {
        DailyCheckinConfig next = sanitize(requested);
        try {
            persist(next);
        } catch (IOException e) {
            throw new IllegalStateException("保存每日签到配置失败：" + e.getMessage(), e);
        }
        runtimeConfig = next;
        return copy(next);
    }

    public synchronized DailyCheckinConfig reset() {
        return save(defaults());
    }

    public DailyCheckinConfig getConfig() {
        return copy(runtimeConfig);
    }

    public static boolean isEnabled() {
        return runtimeConfig.isEnabled();
    }

    public static boolean isAutoPopup() {
        return runtimeConfig.isEnabled() && runtimeConfig.isAutoPopup();
    }

    public static int minLevel() {
        return runtimeConfig.getMinLevel();
    }

    public static long claimIntervalMillis() {
        return runtimeConfig.getClaimIntervalHours() * 3_600_000L;
    }

    public static long resetAfterMillis() {
        return runtimeConfig.getResetAfterHours() * 3_600_000L;
    }

    public static DailyCheckinConfig.Reward reward(int day) {
        if (day < 1 || day > DailyCheckinRewards.CYCLE_DAYS) {
            return null;
        }
        return runtimeConfig.getRewards().get(day - 1);
    }

    private void persist(DailyCheckinConfig config) throws IOException {
        Files.createDirectories(configFile.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(configFile.toFile(), config);
    }

    private static DailyCheckinConfig sanitize(DailyCheckinConfig requested) {
        DailyCheckinConfig source = requested == null ? defaults() : requested;
        DailyCheckinConfig clean = new DailyCheckinConfig();
        clean.setEnabled(source.isEnabled());
        clean.setAutoPopup(source.isAutoPopup());
        clean.setMinLevel(clamp(source.getMinLevel(), 1, MAX_LEVEL));
        int claimHours = clamp(source.getClaimIntervalHours(), 1, MAX_INTERVAL_HOURS);
        clean.setClaimIntervalHours(claimHours);
        clean.setResetAfterHours(clamp(source.getResetAfterHours(), claimHours + 1, MAX_INTERVAL_HOURS));

        Map<Integer, DailyCheckinConfig.Reward> byDay = new HashMap<>();
        if (source.getRewards() != null) {
            for (DailyCheckinConfig.Reward reward : source.getRewards()) {
                if (reward != null && reward.getDay() >= 1 && reward.getDay() <= DailyCheckinRewards.CYCLE_DAYS) {
                    byDay.put(reward.getDay(), reward);
                }
            }
        }

        List<DailyCheckinConfig.Reward> rewards = new ArrayList<>(DailyCheckinRewards.CYCLE_DAYS);
        for (int day = 1; day <= DailyCheckinRewards.CYCLE_DAYS; day++) {
            DailyCheckinConfig.Reward sourceReward = byDay.get(day);
            DailyCheckinConfig.Reward reward = new DailyCheckinConfig.Reward();
            reward.setDay(day);
            if (sourceReward != null) {
                reward.setIconItemId(Math.max(1, sourceReward.getIconItemId()));
                reward.setMesos(Math.max(0, sourceReward.getMesos()));
                List<DailyCheckinConfig.Grant> grants = new ArrayList<>();
                if (sourceReward.getGrants() != null) {
                    for (DailyCheckinConfig.Grant sourceGrant : sourceReward.getGrants()) {
                        if (sourceGrant == null || sourceGrant.getItemId() <= 0 || sourceGrant.getQuantity() <= 0) {
                            continue;
                        }
                        DailyCheckinConfig.Grant grant = new DailyCheckinConfig.Grant();
                        grant.setItemId(sourceGrant.getItemId());
                        grant.setQuantity(Math.min(Short.MAX_VALUE, sourceGrant.getQuantity()));
                        grants.add(grant);
                    }
                }
                reward.setGrants(grants);
            }
            rewards.add(reward);
        }
        clean.setRewards(rewards);
        return clean;
    }

    private static DailyCheckinConfig defaults() {
        DailyCheckinConfig config = new DailyCheckinConfig();
        List<DailyCheckinConfig.Reward> rewards = new ArrayList<>(DailyCheckinRewards.CYCLE_DAYS);
        for (int day = 1; day <= DailyCheckinRewards.CYCLE_DAYS; day++) {
            DailyCheckinConfig.Reward reward = new DailyCheckinConfig.Reward();
            reward.setDay(day);
            rewards.add(reward);
        }
        config.setRewards(rewards);
        return config;
    }

    private static DailyCheckinConfig copy(DailyCheckinConfig source) {
        DailyCheckinConfig target = new DailyCheckinConfig();
        target.setEnabled(source.isEnabled());
        target.setAutoPopup(source.isAutoPopup());
        target.setMinLevel(source.getMinLevel());
        target.setClaimIntervalHours(source.getClaimIntervalHours());
        target.setResetAfterHours(source.getResetAfterHours());
        List<DailyCheckinConfig.Reward> rewards = new ArrayList<>();
        for (DailyCheckinConfig.Reward sourceReward : source.getRewards()) {
            DailyCheckinConfig.Reward reward = new DailyCheckinConfig.Reward();
            reward.setDay(sourceReward.getDay());
            reward.setIconItemId(sourceReward.getIconItemId());
            reward.setMesos(sourceReward.getMesos());
            List<DailyCheckinConfig.Grant> grants = new ArrayList<>();
            for (DailyCheckinConfig.Grant sourceGrant : sourceReward.getGrants()) {
                DailyCheckinConfig.Grant grant = new DailyCheckinConfig.Grant();
                grant.setItemId(sourceGrant.getItemId());
                grant.setQuantity(sourceGrant.getQuantity());
                grants.add(grant);
            }
            reward.setGrants(grants);
            rewards.add(reward);
        }
        target.setRewards(rewards);
        return target;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
