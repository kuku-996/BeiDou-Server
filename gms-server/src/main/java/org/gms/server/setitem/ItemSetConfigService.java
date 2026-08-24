package org.gms.server.setitem;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.gms.manager.ServerManager;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;

/** Persists item-set overrides and atomically applies them to the live provider. */
@Slf4j
@Service
public class ItemSetConfigService {
    private static final int MAX_SETS = 500;
    private static final int MAX_ITEMS_PER_SET = 100;
    private static final int MAX_STAT_VALUE = 1_000_000;

    private final ObjectMapper objectMapper;
    private final Path configFile = Path.of(System.getProperty("user.dir"), "config", "item-sets.json");
    private volatile ItemSetAdminConfig runtimeConfig;

    public ItemSetConfigService(ObjectMapper objectMapper, ServerManager serverManager) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void loadOnStartup() {
        ItemSetInfoProvider provider = ItemSetInfoProvider.getInstance();
        try {
            if (Files.exists(configFile)) {
                runtimeConfig = sanitize(objectMapper.readValue(configFile.toFile(), ItemSetAdminConfig.class));
                apply(runtimeConfig);
            } else {
                runtimeConfig = fromProvider(true, provider.getDefinitions());
                persist(runtimeConfig);
            }
        } catch (Exception e) {
            log.warn("Unable to load item-set settings; WZ definitions will be used.", e);
            provider.reloadFromWz();
            runtimeConfig = fromProvider(true, provider.getDefinitions());
        }
        log.info("Loaded item-set settings from {} (enabled={}, sets={}).",
                configFile, runtimeConfig.isEnabled(), runtimeConfig.getSets().size());
    }

    public ItemSetAdminConfig getConfig() {
        return copy(runtimeConfig);
    }

    public synchronized ItemSetAdminConfig save(ItemSetAdminConfig requested) {
        ItemSetAdminConfig next = sanitize(requested);
        try {
            persist(next);
        } catch (IOException e) {
            throw new IllegalStateException("保存套装配置失败：" + e.getMessage(), e);
        }
        apply(next);
        runtimeConfig = next;
        return copy(next);
    }

    public synchronized ItemSetAdminConfig resetToWz() {
        ItemSetInfoProvider provider = ItemSetInfoProvider.getInstance();
        provider.reloadFromWz();
        ItemSetAdminConfig next = fromProvider(true, provider.getDefinitions());
        try {
            persist(next);
        } catch (IOException e) {
            throw new IllegalStateException("恢复 WZ 套装配置失败：" + e.getMessage(), e);
        }
        runtimeConfig = next;
        return copy(next);
    }

    private void apply(ItemSetAdminConfig config) {
        Map<Integer, ItemSetInfo> definitions = new LinkedHashMap<>();
        for (ItemSetAdminConfig.Definition definition : config.getSets()) {
            NavigableMap<Integer, Map<String, Integer>> tiers = ItemSetInfo.newTierMap();
            for (ItemSetAdminConfig.Tier tier : definition.getTiers()) {
                tiers.put(tier.getPieceCount(), new LinkedHashMap<>(tier.getStats()));
            }
            ItemSetInfo info = new ItemSetInfo(
                    definition.getSetId(),
                    definition.getName(),
                    new LinkedHashSet<>(definition.getItemIds()),
                    tiers);
            definitions.put(info.getSetId(), info);
        }
        ItemSetInfoProvider.getInstance().replaceDefinitions(config.isEnabled(), definitions);
    }

