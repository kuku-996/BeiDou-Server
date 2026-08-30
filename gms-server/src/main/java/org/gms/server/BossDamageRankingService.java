package org.gms.server;

import org.gms.client.Character;
import org.gms.server.life.Monster;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Tracks damage dealt to one live boss instance. Weak monster keys make the
 * session disappear naturally after the map disposes the boss.
 */
public final class BossDamageRankingService {
    private static final int MAX_ENTRIES = 30;
    private static final Map<Monster, Session> SESSIONS = new WeakHashMap<>();

    public record Entry(String name, long damage, long dps,
                        int contributionBasisPoints) {
    }

    public record Snapshot(int mobId, int objectId, long currentHp, long maxHp,
                           int elapsedSeconds, List<Entry> entries) {
    }

    private static final class DamageRecord {
        private String name;
        private long damage;

        private DamageRecord(String name) {
            this.name = name;
        }
    }

    private static final class Session {
        private long startedAt;
        private final Map<Integer, DamageRecord> damageByCharacter =
                new HashMap<>();
    }

    private BossDamageRankingService() {
    }

    public static void recordDamage(Monster boss, Character player,
                                    long appliedDamage) {
        if (boss == null || player == null || appliedDamage <= 0L ||
                !boss.hasBossHPBar()) {
            return;
        }

        Session session;
        synchronized (SESSIONS) {
            session = SESSIONS.computeIfAbsent(boss, ignored -> new Session());
        }
        synchronized (session) {
            if (session.startedAt == 0L) {
                session.startedAt = System.currentTimeMillis();
            }
            DamageRecord record = session.damageByCharacter.computeIfAbsent(
                    player.getId(), ignored -> new DamageRecord(player.getName()));
            record.name = player.getName();
            record.damage = safeAdd(record.damage, appliedDamage);
        }
    }

    public static Snapshot snapshot(Monster boss) {
        if (boss == null) {
            return new Snapshot(0, 0, 0L, 0L, 0, List.of());
        }

        Session session;
        synchronized (SESSIONS) {
            session = SESSIONS.get(boss);
        }
        if (session == null) {
            return new Snapshot(boss.getId(), boss.getObjectId(), boss.getHp(),
                    boss.getMaxHp(), 0, List.of());
        }

        synchronized (session) {
            long now = System.currentTimeMillis();
            int elapsedSeconds = session.startedAt == 0L ? 0 :
                    clampInt(Math.max(0L, (now - session.startedAt) / 1000L));
            long totalDamage = 0L;
            for (DamageRecord record : session.damageByCharacter.values()) {
                totalDamage = safeAdd(totalDamage, record.damage);
            }

            final long divisor = Math.max(1L, elapsedSeconds);
            final long total = totalDamage;
            List<Entry> entries = new ArrayList<>(
                    session.damageByCharacter.size());
            for (DamageRecord record : session.damageByCharacter.values()) {
                int contribution = total == 0L ? 0 : (int) Math.min(10000L,
                        Math.round(record.damage * 10000.0 / total));
                entries.add(new Entry(record.name, record.damage,
                        record.damage / divisor, contribution));
            }
            entries.sort(Comparator.comparingLong(Entry::damage).reversed()
                    .thenComparing(Entry::name));
            if (entries.size() > MAX_ENTRIES) {
                entries = new ArrayList<>(entries.subList(0, MAX_ENTRIES));
            }
            return new Snapshot(boss.getId(), boss.getObjectId(), boss.getHp(),
                    boss.getMaxHp(), elapsedSeconds, List.copyOf(entries));
        }
    }

    private static int clampInt(long value) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, value));
    }

    private static long safeAdd(long current, long amount) {
        return current > Long.MAX_VALUE - amount ? Long.MAX_VALUE :
                current + amount;
    }
}
