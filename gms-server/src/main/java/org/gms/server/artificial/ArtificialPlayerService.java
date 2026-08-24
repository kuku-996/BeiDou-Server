package org.gms.server.artificial;

import io.netty.buffer.Unpooled;
import lombok.extern.slf4j.Slf4j;
import org.gms.client.Character;
import org.gms.client.Client;
import org.gms.client.Job;
import org.gms.net.packet.ByteBufInPacket;
import org.gms.net.packet.ByteBufOutPacket;
import org.gms.net.packet.OutPacket;
import org.gms.net.packet.Packet;
import org.gms.net.server.Server;
import org.gms.server.maps.MapleMap;
import org.gms.server.maps.Portal;
import org.gms.util.PacketCreator;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.awt.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Server-side artificial players adapted from SoloMapling for BeiDou.
 *
 * <p>Each bot is a normal Character backed by a mock Client. It is visible to
 * real players but has no account or character database row.</p>
 */
@Slf4j
@Service
public class ArtificialPlayerService {
    private static final int DEFAULT_WORLD = 0;
    private static final int DEFAULT_CHANNEL = 1;
    private static final int FIRST_BOT_ID = 700000000;
    private static final int MAX_BOTS = 100;
    private static final long TICK_MILLIS = 500L;
    private static final int WALK_PIXELS_PER_SECOND = 137;
    private static final int WALK_STEP_PIXELS = (int) Math.round(WALK_PIXELS_PER_SECOND * TICK_MILLIS / 1000.0);
    private static final int MIN_SPAWN_SPACING = 30;
    private static final int SPAWN_ATTEMPTS = 24;
    private static final int MAX_PLATFORM_PROBE = 96;
    private static final int MAX_PLATFORM_HEIGHT_DELTA = 48;
    private static final long MIN_WALK_MILLIS = 3_000L;
    private static final long WALK_VARIANCE_MILLIS = 4_001L;
    private static final long MIN_DWELL_MILLIS = 2_500L;
    private static final long DWELL_VARIANCE_MILLIS = 4_001L;
    private static final int CROWD_PROBE_DISTANCE = 220;
    private static final long MIN_MAP_STAY_MILLIS = 90_000L;
    private static final long MAP_STAY_VARIANCE_MILLIS = 90_001L;
    private static final int[] TARGET_MAP_IDS = {
            100000000, // Henesys
            100000100, // Henesys Market
            100000200  // Henesys Park
    };
    private static final int[] MALE_HAIR_IDS = {
            30000, 30020, 30030, 30040, 30050, 30060, 30100, 30120
    };
    private static final int[] FEMALE_HAIR_IDS = {
            31000, 31010, 31020, 31030, 31040, 31050, 31100, 31120
    };
    private static final int[] MALE_FACE_IDS = {
            20000, 20001, 20002, 20003, 20004, 20005, 20006, 20007
    };
    private static final int[] FEMALE_FACE_IDS = {
            21000, 21001, 21002, 21003, 21004, 21005, 21006, 21007
    };

    private final Map<Integer, ArtificialPlayer> bots = new ConcurrentHashMap<>();
    private final Random random = new Random();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(new BotThreadFactory());
    private final Map<Integer, List<Point>> townSpawnPlans = new ConcurrentHashMap<>();
    private volatile List<MapleMap> activeMaps = List.of();
    private ScheduledFuture<?> tickTask;

