package org.gms.server;

import org.gms.client.Character;
import org.gms.util.DatabaseConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-authoritative damage-skin catalog, ownership and active-skin state.
 * The catalog is seeded from the same skin-id manifest used to build the
 * client's BasicEff.img overlay, so unsupported ids are never sold.
 */
public final class DamageSkinService {
    public static final int DEFAULT_SKIN_ID = 0;
    public static final long DEFAULT_PRICE_MESOS = 10_000_000L;

    private static final Logger log = LoggerFactory.getLogger(DamageSkinService.class);
    private static final String SKIN_ID_RESOURCE = "/damage-skin-ids.txt";
    private static final Map<Integer, Long> catalog = new TreeMap<>();
    private static final ConcurrentHashMap<Integer, Integer> activeCache = new ConcurrentHashMap<>();
    private static volatile boolean catalogLoaded;

    private DamageSkinService() {
    }

    public static synchronized void ensureCatalog() {
        if (catalogLoaded) {
            return;
        }

        List<Integer> skinIds;
        try {
            skinIds = loadSkinIds();
        } catch (IOException e) {
            log.error("Unable to read {}", SKIN_ID_RESOURCE, e);
            return;
        }

        try (Connection con = DatabaseConnection.getConnection()) {
            try (PreparedStatement insert = con.prepareStatement(
                    "INSERT IGNORE INTO damageskin_catalog (skinId, priceMesos) VALUES (?, ?)")) {
                for (int skinId : skinIds) {
                    insert.setInt(1, skinId);
                    insert.setLong(2, DEFAULT_PRICE_MESOS);
                    insert.addBatch();
                }
                insert.executeBatch();
            }

            catalog.clear();
            try (PreparedStatement select = con.prepareStatement(
                    "SELECT skinId, priceMesos FROM damageskin_catalog ORDER BY skinId");
                 ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    int skinId = rs.getInt("skinId");
                    long price = rs.getLong("priceMesos");
                    if (skinId > DEFAULT_SKIN_ID && price >= 0) {
                        catalog.put(skinId, price);
                    }
                }
            }
            catalogLoaded = true;
            log.info("Damage skin catalog loaded: {} skins", catalog.size());
        } catch (SQLException e) {
            log.error("Unable to seed/load damage skin catalog", e);
        }
    }

    private static List<Integer> loadSkinIds() throws IOException {
        InputStream stream = DamageSkinService.class.getResourceAsStream(SKIN_ID_RESOURCE);
        if (stream == null) {
            throw new IOException("Missing classpath resource " + SKIN_ID_RESOURCE);
        }

        List<Integer> ids = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                try {
                    int id = Integer.parseInt(line);
                    if (id > DEFAULT_SKIN_ID) {
                        ids.add(id);
                    }
                } catch (NumberFormatException ignored) {
                    log.warn("Ignoring invalid damage skin id: {}", line);
                }
            }
        }
        return ids;
    }

    public static Map<Integer, Long> getCatalog() {
        ensureCatalog();
        synchronized (DamageSkinService.class) {
            return Collections.unmodifiableMap(new TreeMap<>(catalog));
        }
    }

    public static List<Integer> getOwned(int characterId) {
        List<Integer> owned = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT skinId FROM damageskin_inventory WHERE characterId = ? ORDER BY skinId")) {
            ps.setInt(1, characterId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    owned.add(rs.getInt("skinId"));
                }
            }
        } catch (SQLException e) {
            log.error("Unable to load damage skins for character {}", characterId, e);
        }
        return owned;
    }

    public static int getActive(int characterId) {
        return activeCache.computeIfAbsent(characterId, DamageSkinService::loadActive);
    }

    private static int loadActive(int characterId) {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT activeSkinId FROM damage_skin_state WHERE characterId = ?")) {
            ps.setInt(1, characterId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("activeSkinId") : DEFAULT_SKIN_ID;
            }
        } catch (SQLException e) {
            log.error("Unable to load active damage skin for character {}", characterId, e);
            return DEFAULT_SKIN_ID;
        }
    }

    public static boolean apply(int characterId, int skinId) {
        if (skinId != DEFAULT_SKIN_ID && !owns(characterId, skinId)) {
            return false;
        }

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT INTO damage_skin_state (characterId, activeSkinId) VALUES (?, ?) "
                             + "ON DUPLICATE KEY UPDATE activeSkinId = VALUES(activeSkinId)")) {
            ps.setInt(1, characterId);
            ps.setInt(2, skinId);
            ps.executeUpdate();
            activeCache.put(characterId, skinId);
            return true;
        } catch (SQLException e) {
            log.error("Unable to apply damage skin {} for character {}", skinId, characterId, e);
            return false;
        }
    }

    public static boolean purchase(Character player, int skinId) {
        if (player == null || skinId == DEFAULT_SKIN_ID) {
            return false;
        }
        ensureCatalog();

        final Long priceLong;
        synchronized (DamageSkinService.class) {
            priceLong = catalog.get(skinId);
        }
        if (priceLong == null || priceLong < 0 || priceLong > Integer.MAX_VALUE) {
            return false;
        }

        int price = priceLong.intValue();
        synchronized (player) {
            if (player.getMeso() < price || owns(player.getId(), skinId)) {
                return false;
            }

            try (Connection con = DatabaseConnection.getConnection();
                 PreparedStatement ps = con.prepareStatement(
                         "INSERT IGNORE INTO damageskin_inventory (characterId, skinId) VALUES (?, ?)")) {
                ps.setInt(1, player.getId());
                ps.setInt(2, skinId);
                if (ps.executeUpdate() != 1) {
                    return false;
                }
            } catch (SQLException e) {
                log.error("Unable to purchase damage skin {} for character {}", skinId, player.getId(), e);
                return false;
            }

            player.gainMeso(-price, true, false, false);
            return true;
        }
    }

    private static boolean owns(int characterId, int skinId) {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT 1 FROM damageskin_inventory WHERE characterId = ? AND skinId = ? LIMIT 1")) {
            ps.setInt(1, characterId);
            ps.setInt(2, skinId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            log.error("Unable to check damage skin {} for character {}", skinId, characterId, e);
            return false;
        }
    }
}
