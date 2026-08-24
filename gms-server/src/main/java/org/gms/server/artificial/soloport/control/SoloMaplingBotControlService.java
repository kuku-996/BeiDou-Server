package org.gms.server.artificial.soloport.control;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.gms.client.Character;
import org.gms.net.server.Server;
import org.gms.server.artificial.soloport.ArtificialPlayer.BotGeneration;
import org.gms.server.artificial.soloport.ArtificialPlayer.BotSM;
import org.gms.server.artificial.soloport.ArtificialPlayer.BotTypeManager;
import org.gms.server.artificial.soloport.ArtificialPlayer.BotTypeManager.BotType;
import org.gms.server.artificial.soloport.ArtificialPlayer.BotAttackSystem.BotAttackDriver;
import org.gms.server.artificial.soloport.ArtificialPlayer.BotDecoratorSystem.BotDecorationQueue;
import org.gms.server.artificial.soloport.ArtificialPlayer.BotDecoratorSystem.BotEquipChecker;
import org.gms.server.artificial.soloport.ArtificialPlayer.BotMessagingSystem.CharacterStorage;
import org.gms.server.artificial.soloport.ArtificialPlayer.BotTypes.TrainingBot;
import org.gms.server.artificial.soloport.ArtificialPlayer.ConversationManager;
import org.gms.server.artificial.soloport.ArtificialPlayer.SocialHotPotatoManager;
import org.gms.server.artificial.soloport.Environment.EnvironmentManager;
import org.gms.server.artificial.soloport.FreeMarket.ArtificialFreeMarket;
import org.gms.server.maps.MapleMap;
import org.springframework.stereotype.Service;

import java.awt.Point;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Persistent live control for the SoloMapling population.
 *
 * Population changes run on a virtual thread and are intentionally batched so the
 * v83 client never receives the original project's hundreds of simultaneous spawns.
 */
@Slf4j
@Service
public class SoloMaplingBotControlService {
    public static final int MAX_BOT_COUNT = 200;
    private static final int SPAWN_BATCH_SIZE = 5;
    private static final int[] SAFE_TOWN_MAPS = {100000000, 103000000, 102000000, 101000000};
    private static final int[][] SAFE_LEVEL_BANDS = {{10, 30}, {10, 30}, {10, 30}, {10, 30}};
    private static final List<ManagedType> AMBIENT_TYPES = List.of(
            new ManagedType("社交机器人", BotType.SOCIAL_BOT, 45),
            new ManagedType("城镇漫游机器人", BotType.TOWN_WANDERER_BOT, 20),
            new ManagedType("自由市场机器人", BotType.FM_BOT, 15),
            new ManagedType("自由市场商人", BotType.SELLING_MERCHANT_BOT, 10),
            new ManagedType("射手村机器人", BotType.HENESYS_BOT, 10)
    );

    private static volatile SoloMaplingBotControlService instance;
    private static volatile SoloMaplingBotControlConfig runtimeConfig = new SoloMaplingBotControlConfig();

    private final ObjectMapper objectMapper;
    private final Path configFile = Path.of(System.getProperty("user.dir"), "config", "solomapling-bot-control.json");
    private final AtomicBoolean applying = new AtomicBoolean(false);

    private volatile boolean environmentReady;
    private volatile String lastAction = "等待服务端机器人环境初始化";
    private volatile String lastError = "";
    private volatile long updatedAt = System.currentTimeMillis();
    private int townCursor;

    private record ManagedType(String label, BotType type, int percent) {
    }