    private void persist(ItemSetAdminConfig config) throws IOException {
        Files.createDirectories(configFile.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(configFile.toFile(), config);
    }

    private static ItemSetAdminConfig sanitize(ItemSetAdminConfig requested) {
        if (requested == null) {
            throw new IllegalArgumentException("套装配置不能为空。");
        }
        ItemSetAdminConfig clean = new ItemSetAdminConfig();
        clean.setEnabled(requested.isEnabled());
        List<ItemSetAdminConfig.Definition> sourceSets = requested.getSets() == null
                ? List.of() : requested.getSets();
        if (sourceSets.size() > MAX_SETS) {
            throw new IllegalArgumentException("套装数量不能超过 " + MAX_SETS + " 个。");
        }

        Set<Integer> usedSetIds = new HashSet<>();
        Set<Integer> usedItemIds = new HashSet<>();
        List<ItemSetAdminConfig.Definition> sets = new ArrayList<>();
        for (ItemSetAdminConfig.Definition source : sourceSets) {
            if (source == null || source.getSetId() <= 0) {
                throw new IllegalArgumentException("套装 ID 必须大于 0。");
            }
            if (!usedSetIds.add(source.getSetId())) {
                throw new IllegalArgumentException("套装 ID 重复：" + source.getSetId());
            }
            ItemSetAdminConfig.Definition definition = new ItemSetAdminConfig.Definition();
            definition.setSetId(source.getSetId());
            String name = source.getName() == null ? "" : source.getName().trim();
            definition.setName(name.isEmpty() ? "套装 " + source.getSetId() : name);

            LinkedHashSet<Integer> itemIds = new LinkedHashSet<>();
            if (source.getItemIds() != null) {
                for (Integer itemId : source.getItemIds()) {
                    if (itemId == null || itemId <= 0) continue;
                    if (!usedItemIds.add(itemId)) {
                        throw new IllegalArgumentException("装备 ID 同时出现在多个套装中：" + itemId);
                    }
                    itemIds.add(itemId);
                }
            }
            if (itemIds.isEmpty()) {
                throw new IllegalArgumentException("套装 " + source.getSetId() + " 至少需要一个装备 ID。");
            }
            if (itemIds.size() > MAX_ITEMS_PER_SET) {
                throw new IllegalArgumentException("单个套装的装备数量不能超过 " + MAX_ITEMS_PER_SET + " 个。");
            }
            definition.setItemIds(new ArrayList<>(itemIds));

            Map<Integer, ItemSetAdminConfig.Tier> tiersByCount = new LinkedHashMap<>();
            if (source.getTiers() != null) {
                for (ItemSetAdminConfig.Tier sourceTier : source.getTiers()) {
                    if (sourceTier == null || sourceTier.getPieceCount() <= 0) continue;
                    ItemSetAdminConfig.Tier tier = new ItemSetAdminConfig.Tier();
                    tier.setPieceCount(sourceTier.getPieceCount());
                    Map<String, Integer> stats = new LinkedHashMap<>();
                    if (sourceTier.getStats() != null) {
                        for (Map.Entry<String, Integer> stat : sourceTier.getStats().entrySet()) {
                            if (!ItemSetInfoProvider.SUPPORTED_STAT_KEYS.contains(stat.getKey())
                                    || stat.getValue() == null || stat.getValue() == 0) {
                                continue;
                            }
                            stats.put(stat.getKey(), clamp(stat.getValue(), -MAX_STAT_VALUE, MAX_STAT_VALUE));
                        }
                    }
                    if (!stats.isEmpty()) {
                        tier.setStats(stats);
                        tiersByCount.put(tier.getPieceCount(), tier);
                    }
                }
            }
            if (tiersByCount.isEmpty()) {
                throw new IllegalArgumentException("套装 " + source.getSetId() + " 至少需要一个有效档位属性。");
            }
            definition.setTiers(new ArrayList<>(tiersByCount.values()));
            definition.getTiers().sort(java.util.Comparator.comparingInt(ItemSetAdminConfig.Tier::getPieceCount));
            sets.add(definition);
        }
        clean.setSets(sets);
        return clean;
    }

    private static ItemSetAdminConfig fromProvider(boolean enabled, Map<Integer, ItemSetInfo> source) {
        ItemSetAdminConfig config = new ItemSetAdminConfig();
        config.setEnabled(enabled);
        List<ItemSetAdminConfig.Definition> definitions = new ArrayList<>();
        for (ItemSetInfo info : source.values()) {
            ItemSetAdminConfig.Definition definition = new ItemSetAdminConfig.Definition();
            definition.setSetId(info.getSetId());
            definition.setName(info.getName());
            definition.setItemIds(new ArrayList<>(info.getItemIds()));
            List<ItemSetAdminConfig.Tier> tiers = new ArrayList<>();
            for (Map.Entry<Integer, Map<String, Integer>> entry : info.getTierEffects().entrySet()) {
                ItemSetAdminConfig.Tier tier = new ItemSetAdminConfig.Tier();
                tier.setPieceCount(entry.getKey());
                Map<String, Integer> stats = new LinkedHashMap<>();
                for (Map.Entry<String, Integer> stat : entry.getValue().entrySet()) {
                    if (ItemSetInfoProvider.SUPPORTED_STAT_KEYS.contains(stat.getKey())) {
                        stats.put(stat.getKey(), stat.getValue());
                    }
                }
                if (!stats.isEmpty()) {
                    tier.setStats(stats);
                    tiers.add(tier);
                }
            }
            if (!definition.getItemIds().isEmpty() && !tiers.isEmpty()) {
                definition.setTiers(tiers);
                definitions.add(definition);
            }
        }
        config.setSets(definitions);
        return config;
    }

    private static ItemSetAdminConfig copy(ItemSetAdminConfig source) {
        ItemSetAdminConfig target = new ItemSetAdminConfig();
        target.setEnabled(source.isEnabled());
        List<ItemSetAdminConfig.Definition> sets = new ArrayList<>();
        for (ItemSetAdminConfig.Definition sourceDefinition : source.getSets()) {
            ItemSetAdminConfig.Definition definition = new ItemSetAdminConfig.Definition();
            definition.setSetId(sourceDefinition.getSetId());
            definition.setName(sourceDefinition.getName());
            definition.setItemIds(new ArrayList<>(sourceDefinition.getItemIds()));
            List<ItemSetAdminConfig.Tier> tiers = new ArrayList<>();
            for (ItemSetAdminConfig.Tier sourceTier : sourceDefinition.getTiers()) {
                ItemSetAdminConfig.Tier tier = new ItemSetAdminConfig.Tier();
                tier.setPieceCount(sourceTier.getPieceCount());
                tier.setStats(new LinkedHashMap<>(sourceTier.getStats()));
                tiers.add(tier);
            }
            definition.setTiers(tiers);
            sets.add(definition);
        }
        target.setSets(sets);
        return target;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
