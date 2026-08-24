package org.gms.server.setitem;

import org.gms.client.Character;
import org.gms.client.inventory.InventoryType;
import org.gms.client.inventory.Item;
import org.gms.provider.Data;
import org.gms.provider.DataProvider;
import org.gms.provider.DataProviderFactory;
import org.gms.provider.DataTool;
import org.gms.provider.wz.WZFiles;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;

/** Loads and indexes Etc.wz/SetItemInfo.img once per server process. */
public final class ItemSetInfoProvider {
    public static final Set<String> SUPPORTED_STAT_KEYS = Set.of(
            "incSTR", "incDEX", "incINT", "incLUK", "incAllStat",
            "incMHP", "incMMP", "incPAD", "incMAD");

    private static final ItemSetInfoProvider INSTANCE = new ItemSetInfoProvider();

    private volatile Map<Integer, ItemSetInfo> setsById = Map.of();
    private volatile Map<Integer, Integer> itemIdToSetId = Map.of();
    private volatile boolean enabled = true;

    public static ItemSetInfoProvider getInstance() {
        return INSTANCE;
    }

    private ItemSetInfoProvider() {
        reloadFromWz();
    }

    private Map<Integer, ItemSetInfo> loadFromWz() {
        Map<Integer, ItemSetInfo> loadedSets = new LinkedHashMap<>();
        DataProvider etcData = DataProviderFactory.getDataProvider(WZFiles.ETC);
        Data root = etcData.getData("SetItemInfo.img");
        if (root == null) {
            System.out.println("[SetItem] Etc.wz/SetItemInfo.img not found; no sets loaded.");
            return loadedSets;
        }

        Set<String> unsupportedKeys = new HashSet<>();
        for (Data setNode : root.getChildren()) {
            int setId;
            try {
                setId = Integer.parseInt(setNode.getName());
            } catch (NumberFormatException ignored) {
                continue;
            }

            try {
                String name = DataTool.getString(setNode.getChildByPath("setItemName"), "Set #" + setId);
                Set<Integer> itemIds = new LinkedHashSet<>();
                Data itemIdNode = setNode.getChildByPath("ItemID");
                if (itemIdNode != null) {
                    for (Data itemNode : itemIdNode.getChildren()) {
                        int itemId = DataTool.getInt(itemNode);
                        if (itemId > 0) {
                            itemIds.add(itemId);
                        }
                    }
                }

                NavigableMap<Integer, Map<String, Integer>> tierEffects = ItemSetInfo.newTierMap();
                Data effectNode = setNode.getChildByPath("Effect");
                if (effectNode != null) {
                    for (Data tierNode : effectNode.getChildren()) {
                        int pieceCount;
                        try {
                            pieceCount = Integer.parseInt(tierNode.getName());
                        } catch (NumberFormatException ignored) {
                            continue;
                        }
                        Map<String, Integer> stats = new LinkedHashMap<>();
                        for (Data statNode : tierNode.getChildren()) {
                            String key = statNode.getName();
                            stats.put(key, DataTool.getInt(statNode));
                            if (!SUPPORTED_STAT_KEYS.contains(key)) {
                                unsupportedKeys.add(key);
                            }
                        }
                        if (pieceCount > 0 && !stats.isEmpty()) {
                            tierEffects.put(pieceCount, stats);
                        }
                    }
                }

                if (itemIds.isEmpty() || tierEffects.isEmpty()) {
                    System.out.println("[SetItem] Set #" + setId + " has no items or effects; skipped.");
                    continue;
                }
                ItemSetInfo info = new ItemSetInfo(setId, name, itemIds, tierEffects);
                loadedSets.put(setId, info);
            } catch (RuntimeException ex) {
                System.out.println("[SetItem] Failed to load set #" + setId + ": " + ex);
            }
        }

        System.out.println("[SetItem] Loaded " + loadedSets.size() + " set definition(s) from WZ.");
        if (!unsupportedKeys.isEmpty()) {
            System.out.println("[SetItem] Tooltip-only stat keys: " + unsupportedKeys);
        }
        return loadedSets;
    }

    public synchronized void reloadFromWz() {
        replaceDefinitions(true, loadFromWz());
    }

    public synchronized void replaceDefinitions(boolean enabled, Map<Integer, ItemSetInfo> definitions) {
        Map<Integer, ItemSetInfo> newSets = new LinkedHashMap<>(definitions);
        Map<Integer, Integer> newIndex = new HashMap<>();
        for (ItemSetInfo info : newSets.values()) {
            for (int itemId : info.getItemIds()) {
                Integer previous = newIndex.putIfAbsent(itemId, info.getSetId());
                if (previous != null && previous != info.getSetId()) {
                    System.out.println("[SetItem] Item " + itemId + " appears in sets "
                            + previous + " and " + info.getSetId() + "; first set retained.");
                }
            }
        }
        this.setsById = java.util.Collections.unmodifiableMap(newSets);
        this.itemIdToSetId = java.util.Collections.unmodifiableMap(newIndex);
        this.enabled = enabled;
        System.out.println("[SetItem] Applied " + newSets.size() + " set definition(s); enabled=" + enabled + ".");
    }

    public Map<Integer, ItemSetInfo> getDefinitions() {
        return setsById;
    }

    public Integer getSetIdForItem(int itemId) {
        return enabled ? itemIdToSetId.get(itemId) : null;
    }

    public ItemSetInfo getSetInfo(int setId) {
        return enabled ? setsById.get(setId) : null;
    }

    public Map<Integer, ItemSetInfo> getAllSets() {
        return enabled ? setsById : Map.of();
    }

    public Map<Integer, Set<Integer>> getEquippedSetItemIds(Character player) {
        Map<Integer, Set<Integer>> equipped = new LinkedHashMap<>();
        for (Item item : player.getInventory(InventoryType.EQUIPPED)) {
            Integer setId = getSetIdForItem(item.getItemId());
            if (setId != null) {
                equipped.computeIfAbsent(setId, ignored -> new LinkedHashSet<>()).add(item.getItemId());
            }
        }
        return equipped;
    }

    public Map<String, Integer> getActiveBonusStats(Character player) {
        Map<String, Integer> total = new HashMap<>();
        for (Map.Entry<Integer, Set<Integer>> equipped : getEquippedSetItemIds(player).entrySet()) {
            ItemSetInfo set = setsById.get(equipped.getKey());
            if (set == null) {
                continue;
            }
            for (Map.Entry<String, Integer> stat
                    : set.cumulativeEffectFor(equipped.getValue().size()).entrySet()) {
                total.merge(stat.getKey(), stat.getValue(), Integer::sum);
            }
        }
        return total;
    }
}