    public synchronized String start(int requestedCount) {
        int count = Math.max(1, Math.min(MAX_BOTS, requestedCount));
        if (!bots.isEmpty()) {
            return "\u4eba\u5de5\u73a9\u5bb6\u5df2\u7ecf\u8fd0\u884c\u4e2d\uff0c\u5f53\u524d\u6570\u91cf\uff1a" + bots.size();
        }

        List<MapleMap> maps = getTargetMaps();
        if (maps.isEmpty()) {
            return "\u4eba\u5de5\u73a9\u5bb6\u542f\u52a8\u5931\u8d25\uff1a\u627e\u4e0d\u5230\u5c04\u624b\u6751\u5730\u56fe\u3002";
        }
        activeMaps = List.copyOf(maps);
        prepareTownSpawnPlans(count);

        List<ArtificialPlayer> created = new ArrayList<>();
        try {
            for (int i = 0; i < count; i++) {
                MapleMap map = activeMaps.get(i % activeMaps.size());
                ArtificialPlayer bot = createBot(map, i);
                map.addPlayer(bot.character());
                created.add(bot);
                bots.put(bot.character().getId(), bot);
            }
        } catch (Exception e) {
            log.error("Failed to create artificial players; rolling back this batch", e);
            created.forEach(this::removeBot);
            bots.clear();
            activeMaps = List.of();
            townSpawnPlans.clear();
            return "\u4eba\u5de5\u73a9\u5bb6\u542f\u52a8\u5931\u8d25\uff1a" + e.getClass().getSimpleName();
        }

        tickTask = scheduler.scheduleAtFixedRate(this::tickAll, TICK_MILLIS, TICK_MILLIS, TimeUnit.MILLISECONDS);
        log.info("BeiDou artificial players started: {}, maps: {}", bots.size(), mapSummary());
        return "\u4eba\u5de5\u73a9\u5bb6\u5df2\u542f\u52a8\uff0c\u6570\u91cf\uff1a" + bots.size()
                + "\uff0c\u5206\u5e03\uff1a" + mapSummary();
    }

    public synchronized String stop() {
        if (tickTask != null) {
            tickTask.cancel(false);
            tickTask = null;
        }
        int removed = bots.size();
        bots.values().forEach(this::removeBot);
        bots.clear();
        activeMaps = List.of();
        townSpawnPlans.clear();
        log.info("BeiDou artificial players stopped: {}", removed);
        return "\u4eba\u5de5\u73a9\u5bb6\u5df2\u505c\u6b62\uff0c\u5df2\u79fb\u9664\uff1a" + removed;
    }

    public String status() {
        return bots.isEmpty()
                ? "\u4eba\u5de5\u73a9\u5bb6\u672a\u8fd0\u884c\u3002"
                : "\u4eba\u5de5\u73a9\u5bb6\u8fd0\u884c\u4e2d\uff0c\u6570\u91cf\uff1a" + bots.size()
                + "\uff0c\u5206\u5e03\uff1a" + mapSummary();
    }

