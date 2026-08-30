package org.gms.server;

import org.gms.client.Character;
import org.gms.client.Client;
import org.gms.net.packet.Packet;
import org.gms.util.PacketCreator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-login combat statistics. Sessions deliberately stay in memory and are
 * reset whenever the player presses the start button.
 */
public final class BattleStatisticsService {
    private static final long SYNC_INTERVAL_MS = 500L;
    private static final int MAX_SKILLS_IN_SNAPSHOT = 50;
    private static final ConcurrentHashMap<Integer, Session> SESSIONS = new ConcurrentHashMap<>();

    public record SkillSnapshot(int skillId, long totalDamage, int useCount,
                                int hitCount, int maxHit, int minHit) {
    }

    public record Snapshot(boolean active, int elapsedSeconds, int killedMobs,
                           long mesos, long experience, long totalDamage,
                           List<SkillSnapshot> skills) {
    }

    private static final class SkillStats {
        private long totalDamage;
        private int useCount;
        private int hitCount;
        private int maxHit;
        private int minHit = Integer.MAX_VALUE;

        private void record(List<Integer> damageLines) {
            boolean used = false;
            for (int damage : damageLines) {
                if (damage <= 0) continue;
                used = true;
                totalDamage = safeAdd(totalDamage, damage);
                hitCount = increment(hitCount, 1);
                maxHit = Math.max(maxHit, damage);
                minHit = Math.min(minHit, damage);
            }
            if (used) useCount = increment(useCount, 1);
        }

        private SkillSnapshot snapshot(int skillId) {
            return new SkillSnapshot(skillId, totalDamage, useCount, hitCount,
                    maxHit, minHit == Integer.MAX_VALUE ? 0 : minHit);
        }
    }

    private static final class Session {
        private boolean active;
        private long startedAt;
        private int killedMobs;
        private long mesos;
        private long experience;
        private long totalDamage;
        private final Map<Integer, SkillStats> skills = new HashMap<>();
        private long lastSyncAt;
        private boolean syncScheduled;
        private int syncGeneration;

        private Snapshot snapshot(long now) {
            long elapsed = active ? Math.max(0L, (now - startedAt) / 1000L) : startedAt;
            List<SkillSnapshot> skillSnapshots = new ArrayList<>(skills.size());
            for (Map.Entry<Integer, SkillStats> entry : skills.entrySet()) {
                skillSnapshots.add(entry.getValue().snapshot(entry.getKey()));
            }
            skillSnapshots.sort(Comparator
                    .comparingLong(SkillSnapshot::totalDamage).reversed()
                    .thenComparingInt(SkillSnapshot::skillId));
            if (skillSnapshots.size() > MAX_SKILLS_IN_SNAPSHOT) {
                skillSnapshots = new ArrayList<>(
                        skillSnapshots.subList(0, MAX_SKILLS_IN_SNAPSHOT));
            }
            return new Snapshot(active, clampInt(elapsed), killedMobs, mesos,
                    experience, totalDamage, List.copyOf(skillSnapshots));
        }
    }

    private BattleStatisticsService() {
    }

    public static Snapshot snapshot(Character player) {
        if (player == null) {
            return new Snapshot(false, 0, 0, 0L, 0L, 0L, List.of());
        }
        Session session = SESSIONS.computeIfAbsent(player.getId(), ignored -> new Session());
        synchronized (session) {
            return session.snapshot(System.currentTimeMillis());
        }
    }

    public static void start(Character player) {
        if (player == null) return;
        Session session = SESSIONS.computeIfAbsent(player.getId(), ignored -> new Session());
        synchronized (session) {
            session.active = true;
            session.startedAt = System.currentTimeMillis();
            session.killedMobs = 0;
            session.mesos = 0L;
            session.experience = 0L;
            session.totalDamage = 0L;
            session.skills.clear();
            session.lastSyncAt = 0L;
            session.syncScheduled = false;
            session.syncGeneration++;
        }
        requestSync(player, session, true);
    }

    public static void stop(Character player) {
        if (player == null) return;
        Session session = SESSIONS.computeIfAbsent(player.getId(), ignored -> new Session());
        synchronized (session) {
            if (session.active) {
                session.startedAt = Math.max(0L,
                        (System.currentTimeMillis() - session.startedAt) / 1000L);
                session.active = false;
            }
            session.syncScheduled = false;
            session.syncGeneration++;
        }
        requestSync(player, session, true);
    }

    public static void recordMobKill(Character player) {
        update(player, session -> session.killedMobs = increment(session.killedMobs, 1));
    }

    public static void recordMesoGain(Character player, int amount) {
        if (amount > 0) update(player,
                session -> session.mesos = safeAdd(session.mesos, amount));
    }

    public static void recordExperienceGain(Character player, long amount) {
        if (amount > 0) update(player,
                session -> session.experience = safeAdd(session.experience, amount));
    }

    /** Records one accepted attack packet after server-side damage validation. */
    public static void recordAttackDamage(Character player, int skillId,
                                          List<Integer> appliedDamageLines) {
        if (appliedDamageLines == null || appliedDamageLines.isEmpty()) return;
        update(player, session -> {
            SkillStats stats = session.skills.computeIfAbsent(skillId,
                    ignored -> new SkillStats());
            long before = stats.totalDamage;
            stats.record(appliedDamageLines);
            session.totalDamage = safeAdd(session.totalDamage,
                    stats.totalDamage - before);
        });
    }

    public static Packet packet(Snapshot snapshot) {
        return PacketCreator.battleStatisticsSnapshot(snapshot);
    }

    private static void update(Character player, SessionUpdate update) {
        if (player == null) return;
        Session session = SESSIONS.get(player.getId());
        if (session == null) return;
        boolean changed = false;
        synchronized (session) {
            if (session.active) {
                update.apply(session);
                changed = true;
            }
        }
        if (changed) requestSync(player, session, false);
    }

    private static void requestSync(Character player, Session session, boolean immediate) {
        final long now = System.currentTimeMillis();
        long delay = 0L;
        int generation = 0;
        boolean sendNow = false;
        synchronized (session) {
            if (immediate || now - session.lastSyncAt >= SYNC_INTERVAL_MS) {
                session.lastSyncAt = now;
                session.syncScheduled = false;
                sendNow = true;
            } else if (!session.syncScheduled) {
                session.syncScheduled = true;
                delay = Math.max(1L, SYNC_INTERVAL_MS - (now - session.lastSyncAt));
                generation = session.syncGeneration;
            }
        }
        if (sendNow) {
            sync(player);
        } else if (delay > 0L) {
            final int expectedGeneration = generation;
            TimerManager.getInstance().schedule(
                    () -> flushScheduledSync(player, session, expectedGeneration), delay);
        }
    }

    private static void flushScheduledSync(Character player, Session session, int generation) {
        synchronized (session) {
            if (!session.syncScheduled || session.syncGeneration != generation) return;
            session.syncScheduled = false;
            session.lastSyncAt = System.currentTimeMillis();
        }
        sync(player);
    }

    private static void sync(Character player) {
        Client client = player.getClient();
        if (client != null && client.getPlayer() == player) {
            client.sendPacket(packet(snapshot(player)));
        }
    }

    private static int increment(int current, int amount) {
        return clampInt((long) current + amount);
    }

    private static int clampInt(long value) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, value));
    }

    private static long safeAdd(long current, long amount) {
        if (amount <= 0L) return current;
        return current > Long.MAX_VALUE - amount ? Long.MAX_VALUE : current + amount;
    }

    @FunctionalInterface
    private interface SessionUpdate {
        void apply(Session session);
    }
}
