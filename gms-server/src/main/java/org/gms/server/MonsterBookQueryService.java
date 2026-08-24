package org.gms.server;

import org.gms.client.Character;
import org.gms.server.life.MonsterDropEntry;
import org.gms.server.life.MonsterInformationProvider;
import org.gms.util.DatabaseConnection;
import org.gms.util.Pair;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Read-only query source for Kaentake's extended Monster Book.
 *
 * <p>All chances are returned in parts per million, using the same character
 * drop-rate and card-rate values that {@code MapleMap} uses when a monster
 * actually drops an item.  No character state is modified here.</p>
 */
public final class MonsterBookQueryService {
    public static final int CHANCE_DENOMINATOR = 1_000_000;
    private static final int MAX_INDEXED_ITEMS = 2_000;

    private static volatile Set<Integer> bookItemIds;

    private MonsterBookQueryService() {
    }

    public static LinkedHashMap<Integer, Integer> mobDropChances(Character chr, int mobId) {
        if (chr == null || mobId <= 0) {
            return new LinkedHashMap<>();
        }

        MonsterInformationProvider monsters = MonsterInformationProvider.getInstance();
        float dropRate = monsters.isBoss(mobId) ? chr.getBossDropRate() : chr.getDropRate();
        List<ChanceRow> rows = new ArrayList<>();
        for (MonsterDropEntry entry : monsters.retrieveDrop(mobId)) {
            if (entry.itemId <= 0 || entry.chance <= 0) {
                continue; // mesos have no item icon in the book
            }
            String name = ItemInformationProvider.getInstance().getName(entry.itemId);
            if (name == null || name.isBlank()) {
                continue;
            }
            rows.add(new ChanceRow(entry.itemId, effectiveChance(entry.chance, dropRate, chr.getCardRate(entry.itemId))));
        }
        return orderUnique(rows);
    }

    /** Finds item ids by name, restricted to monsters that have a Monster Book card. */
    public static int[] findBookItems(String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (needle.length() < 3) {
            return new int[0];
        }

        Set<Integer> allowed = bookItemIds();
        List<Pair<Integer, String>> hits = new ArrayList<>();
        for (Pair<Integer, String> item : ItemInformationProvider.getInstance().getAllItems()) {
            if (!allowed.contains(item.getLeft()) || item.getRight() == null) {
                continue;
            }
            if (item.getRight().toLowerCase(Locale.ROOT).contains(needle)) {
                hits.add(item);
            }
        }
        hits.sort(Comparator
                .comparing((Pair<Integer, String> item) -> !item.getRight().toLowerCase(Locale.ROOT).startsWith(needle))
                .thenComparing(item -> item.getRight().toLowerCase(Locale.ROOT))
                .thenComparingInt(Pair::getLeft));

        int size = Math.min(MAX_INDEXED_ITEMS, hits.size());
        int[] result = new int[size];
        for (int i = 0; i < size; i++) {
            result[i] = hits.get(i).getLeft();
        }
        return result;
    }

    /** Reverse lookup: mobs with a card that drop the specified item, best chance first. */
    public static LinkedHashMap<Integer, Integer> itemDroppers(Character chr, int itemId) {
        if (chr == null || itemId <= 0) {
            return new LinkedHashMap<>();
        }

        List<ChanceRow> rows = new ArrayList<>();
        String sql = "SELECT d.dropperid, MAX(d.chance) AS chance "
                + "FROM drop_data d INNER JOIN monstercarddata c ON c.mobid = d.dropperid "
                + "WHERE d.itemid = ? AND d.chance > 0 GROUP BY d.dropperid";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, itemId);
            try (ResultSet result = statement.executeQuery()) {
                MonsterInformationProvider monsters = MonsterInformationProvider.getInstance();
                float cardRate = chr.getCardRate(itemId);
                while (result.next()) {
                    int mobId = result.getInt("dropperid");
                    float dropRate = monsters.isBoss(mobId) ? chr.getBossDropRate() : chr.getDropRate();
                    rows.add(new ChanceRow(mobId, effectiveChance(result.getInt("chance"), dropRate, cardRate)));
                }
            }
        } catch (SQLException ignored) {
            // The user sees an empty lookup if the drop database is temporarily unavailable.
        }
        return orderUnique(rows);
    }

    /** Call after !reloaddrops or a direct edit to drop_data / monstercarddata. */
    public static void clearDropCaches() {
        bookItemIds = null;
    }

    private static int effectiveChance(int rawChance, float dropRate, float cardRate) {
        long chance = Math.round((double) rawChance * dropRate * cardRate);
        return (int) Math.max(1, Math.min(CHANCE_DENOMINATOR, chance));
    }

    private static LinkedHashMap<Integer, Integer> orderUnique(List<ChanceRow> rows) {
        rows.sort(Comparator.comparingInt(ChanceRow::chance).reversed().thenComparingInt(ChanceRow::id));
        LinkedHashMap<Integer, Integer> result = new LinkedHashMap<>();
        for (ChanceRow row : rows) {
            result.putIfAbsent(row.id(), row.chance());
        }
        return result;
    }

    private static Set<Integer> bookItemIds() {
        Set<Integer> cached = bookItemIds;
        if (cached != null) {
            return cached;
        }

        Set<Integer> loaded = new HashSet<>();
        String sql = "SELECT DISTINCT d.itemid FROM drop_data d "
                + "INNER JOIN monstercarddata c ON c.mobid = d.dropperid "
                + "WHERE d.itemid > 0 AND d.chance > 0";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                loaded.add(result.getInt("itemid"));
            }
        } catch (SQLException ignored) {
            return Set.of();
        }
        bookItemIds = loaded;
        return loaded;
    }

    private record ChanceRow(int id, int chance) {
    }
}