    private List<MapleMap> getTargetMaps() {
        List<MapleMap> maps = new ArrayList<>();
        try {
            var channel = Server.getInstance().getChannel(DEFAULT_WORLD, DEFAULT_CHANNEL);
            if (channel == null) {
                return maps;
            }
            for (int mapId : TARGET_MAP_IDS) {
                MapleMap map = channel.getMapFactory().getMap(mapId);
                if (map != null) {
                    maps.add(map);
                } else {
                    log.warn("Artificial player target map is unavailable: {}", mapId);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to load artificial player target maps", e);
        }
        return maps;
    }

    private ArtificialPlayer createBot(MapleMap map, int index) {
        Client mockClient = Client.createMock();
        mockClient.setAccID(FIRST_BOT_ID + index);
        mockClient.setWorld(DEFAULT_WORLD);
        mockClient.setChannel(DEFAULT_CHANNEL);

        Character character = Character.getDefault(mockClient);
        mockClient.setPlayer(character);
        character.setId(FIRST_BOT_ID + index);

        Personality personality = Personality.values()[index % Personality.values().length];
        byte gender = (byte) (index % 2);
        character.setName(personality.namePrefix + (index + 1));
        character.setGender(gender);
        character.setHair(randomFrom(gender == 0 ? MALE_HAIR_IDS : FEMALE_HAIR_IDS));
        character.setFace(randomFrom(gender == 0 ? MALE_FACE_IDS : FEMALE_FACE_IDS));
        character.setJob(Job.BEGINNER);
        character.setLevel(10 + random.nextInt(21));
        character.setGMLevel(0);
        character.setMap(map);
        character.setMapId(map.getId());
        character.setLoggedIn(true);

        int spawnSlot = index / Math.max(1, activeMaps.size());
        Point position = townSpawnPoint(map, spawnSlot);
        character.setPosition(position);
        return new ArtificialPlayer(character, personality, spawnSlot, position);
    }

    private int randomFrom(int[] values) {
        return values[random.nextInt(values.length)];
    }

    private void prepareTownSpawnPlans(int totalCount) {
        townSpawnPlans.clear();
        int mapCount = Math.max(1, activeMaps.size());
        for (int mapIndex = 0; mapIndex < activeMaps.size(); mapIndex++) {
            MapleMap map = activeMaps.get(mapIndex);
            int count = totalCount / mapCount + (mapIndex < totalCount % mapCount ? 1 : 0);
            List<Point> planned = BotTownPlanner.sample(map, count + 3, random);
            townSpawnPlans.put(map.getId(), planned);
            log.debug("Artificial player town plan for {}: {} weighted spots", map.getId(), planned.size());
        }
    }

    private Point townSpawnPoint(MapleMap map, int slot) {
        List<Point> planned = townSpawnPlans.getOrDefault(map.getId(), List.of());
        for (int offset = 0; offset < planned.size(); offset++) {
            Point candidate = planned.get(Math.floorMod(slot + offset, planned.size()));
            if (isSpawnAvailable(map, candidate)) {
                return new Point(candidate);
            }
        }
        return spawnPoint(map, slot);
    }

    private Point spawnPoint(MapleMap map, int slot) {
        Point fallback = new Point(0, 0);
        for (int attempt = 0; attempt < SPAWN_ATTEMPTS; attempt++) {
            Portal portal = map.getPortal(Math.floorMod(slot + attempt, 9));
            if (portal == null) {
                portal = map.getPortal(0);
            }
            Point origin = portal == null ? fallback : portal.getPosition();
            int jitter = spawnJitter(slot, attempt);
            Point candidate = new Point(origin.x + jitter, origin.y);
            BotPlatform platform = BotPlatform.findBelow(map, candidate);
            if (platform != null) {
                candidate = platform.project(candidate.x);
            }
            fallback = candidate;
            if (isSpawnAvailable(map, candidate)) {
                return candidate;
            }
        }
        return fallback;
    }

    private int spawnJitter(int slot, int attempt) {
        if (attempt == 0) {
            return Math.floorMod(slot * 37, 49) - 24;
        }
        int distance = ((attempt + 1) / 2) * MIN_SPAWN_SPACING;
        return (attempt % 2 == 0) ? -distance : distance;
    }

    private boolean isSpawnAvailable(MapleMap map, Point candidate) {
        for (Character other : map.getAllPlayers()) {
            Point otherPosition = other.getPosition();
            if (otherPosition != null
                    && Math.abs(otherPosition.x - candidate.x) < MIN_SPAWN_SPACING
                    && Math.abs(otherPosition.y - candidate.y) < 24) {
                return false;
            }
        }
        return true;
    }

    private void tickAll() {
        bots.values().forEach(bot -> {
            try {
                bot.tick();
            } catch (Exception e) {
                log.warn("Artificial player tick failed: {}", bot.character().getName(), e);
            }
        });
    }

    private void transferBot(ArtificialPlayer bot) {
        List<MapleMap> maps = activeMaps;
        Character character = bot.character();
        MapleMap oldMap = character.getMap();
        if (maps.size() < 2 || oldMap == null || !character.isLoggedIn()) {
            return;
        }

        int oldIndex = maps.indexOf(oldMap);
        int offset = 1 + random.nextInt(maps.size() - 1);
        MapleMap targetMap = maps.get(Math.floorMod(oldIndex + offset, maps.size()));
        Point oldPosition = character.getPosition();
        Point targetPosition = townSpawnPoint(targetMap, bot.spawnSlot + random.nextInt(9));

        try {
            oldMap.removePlayer(character);
            character.setMap(targetMap);
            character.setMapId(targetMap.getId());
            character.setPosition(targetPosition);
            targetMap.addPlayer(character);
            bot.setHomePosition(targetMap, targetPosition);
            log.debug("Artificial player {} moved from {} to {}",
                    character.getName(), oldMap.getId(), targetMap.getId());
        } catch (Exception e) {
            log.warn("Failed to transfer artificial player {} from {} to {}",
                    character.getName(), oldMap.getId(), targetMap.getId(), e);
            rollbackTransfer(character, oldMap, oldPosition, targetMap);
        }
    }

    private void rollbackTransfer(Character character, MapleMap oldMap, Point oldPosition, MapleMap targetMap) {
        try {
            if (character.getMap() == targetMap) {
                targetMap.removePlayer(character);
            }
        } catch (Exception ignored) {
            // The target map may not have completed addPlayer.
        }
        try {
            character.setMap(oldMap);
            character.setMapId(oldMap.getId());
            character.setPosition(oldPosition);
            oldMap.addPlayer(character);
        } catch (Exception rollbackError) {
            character.setLoggedIn(false);
            log.error("Failed to roll back artificial player transfer: {}", character.getName(), rollbackError);
        }
    }

    private void removeBot(ArtificialPlayer bot) {
        synchronized (bot) {
            try {
                Character character = bot.character();
                MapleMap map = character.getMap();
                if (map != null && character.isLoggedIn()) {
                    map.removePlayer(character);
                }
                character.setLoggedIn(false);
            } catch (Exception e) {
                log.warn("Failed to remove artificial player: {}", bot.character().getName(), e);
            }
        }
    }

    private String mapSummary() {
        StringBuilder result = new StringBuilder();
        for (MapleMap map : activeMaps) {
            if (!result.isEmpty()) {
                result.append(" / ");
            }
            long count = bots.values().stream()
                    .filter(bot -> bot.character().getMap() == map)
                    .count();
            result.append(map.getId()).append('=').append(count);
        }
        return result.toString();
    }

    @PreDestroy
    public void shutdown() {
        stop();
        scheduler.shutdownNow();
    }

    private final class ArtificialPlayer {
        private final Character character;
        private final Personality personality;
        private final Random behaviorRandom = new Random();
        private final int spawnSlot;
        private Point homePosition;
        private BotPlatform platform;
        private int walkDirection;
        private int chatIndex;
        private long nextChatAt;
        private long nextMapChangeAt;
        private WanderPhase wanderPhase;
        private long wanderPhaseUntil;

        private ArtificialPlayer(Character character, Personality personality, int spawnSlot, Point homePosition) {
            this.character = character;
            this.personality = personality;
            this.spawnSlot = spawnSlot;
            this.homePosition = new Point(homePosition);
            this.platform = BotPlatform.findBelow(character.getMap(), homePosition);
            this.walkDirection = behaviorRandom.nextBoolean() ? 1 : -1;
            this.chatIndex = behaviorRandom.nextInt(personality.chatLines.length);
            long now = System.currentTimeMillis();
            this.nextChatAt = now + 8_000L + behaviorRandom.nextInt(8_001);
            this.wanderPhase = WanderPhase.DWELLING;
            this.wanderPhaseUntil = now + behaviorRandom.nextInt(1_501);
            scheduleNextMapChange(now);
        }

        private Character character() {
            return character;
        }

        private synchronized void tick() {
            MapleMap map = character.getMap();
            if (map == null || !character.isLoggedIn()) {
                return;
            }

            long now = System.currentTimeMillis();
            if (now >= nextMapChangeAt) {
                transferBot(this);
                scheduleNextMapChange(now);
                map = character.getMap();
                if (map == null || !character.isLoggedIn()) {
                    return;
                }
            }

            updateTownWander(map, now);

            if (now >= nextChatAt) {
                map.broadcastMessage(character, PacketCreator.getChatText(
                        character.getId(), personality.chatLines[chatIndex], false, 0), true);
                chatIndex = (chatIndex + 1) % personality.chatLines.length;
                nextChatAt = now + personality.minChatDelayMillis
                        + behaviorRandom.nextInt(personality.chatDelayVarianceMillis);
            }
        }

        private void updateTownWander(MapleMap map, long now) {
            if (now >= wanderPhaseUntil) {
                if (wanderPhase == WanderPhase.WALKING) {
                    wanderPhase = WanderPhase.DWELLING;
                    wanderPhaseUntil = now + MIN_DWELL_MILLIS
                            + Math.floorMod(behaviorRandom.nextLong(), DWELL_VARIANCE_MILLIS);
                    stopWalking();
                    return;
                }

                if (behaviorRandom.nextDouble() > personality.moveChance) {
                    wanderPhaseUntil = now + MIN_DWELL_MILLIS
                            + Math.floorMod(behaviorRandom.nextLong(), DWELL_VARIANCE_MILLIS);
                    stopWalking();
                    return;
                }

                chooseWalkDirection(map);
                wanderPhase = WanderPhase.WALKING;
                wanderPhaseUntil = now + MIN_WALK_MILLIS
                        + Math.floorMod(behaviorRandom.nextLong(), WALK_VARIANCE_MILLIS);
            }

            if (wanderPhase == WanderPhase.WALKING) {
                moveWithinHomeArea(map, now);
            } else {
                stopWalking();
            }
        }

        private void chooseWalkDirection(MapleMap map) {
            Point position = character.getPosition();
            int leftCrowd = 0;
            int rightCrowd = 0;
            for (Character other : map.getAllPlayers()) {
                if (other == character || other.getPosition() == null) {
                    continue;
                }
                Point otherPosition = other.getPosition();
                if (Math.abs(otherPosition.y - position.y) >= 48) {
                    continue;
                }
                int deltaX = otherPosition.x - position.x;
                if (deltaX > 0 && deltaX <= CROWD_PROBE_DISTANCE) {
                    rightCrowd++;
                } else if (deltaX < 0 && -deltaX <= CROWD_PROBE_DISTANCE) {
                    leftCrowd++;
                }
            }

            if (leftCrowd != rightCrowd) {
                walkDirection = leftCrowd < rightCrowd ? -1 : 1;
            } else {
                walkDirection = behaviorRandom.nextBoolean() ? 1 : -1;
            }
        }

        private long nextCrossPlatformAt;

        private void moveWithinHomeArea(MapleMap map, long now) {
            Point oldPosition = character.getPosition();
            if (platform == null) {
                moveOnFallbackPlane(map, oldPosition);
                return;
            }

            int left = platform.getLeftX();
            int right = platform.getRightX();
            if (right <= left) {
                moveOnFallbackPlane(map, oldPosition);
                return;
            }

            int distance = WALK_STEP_PIXELS;
            int nextX = oldPosition.x + walkDirection * distance;
            if (nextX < left || nextX > right) {
                BotPlatform continuation = findContinuation(map, platform, walkDirection);
                if (continuation != null) {
                    platform = continuation;
                    nextX = walkDirection > 0 ? continuation.getLeftX() : continuation.getRightX();
                } else {
                    BotCrossPlatformPlanner.Target target = now >= nextCrossPlatformAt
                            ? BotCrossPlatformPlanner.findTarget(map, platform, oldPosition, walkDirection)
                            : null;
                    if (target != null) {
                        moveAcrossPlatform(map, oldPosition, target, now);
                        return;
                    }
                    walkDirection = -walkDirection;
                    nextX = oldPosition.x + walkDirection * distance;
                    nextX = Math.max(left, Math.min(right, nextX));
                }
            }
            Point nextPosition = platform.project(nextX);
            int duration = movementDuration(oldPosition, nextPosition);
            character.setStance(walkDirection > 0 ? 2 : 3);
            map.movePlayer(character, nextPosition);
            broadcastWalkingMovement(map, nextPosition, platform.getFootholdId(), walkDirection, duration);
        }

        private void moveAcrossPlatform(
                MapleMap map,
                Point oldPosition,
                BotCrossPlatformPlanner.Target target,
                long now) {
            Point nextPosition = target.landing();
            int duration = Math.max(360, Math.min(700, movementDuration(oldPosition, nextPosition) + 120));
            int jumpStance = walkDirection > 0 ? 6 : 7;
            character.setStance(jumpStance);
            map.movePlayer(character, nextPosition);
            broadcastCrossPlatformMovement(
                    map,
                    nextPosition,
                    target.platform().getFootholdId(),
                    jumpStance,
                    duration);
            platform = target.platform();
            nextCrossPlatformAt = now + 900L;
        }

        /** Finds a nearby same-height foothold so town walking can cross short platform seams. */
        private BotPlatform findContinuation(MapleMap map, BotPlatform current, int direction) {
            int edgeX = direction > 0 ? current.getRightX() : current.getLeftX();
            int edgeY = current.project(edgeX).y;
            for (int offset = 8; offset <= MAX_PLATFORM_PROBE; offset += 8) {
                int probeX = edgeX + direction * offset;
                BotPlatform candidate = BotPlatform.findBelow(map, new Point(probeX, edgeY - 40));
                if (candidate == null || current.isSamePlatform(candidate)) {
                    continue;
                }
                int candidateY = candidate.project(probeX).y;
                if (Math.abs(candidateY - edgeY) <= MAX_PLATFORM_HEIGHT_DELTA) {
                    return candidate;
                }
            }
            return null;
        }

        private void moveOnFallbackPlane(MapleMap map, Point oldPosition) {
            int direction;
            if (oldPosition.x >= homePosition.x + personality.roamRadius) {
                direction = -1;
            } else if (oldPosition.x <= homePosition.x - personality.roamRadius) {
                direction = 1;
            } else {
                direction = behaviorRandom.nextBoolean() ? 1 : -1;
            }

            int distance = WALK_STEP_PIXELS;
            Point nextPosition = new Point(oldPosition.x + direction * distance, oldPosition.y);
            int duration = movementDuration(oldPosition, nextPosition);
            character.setStance(direction > 0 ? 2 : 3);
            map.movePlayer(character, nextPosition);
            broadcastWalkingMovement(map, nextPosition, 0, direction, duration);
        }

        private int movementDuration(Point from, Point to) {
            int distance = Math.max(1, (int) from.distance(to));
            return Math.max(180, Math.min(600, distance * 1000 / WALK_PIXELS_PER_SECOND));
        }

        private void broadcastWalkingMovement(
                MapleMap map,
                Point destination,
                int footholdId,
                int direction,
                int duration) {
            OutPacket movementData = new ByteBufOutPacket();
            movementData.writeByte(1); // movement command count
            movementData.writeByte(0); // absolute walking movement
            movementData.writePos(destination);
            movementData.writeShort(direction * WALK_PIXELS_PER_SECOND);
            movementData.writeShort(0);
            movementData.writeShort(footholdId);
            movementData.writeByte(direction > 0 ? 2 : 3); // walk right / walk left
            movementData.writeShort(duration);

            byte[] bytes = movementData.getBytes();
            Packet movement = PacketCreator.movePlayer(
                    character.getId(),
                    new ByteBufInPacket(Unpooled.wrappedBuffer(bytes)),
                    bytes.length);
            map.broadcastMessage(character, movement, false);
        }

        private void broadcastCrossPlatformMovement(
                MapleMap map,
                Point destination,
                int footholdId,
                int jumpStance,
                int duration) {
            OutPacket movementData = new ByteBufOutPacket();
            movementData.writeByte(1);
            // Relative jump packets accumulate against the client's last visual
            // location and can leave clientless bots suspended. An absolute
            // packet keeps the displayed landing point authoritative while the
            // jump stance still gives the transfer a jump animation.
            movementData.writeByte(0);
            movementData.writePos(destination);
            movementData.writeShort(walkDirection * WALK_PIXELS_PER_SECOND);
            movementData.writeShort(0);
            movementData.writeShort(footholdId);
            movementData.writeByte(jumpStance);
            movementData.writeShort(duration);

            byte[] bytes = movementData.getBytes();
            Packet movement = PacketCreator.movePlayer(
                    character.getId(),
                    new ByteBufInPacket(Unpooled.wrappedBuffer(bytes)),
                    bytes.length);
            map.broadcastMessage(character, movement, false);
        }

        private void stopWalking() {
            int stance = walkDirection > 0 ? 4 : 5;
            if (character.getStance() == stance) {
                return;
            }
            character.setStance(stance);
            character.broadcastStance();
        }

        private void setHomePosition(MapleMap map, Point homePosition) {
            this.homePosition = new Point(homePosition);
            this.platform = BotPlatform.findBelow(map, homePosition);
            long now = System.currentTimeMillis();
            this.wanderPhase = WanderPhase.DWELLING;
            this.wanderPhaseUntil = now + behaviorRandom.nextInt(1_501);
        }

        private void scheduleNextMapChange(long now) {
            nextMapChangeAt = now + MIN_MAP_STAY_MILLIS
                    + Math.floorMod(behaviorRandom.nextLong(), MAP_STAY_VARIANCE_MILLIS);
        }
    }

    private enum WanderPhase {
        WALKING,
        DWELLING
    }

    private enum Personality {
        EXPLORER(
                "\u5192\u9669\u5bb6",
                0.82,
                260,
                13_000,
                12_001,
                new String[]{
                        "\u4eca\u5929\u4e5f\u53bb\u5192\u9669\u5427\uff01",
                        "\u6709\u4eba\u4e00\u8d77\u7ec4\u961f\u5417\uff1f",
                        "\u6211\u60f3\u53bb\u770b\u770b\u9644\u8fd1\u7684\u5730\u56fe\u3002",
                        "\u7ec3\u7ea7\u4e4b\u524d\u5148\u8865\u5145\u4e00\u4e0b\u836f\u6c34\u3002"
                }),
        SOCIAL(
                "\u65c5\u884c\u8005",
                0.64,
                210,
                10_000,
                10_001,
                new String[]{
                        "\u4f60\u597d\uff0c\u4eca\u5929\u4e5f\u5728\u5192\u9669\u5417\uff1f",
                        "\u5c04\u624b\u6751\u4eca\u5929\u771f\u70ed\u95f9\u3002",
                        "\u5927\u5bb6\u7ec3\u7ea7\u52a0\u6cb9\uff01",
                        "\u6709\u9700\u8981\u5e2e\u5fd9\u7684\u5417\uff1f"
                }),
        TRADER(
                "\u5546\u5e97\u5ba2",
                0.45,
                150,
                16_000,
                16_001,
                new String[]{
                        "\u5e02\u573a\u91cc\u6709\u4ec0\u4e48\u597d\u4e1c\u897f\uff1f",
                        "\u6211\u5148\u6574\u7406\u4e00\u4e0b\u80cc\u5305\u3002",
                        "\u4eca\u5929\u80fd\u4e70\u5230\u5408\u9002\u7684\u88c5\u5907\u5417\uff1f",
                        "\u836f\u6c34\u548c\u5377\u8f74\u90fd\u8981\u51c6\u5907\u597d\u3002"
                }),
        RELAXED(
                "\u95f2\u901b\u8005",
                0.30,
                120,
                18_000,
                18_001,
                new String[]{
                        "\u6211\u53bb\u516c\u56ed\u8f6c\u4e00\u5708\u3002",
                        "\u7a0d\u5fae\u4f11\u606f\u4e00\u4e0b\u518d\u51fa\u53d1\u3002",
                        "\u4eca\u5929\u7684\u8282\u594f\u5f88\u8f7b\u677e\u3002",
                        "\u6162\u6162\u5192\u9669\u4e5f\u4e0d\u9519\u3002"
                });

        private final String namePrefix;
        private final double moveChance;
        private final int roamRadius;
        private final int minChatDelayMillis;
        private final int chatDelayVarianceMillis;
        private final String[] chatLines;

        Personality(
                String namePrefix,
                double moveChance,
                int roamRadius,
                int minChatDelayMillis,
                int chatDelayVarianceMillis,
                String[] chatLines) {
            this.namePrefix = namePrefix;
            this.moveChance = moveChance;
            this.roamRadius = roamRadius;
            this.minChatDelayMillis = minChatDelayMillis;
            this.chatDelayVarianceMillis = chatDelayVarianceMillis;
            this.chatLines = chatLines;
        }
    }

    private static final class BotThreadFactory implements ThreadFactory {
        private final AtomicInteger number = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "BeiDou-ArtificialPlayer-" + number.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }
}
