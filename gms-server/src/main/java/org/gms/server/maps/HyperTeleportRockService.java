package org.gms.server.maps;

import org.gms.client.Character;
import org.gms.client.Client;
import org.gms.client.inventory.InventoryType;
import org.gms.client.inventory.Item;
import org.gms.client.inventory.manipulator.InventoryManipulator;
import org.gms.constants.id.ItemId;
import org.gms.constants.id.MapId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-authoritative validation for Kaentake Hyper Teleport Rock requests.
 *
 * Item 5041000 is consumed once after every successful validation.
 */
public final class HyperTeleportRockService {
    private static final Logger log =
            LoggerFactory.getLogger(HyperTeleportRockService.class);
    private static final long REQUEST_INTERVAL_MS = 750L;
    private static final ConcurrentHashMap<Integer, Long> LAST_REQUEST =
            new ConcurrentHashMap<>();

    private HyperTeleportRockService() {
    }

    public static boolean tryTeleport(Client c, int targetMapId) {
        Character player = c != null ? c.getPlayer() : null;
        if (c == null || player == null || !c.isLoggedIn()
                || !player.isLoggedInWorld()) {
            return reject(player, -1, targetMapId, false,
                    "player-not-in-world");
        }

        MapleMap sourceMap = player.getMap();
        int sourceMapId = sourceMap != null ? sourceMap.getId() : -1;
        Item rock = getValidRock(player);
        boolean hasRock = rock != null;

        if (sourceMap == null) {
            return reject(player, sourceMapId, targetMapId, hasRock,
                    "missing-source-map");
        }
        if (player.isChangingMaps() || player.isBanned()) {
            return rejectWithMessage(player, sourceMapId, targetMapId, hasRock,
                    "当前状态无法使用超传送岩石。", "changing-map-or-banned");
        }
        if (!player.isAlive()) {
            return rejectWithMessage(player, sourceMapId, targetMapId, hasRock,
                    "死亡状态无法使用超传送岩石。", "dead");
        }
        if ((player.getCashShop() != null && player.getCashShop().isOpened())
                || player.getTrade() != null
                || player.getShop() != null || player.getPlayerShop() != null
                || player.getMiniGame() != null
                || player.getHiredMerchant() != null) {
            return rejectWithMessage(player, sourceMapId, targetMapId, hasRock,
                    "当前界面或交易状态无法使用超传送岩石。",
                    "busy-interface");
        }
        if (player.getEventInstance() != null) {
            return rejectWithMessage(player, sourceMapId, targetMapId, hasRock,
                    "活动副本中无法使用超传送岩石。", "event-instance");
        }
        if (!hasRock) {
            return rejectWithMessage(player, sourceMapId, targetMapId, false,
                    "需要持有未过期的高级瞬移之石。", "missing-item");
        }
        if (isRateLimited(player.getId())) {
            return rejectWithMessage(player, sourceMapId, targetMapId, true,
                    "操作过快，请稍后再试。", "rate-limited");
        }
        if (!isAllowedMap(sourceMap)) {
            return rejectWithMessage(player, sourceMapId, targetMapId, true,
                    "当前地图无法使用超传送岩石。", "blocked-source");
        }
        if (targetMapId <= 0 || targetMapId == sourceMapId) {
            return rejectWithMessage(player, sourceMapId, targetMapId, true,
                    "无法传送到该地图。", "invalid-or-same-target");
        }

        MapleMap targetMap = getTargetMap(c, targetMapId, player);
        if (targetMap == null || !isAllowedMap(targetMap)) {
            return rejectWithMessage(player, sourceMapId, targetMapId, true,
                    "无法传送到该地图。", "blocked-or-missing-target");
        }

        String rockOwner = rock.getOwner();
        short rockFlag = rock.getFlag();
        long rockExpiration = rock.getExpiration();
        boolean consumed = false;
        try {
            InventoryManipulator.removeById(
                    c,
                    InventoryType.CASH,
                    ItemId.HYPER_TELEPORT_ROCK,
                    1,
                    false,
                    false);
            consumed = true;
            player.forceChangeMap(
                    targetMap, targetMap.getRandomPlayerSpawnpoint());
            log.info("[HyperTeleportRock] chr={} id={} from={} to={} hasItem=true result=accepted",
                    player.getName(), player.getId(), sourceMapId, targetMapId);
            return true;
        } catch (RuntimeException e) {
            if (consumed) {
                boolean restored = InventoryManipulator.addById(
                        c,
                        ItemId.HYPER_TELEPORT_ROCK,
                        (short) 1,
                        rockOwner,
                        -1,
                        rockFlag,
                        rockExpiration);
                if (!restored) {
                    log.error("[HyperTeleportRock] failed restoring consumed item: chr={} item={}",
                            player.getName(), ItemId.HYPER_TELEPORT_ROCK);
                }
            }
            log.warn("[HyperTeleportRock] warp failed: chr={} from={} to={}",
                    player.getName(), sourceMapId, targetMapId, e);
            return rejectWithMessage(player, sourceMapId, targetMapId, true,
                    "传送失败，请稍后再试。", "warp-failed");
        }
    }

    private static MapleMap getTargetMap(
            Client c,
            int targetMapId,
            Character player) {
        try {
            if (c.getChannelServer() == null) {
                return null;
            }
            return c.getChannelServer().getMapFactory().getMap(targetMapId);
        } catch (RuntimeException e) {
            log.warn("[HyperTeleportRock] failed loading target={} for chr={}",
                    targetMapId, player.getName(), e);
            return null;
        }
    }

    private static Item getValidRock(Character player) {
        Item rock = player.getInventory(InventoryType.CASH)
                .findById(ItemId.HYPER_TELEPORT_ROCK);
        if (rock == null || rock.getQuantity() < 1) {
            return null;
        }
        long expiration = rock.getExpiration();
        return expiration < 0 || expiration > System.currentTimeMillis()
                ? rock
                : null;
    }

    private static boolean isAllowedMap(MapleMap map) {
        int mapId = map.getId();
        return !MapId.isTimeTemple(mapId)
                && !MapId.isBossExpeditionMap(mapId)
                && !MapId.isRestrictedHyperTeleportMap(mapId)
                && !FieldLimit.CANNOTVIPROCK.check(map.getFieldLimit())
                && (map.getForcedReturnId() == MapId.NONE
                    || MapId.isMapleIsland(mapId));
    }

    private static boolean isRateLimited(int characterId) {
        long now = System.currentTimeMillis();
        Long previous = LAST_REQUEST.put(characterId, now);
        if (LAST_REQUEST.size() > 4096) {
            LAST_REQUEST.entrySet().removeIf(
                    entry -> now - entry.getValue() > 60_000L);
        }
        return previous != null && now - previous < REQUEST_INTERVAL_MS;
    }

    private static boolean rejectWithMessage(
            Character player,
            int sourceMapId,
            int targetMapId,
            boolean hasRock,
            String message,
            String reason) {
        if (player != null) {
            player.dropMessage(1, message);
        }
        return reject(player, sourceMapId, targetMapId, hasRock, reason);
    }

    private static boolean reject(
            Character player,
            int sourceMapId,
            int targetMapId,
            boolean hasRock,
            String reason) {
        log.info("[HyperTeleportRock] chr={} id={} from={} to={} hasItem={} result=rejected reason={}",
                player != null ? player.getName() : "unknown",
                player != null ? player.getId() : -1,
                sourceMapId, targetMapId, hasRock, reason);
        return false;
    }
}