    public SoloMaplingBotControlService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        instance = this;
    }

    @PostConstruct
    public void loadOnStartup() {
        try {
            if (Files.exists(configFile)) {
                runtimeConfig = sanitize(objectMapper.readValue(configFile.toFile(), SoloMaplingBotControlConfig.class));
            } else {
                runtimeConfig = sanitize(runtimeConfig);
                persist(runtimeConfig);
            }
        } catch (Exception e) {
            log.warn("Unable to load SoloMapling bot-control settings; defaults will be used.", e);
            runtimeConfig = sanitize(new SoloMaplingBotControlConfig());
        }
        log.info("Loaded SoloMapling bot-control settings from {} (master={}, target={}).",
                configFile, runtimeConfig.isMasterEnabled(), runtimeConfig.getTargetBotCount());
    }

    public static boolean isMasterEnabled() {
        return runtimeConfig.isMasterEnabled();
    }

    public static boolean isAutoShopEnabled() {
        return runtimeConfig.isMasterEnabled() && runtimeConfig.isAutoShopEnabled();
    }

    /**
     * Apply game-class switches only after Server.init has built the application
     * context, world and channel graph. Calling this from a Spring @PostConstruct
     * would initialize Job/I18nUtil before ServerManager owns the context.
     */
    public static void prepareEnvironmentStartup() {
        SoloMaplingBotControlService service = instance;
        if (service != null) service.applyRuntimeSwitches(false);
    }

    /** Called by EnvironmentManager after its built-in pilot/full startup wave has completed. */
    public static void markEnvironmentReady() {
        SoloMaplingBotControlService service = instance;
        if (service == null) return;
        service.environmentReady = true;
        service.lastAction = "机器人环境已就绪，正在校准目标数量";
        service.updatedAt = System.currentTimeMillis();
        service.applyRuntimeSwitches(true);
        service.reconcileAsync("启动校准");
    }

    public synchronized SoloMaplingBotControlStatus save(SoloMaplingBotControlConfig requested) {
        runtimeConfig = sanitize(requested);
        try {
            persist(runtimeConfig);
        } catch (IOException e) {
            throw new IllegalStateException("保存机器人控制配置失败：" + e.getMessage(), e);
        }
        lastError = "";
        lastAction = "控制配置已保存，正在实时应用";
        updatedAt = System.currentTimeMillis();
        applyRuntimeSwitches(true);
        reconcileAsync("配置变更");
        return getStatus();
    }

    public SoloMaplingBotControlStatus reconcileNow() {
        applyRuntimeSwitches(true);
        reconcileAsync("手动校准");
        return getStatus();
    }

    public SoloMaplingBotControlStatus getStatus() {
        Map<String, Integer> typeCounts = new LinkedHashMap<>();
        int running = 0;
        int training = 0;
        for (BotSM bot : new ArrayList<>(CharacterStorage.getAllBots().values())) {
            if (bot == null) continue;
            String type = friendlyType(bot);
            typeCounts.merge(type, 1, Integer::sum);
            if (bot.getRunning()) running++;
            if (bot instanceof TrainingBot) training++;
        }
        return new SoloMaplingBotControlStatus(
                copy(runtimeConfig),
                environmentReady,
                applying.get(),
                CharacterStorage.getAllBots().size(),
                running,
                training,
                runtimeConfig.isMasterEnabled() && runtimeConfig.isAutoCombatEnabled()
                        ? TrainingBot.activeGrinderCount() : 0,
                ArtificialFreeMarket.countAutomaticShops(),
                typeCounts,
                lastAction,
                lastError,
                updatedAt
        );
    }

    private void applyRuntimeSwitches(boolean manageManagers) {
        boolean active = runtimeConfig.isMasterEnabled();
        BotAttackDriver.setAutomaticCombatEnabled(active && runtimeConfig.isAutoCombatEnabled());
        ArtificialFreeMarket.setAutomaticShopsEnabled(active && runtimeConfig.isAutoShopEnabled());

        if (!manageManagers || !environmentReady) return;
        if (active && runtimeConfig.isSocialEnabled()) {
            SocialHotPotatoManager.getInstance().start();
            ConversationManager.getInstance().start();
        } else {
            SocialHotPotatoManager.getInstance().stop();
            ConversationManager.getInstance().stop();
        }
        if (active) {
            BotDecorationQueue.start();
            BotEquipChecker.start();
        }
        if (!active || !runtimeConfig.isAutoShopEnabled() || runtimeConfig.getTargetBotCount() == 0) {
            ArtificialFreeMarket.closeAutomaticShops();
        } else {
            ArtificialFreeMarket.ensureAutomaticShopCount(desiredAutomaticShopCount());
        }
    }

    private int desiredAutomaticShopCount() {
        int target = runtimeConfig.getTargetBotCount();
        if (target == 0) return 0;
        return Math.min(4, Math.max(1, (target + 24) / 25));
    }

    private void reconcileAsync(String reason) {
        if (!environmentReady || !applying.compareAndSet(false, true)) return;
        Thread.startVirtualThread(() -> {
            try {
                reconcile(reason);
                lastError = "";
            } catch (Exception e) {
                lastError = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                lastAction = "机器人数量校准失败";
                log.error("SoloMapling population reconciliation failed.", e);
            } finally {
                updatedAt = System.currentTimeMillis();
                applying.set(false);
            }
        });
    }

    private void reconcile(String reason) {
        SoloMaplingBotControlConfig config = copy(runtimeConfig);
        if (!config.isMasterEnabled()) {
            lastAction = "总开关已关闭，正在安全移除机器人";
            removeBots(CharacterStorage.getAllBots().size(), null);
            lastAction = "机器人已全部停用";
            return;
        }

        int target = config.getTargetBotCount();
        int desiredTraining = (int) Math.round(target * (config.getTrainingBotPercent() / 100.0));
        int currentTraining = countTrainingBots();
        int total = CharacterStorage.getAllBots().size();

        if (total > target) {
            lastAction = "正在减少机器人至 " + target + " 个";
            removeBots(total - target, null);
        }

        currentTraining = countTrainingBots();
        total = CharacterStorage.getAllBots().size();
        if (currentTraining > desiredTraining) {
            removeBots(currentTraining - desiredTraining, Boolean.TRUE);
        } else if (currentTraining < desiredTraining && total >= target) {
            // Make room for the required training share before creating its replacement.
            removeBots(desiredTraining - currentTraining, Boolean.FALSE);
        }

        Map<String, Integer> desiredAmbient = desiredAmbientCounts(target - desiredTraining);
        trimAmbientSurplus(desiredAmbient);

        int guard = 0;
        while (CharacterStorage.getAllBots().size() < target && guard++ < MAX_BOT_COUNT) {
            int missing = target - CharacterStorage.getAllBots().size();
            int batch = Math.min(SPAWN_BATCH_SIZE, missing);
            currentTraining = countTrainingBots();
            int trainMissing = Math.max(0, desiredTraining - currentTraining);
            int trainBatch = Math.min(batch, trainMissing);
            int before = CharacterStorage.getAllBots().size();
            if (trainBatch > 0) {
                spawnTrainingBatch(trainBatch);
            } else {
                ManagedType type = nextAmbientType(desiredAmbient);
                if (type == null) break;
                int ambientMissing = desiredAmbient.getOrDefault(type.label(), 0)
                        - ambientTypeCounts().getOrDefault(type.label(), 0);
                spawnAmbientBatch(type, Math.min(batch, ambientMissing));
            }
            if (CharacterStorage.getAllBots().size() >= target) break;
            if (CharacterStorage.getAllBots().size() <= before) {
                log.warn("SoloMapling control could not create the next bot cohort; stopping this pass.");
                break;
            }
            try {
                Thread.sleep(150L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        lastAction = reason + "完成：当前 " + CharacterStorage.getAllBots().size()
                + " / 目标 " + target + " 个机器人";
    }

    private void spawnTrainingBatch(int count) {
        int index = nextTownIndex();
        MapleMap map = safeMap(index);
        if (map == null || map.getPortal(0) == null) return;
        Point anchor = map.getPortal(0).getPosition();
        EnvironmentManager.spawnScatteredTrainingBots(
                map, anchor, count, SAFE_LEVEL_BANDS[index][0], SAFE_LEVEL_BANDS[index][1]);
    }

    private void spawnAmbientBatch(ManagedType type, int count) {
        int index = nextTownIndex();
        EnvironmentManager.spawnControlCohort(type.type(), count,
                SAFE_TOWN_MAPS[index], SAFE_LEVEL_BANDS[index][0], SAFE_LEVEL_BANDS[index][1]);
    }

    private Map<String, Integer> desiredAmbientCounts(int total) {
        Map<String, Integer> result = new LinkedHashMap<>();
        int remaining = Math.max(0, total);
        for (int i = 0; i < AMBIENT_TYPES.size(); i++) {
            ManagedType type = AMBIENT_TYPES.get(i);
            int desired = i == AMBIENT_TYPES.size() - 1
                    ? remaining
                    : Math.min(remaining, (int) Math.round(total * (type.percent() / 100.0)));
            result.put(type.label(), desired);
            remaining -= desired;
        }
        return result;
    }

    private ManagedType nextAmbientType(Map<String, Integer> desiredAmbient) {
        Map<String, Integer> current = ambientTypeCounts();
        for (ManagedType type : AMBIENT_TYPES) {
            if (current.getOrDefault(type.label(), 0) < desiredAmbient.getOrDefault(type.label(), 0)) {
                return type;
            }
        }
        return null;
    }

    private Map<String, Integer> ambientTypeCounts() {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (BotSM bot : CharacterStorage.getAllBots().values()) {
            if (bot == null || bot instanceof TrainingBot) continue;
            result.merge(friendlyType(bot), 1, Integer::sum);
        }
        return result;
    }

    /** Removes surplus legacy/cohort types so subsequent spawns restore the requested mix. */
    private void trimAmbientSurplus(Map<String, Integer> desiredAmbient) {
        Map<String, Integer> current = ambientTypeCounts();
        List<BotSM> candidates = new ArrayList<>(CharacterStorage.getAllBots().values());
        candidates.removeIf(bot -> bot == null || bot instanceof TrainingBot);
        candidates.sort(Comparator.comparingInt(this::removalPriority));
        for (BotSM bot : candidates) {
            String label = friendlyType(bot);
            int allowed = desiredAmbient.getOrDefault(label, 0);
            int observed = current.getOrDefault(label, 0);
            if (observed <= allowed) continue;
            if (removeBot(bot)) current.put(label, observed - 1);
        }
    }

    private int nextTownIndex() {
        int result = Math.floorMod(townCursor, SAFE_TOWN_MAPS.length);
        townCursor = (townCursor + 1) % SAFE_TOWN_MAPS.length;
        return result;
    }

    private MapleMap safeMap(int index) {
        try {
            if (Server.getInstance().getChannel(0, 1) == null) return null;
            return Server.getInstance().getChannel(0, 1).getMapFactory().getMap(SAFE_TOWN_MAPS[index]);
        } catch (Exception e) {
            log.warn("Unable to load safe bot-control map {}.", SAFE_TOWN_MAPS[index], e);
            return null;
        }
    }

    /**
     * @param trainingOnly true removes training, false removes non-training, null removes any.
     */
    private void removeBots(int count, Boolean trainingOnly) {
        List<BotSM> candidates = new ArrayList<>(CharacterStorage.getAllBots().values());
        candidates.removeIf(bot -> bot == null || (trainingOnly != null
                && (bot instanceof TrainingBot) != trainingOnly));
        candidates.sort(Comparator
                .comparingInt(this::removalPriority)
                .thenComparingInt(bot -> -bot.getChr().getId()));
        int removed = 0;
        for (BotSM bot : candidates) {
            if (removed >= count) break;
            if (removeBot(bot)) removed++;
        }
    }

    private int removalPriority(BotSM bot) {
        String type = friendlyType(bot);
        if (type.equals("训练机器人") || type.equals("社交机器人") || type.equals("城镇漫游机器人")) return 0;
        if (type.equals("自由市场机器人") || type.contains("商人")) return 2;
        if (type.equals("教程机器人")) return 3;
        return 1;
    }

    private boolean removeBot(BotSM bot) {
        Character chr = bot.getChr();
        if (chr == null) return false;
        try {
            bot.setRunning(false);
            bot.stopScheduledTask();
            if (chr.getPlayerShop() != null) chr.closePlayerShop();
            if (chr.getMap() != null) {
                BotGeneration.removeBotFromServer(chr);
            } else {
                CharacterStorage.removeActiveBot(chr.getId());
            }
            return true;
        } catch (Exception e) {
            log.warn("Unable to remove bot {} ({}).", chr.getName(), chr.getId(), e);
            CharacterStorage.removeActiveBot(chr.getId());
            return false;
        }
    }

    private int countTrainingBots() {
        int count = 0;
        for (BotSM bot : CharacterStorage.getAllBots().values()) {
            if (bot instanceof TrainingBot) count++;
        }
        return count;
    }

    private String friendlyType(BotSM bot) {
        if (bot instanceof TrainingBot) return "训练机器人";
        String type = bot.getBotType();
        if (type == null || type.isBlank()) type = bot.getClass().getSimpleName();
        return switch (type.toUpperCase()) {
            case "SOCIALBOT", "SOCIAL_BOT" -> "社交机器人";
            case "TOWNWANDERERBOT", "TOWN_WANDERER_BOT" -> "城镇漫游机器人";
            case "FMBOT", "FM_BOT" -> "自由市场机器人";
            case "TUTORIALBOT", "TUTORIAL_BOT" -> "教程机器人";
            case "SELLINGMERCHANTBOT", "SELLING_MERCHANT_BOT" -> "出售商人";
            case "BUYINGMERCHANTBOT", "BUYING_MERCHANT_BOT" -> "收购商人";
            case "NXMERCHANTBOT", "NX_MERCHANT_BOT" -> "点券商人";
            case "MERCHANTBOT" -> "自由市场商人";
            case "HENESYSBOT", "HENESYS_BOT" -> "射手村机器人";
            default -> type;
        };
    }

    private SoloMaplingBotControlConfig sanitize(SoloMaplingBotControlConfig source) {
        SoloMaplingBotControlConfig result = source == null ? new SoloMaplingBotControlConfig() : copy(source);
        result.setTargetBotCount(Math.max(0, Math.min(MAX_BOT_COUNT, result.getTargetBotCount())));
        result.setTrainingBotPercent(Math.max(0, Math.min(100, result.getTrainingBotPercent())));
        return result;
    }

    private SoloMaplingBotControlConfig copy(SoloMaplingBotControlConfig source) {
        SoloMaplingBotControlConfig result = new SoloMaplingBotControlConfig();
        result.setMasterEnabled(source.isMasterEnabled());
        result.setTargetBotCount(source.getTargetBotCount());
        result.setAutoCombatEnabled(source.isAutoCombatEnabled());
        result.setAutoShopEnabled(source.isAutoShopEnabled());
        result.setSocialEnabled(source.isSocialEnabled());
        result.setTrainingBotPercent(source.getTrainingBotPercent());
        return result;
    }

    private void persist(SoloMaplingBotControlConfig config) throws IOException {
        Files.createDirectories(configFile.getParent());
        Files.writeString(configFile,
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(config) + System.lineSeparator(),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }
}
