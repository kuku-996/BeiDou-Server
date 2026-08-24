package org.gms.server.setitem;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;

/** Immutable definition of one Etc.wz/SetItemInfo.img entry. */
public final class ItemSetInfo {
    private final int setId;
    private final String name;
    private final Set<Integer> itemIds;
    private final NavigableMap<Integer, Map<String, Integer>> tierEffects;

    public ItemSetInfo(int setId, String name, Set<Integer> itemIds,
                       NavigableMap<Integer, Map<String, Integer>> tierEffects) {
        this.setId = setId;
        this.name = name;
        this.itemIds = Collections.unmodifiableSet(new LinkedHashSet<>(itemIds));

        NavigableMap<Integer, Map<String, Integer>> tiers = new TreeMap<>();
        for (Map.Entry<Integer, Map<String, Integer>> tier : tierEffects.entrySet()) {
            tiers.put(tier.getKey(), Collections.unmodifiableMap(new LinkedHashMap<>(tier.getValue())));
        }
        this.tierEffects = Collections.unmodifiableNavigableMap(tiers);
    }

    public int getSetId() {
        return setId;
    }

    public String getName() {
        return name;
    }

    public Set<Integer> getItemIds() {
        return itemIds;
    }

    public int getMaxTier() {
        return tierEffects.isEmpty() ? 0 : tierEffects.lastKey();
    }

    public Map<String, Integer> cumulativeEffectFor(int equippedCount) {
        Map<String, Integer> total = new HashMap<>();
        if (equippedCount <= 0) {
            return total;
        }
        for (Map<String, Integer> tier : tierEffects.headMap(equippedCount, true).values()) {
            for (Map.Entry<String, Integer> stat : tier.entrySet()) {
                total.merge(stat.getKey(), stat.getValue(), Integer::sum);
            }
        }
        return total;
    }

    public NavigableMap<Integer, Map<String, Integer>> getTierEffects() {
        return tierEffects;
    }

    public static NavigableMap<Integer, Map<String, Integer>> newTierMap() {
        return new TreeMap<>();
    }
}
