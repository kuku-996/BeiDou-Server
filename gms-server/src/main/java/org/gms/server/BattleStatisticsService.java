package org.gms.server;

import org.gms.client.Character;
import org.gms.client.Client;
import org.gms.net.packet.Packet;
import org.gms.util.PacketCreator;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-login combat statistics. The state deliberately stays in memory: a new
 * start button always begins a new measurement and no character data is stored.
 */
public final class BattleStatisticsService {
    private static final ConcurrentHashMap<Integer, Session> SESSIONS = new ConcurrentHashMap<>();

    public record Snapshot(boolean active, int elapsedSeconds, int killedMobs,
                           int mesos, int experience) {
    }

    private static final class Session {
        private boolean active;
        private long startedAt;
        private int killedMobs;
        private int mesos;
        private int experience;

        private Snapshot snapshot(long now) {
            long elapsed = active ? Math.max(0L, (now - startedAt) / 1000L) : startedAt;
            return new Snapshot(active, clamp(elapsed), killedMobs, mesos, experience);
        }
    }

    private BattleStatisticsService() {
    }

    public static Snapshot snapshot(Character player) {
        if (player == null) return new Snapshot(false, 0, 0, 0, 0);
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
            session.mesos = 0;
            session.experience = 0;
        }
        sync(player);
    }

    public static void stop(Character player) {
        if (player == null) return;
        Session session = SESSIONS.computeIfAbsent(player.getId(), ignored -> new Session());
        synchronized (session) {
            if (session.active) {
                session.startedAt = Math.max(0L, (System.currentTimeMillis() - session.startedAt) / 1000L);
                session.active = false;
            }
        }
        sync(player);
    }

    public static void recordMobKill(Character player) {
        update(player, session -> session.killedMobs = increment(session.killedMobs, 1));
    }

    public static void recordMesoGain(Character player, int amount) {
        if (amount > 0) update(player, session -> session.mesos = increment(session.mesos, amount));
    }

    public static void recordExperienceGain(Character player, long amount) {
        if (amount > 0) update(player, session -> session.experience = increment(session.experience, amount));
    }

    public static Packet packet(Snapshot snapshot) {
        return PacketCreator.battleStatisticsSnapshot(snapshot.active(), snapshot.elapsedSeconds(),
                snapshot.killedMobs(), snapshot.mesos(), snapshot.experience());
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
        if (changed) sync(player);
    }

    private static void sync(Character player) {
        Client client = player.getClient();
        if (client != null) client.sendPacket(packet(snapshot(player)));
    }

    private static int increment(int current, long amount) {
        return clamp((long) current + amount);
    }

    private static int clamp(long value) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, value));
    }

    @FunctionalInterface
    private interface SessionUpdate {
        void apply(Session session);
    }
}
