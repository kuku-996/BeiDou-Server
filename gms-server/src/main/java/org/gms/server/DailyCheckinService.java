package org.gms.server;

import org.gms.client.Character;
import org.gms.client.Client;
import org.gms.net.packet.Packet;
import org.gms.util.DatabaseConnection;
import org.gms.util.PacketCreator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentHashMap;

/** Server-authoritative 28-day daily check-in state and claim processing. */
public final class DailyCheckinService {
    private static final Logger log = LoggerFactory.getLogger(DailyCheckinService.class);
    private static final ConcurrentHashMap<Integer, Object> locks = new ConcurrentHashMap<>();

    public record Snapshot(int currentDay, int claimedMask, int claimableDay, long cooldownSeconds) {
    }

    public record ClaimResult(Snapshot snapshot, int claimedDay, boolean granted) {
    }

    private record StoredState(int day, int mask, long lastClaim) {
    }

    private DailyCheckinService() {
    }

    public static Snapshot snapshot(Character player) {
        if (!eligible(player)) {
            return new Snapshot(0, 0, 0, 0);
        }
        synchronized (lockFor(player.getId())) {
            return normalize(player.getId(), System.currentTimeMillis());
        }
    }

    public static ClaimResult claim(Client client, int requestedDay) {
        Character player = client == null ? null : client.getPlayer();
        if (!eligible(player)) {
            return new ClaimResult(new Snapshot(0, 0, 0, 0), 0, false);
        }

        synchronized (lockFor(player.getId())) {
            long now = System.currentTimeMillis();
            Snapshot before = normalize(player.getId(), now);
            if (before.claimableDay() == 0 || requestedDay != before.claimableDay()) {
                return new ClaimResult(before, 0, false);
            }
            if (!DailyCheckinRewards.grant(client, requestedDay)) {
                return new ClaimResult(before, 0, false);
            }

            int newMask = before.claimedMask() | (1 << (requestedDay - 1));
            if (!save(player.getId(), requestedDay, newMask, now)) {
                log.error("Daily check-in reward granted but state persistence failed: character={}, day={}",
                        player.getId(), requestedDay);
                return new ClaimResult(before, 0, false);
            }

            Snapshot after = new Snapshot(requestedDay, newMask, 0,
                    DailyCheckinConfigService.claimIntervalMillis() / 1000L);
            log.info("Daily check-in claimed: character={} name={} day={}",
                    player.getId(), player.getName(), requestedDay);
            return new ClaimResult(after, requestedDay, true);
        }
    }

    public static boolean eligible(Character player) {
        return DailyCheckinConfigService.isEnabled()
                && player != null && player.getLevel() >= DailyCheckinConfigService.minLevel();
    }

    public static Packet packet(Snapshot snapshot, int justClaimed) {
        return PacketCreator.dailyCheckinSnapshot(
                snapshot.currentDay(), snapshot.claimedMask(), justClaimed);
    }

    private static Snapshot normalize(int characterId, long now) {
        StoredState stored = load(characterId);
        int day = stored.day();
        int mask = stored.mask();
        long lastClaim = stored.lastClaim();
        int claimable = 0;
        long cooldownSeconds = 0;

        if (lastClaim <= 0L) {
            day = 0;
            mask = 0;
            claimable = 1;
        } else {
            long elapsed = Math.max(0L, now - lastClaim);
            long claimInterval = DailyCheckinConfigService.claimIntervalMillis();
            long resetAfter = DailyCheckinConfigService.resetAfterMillis();
            if (elapsed >= resetAfter || (day >= DailyCheckinRewards.CYCLE_DAYS && elapsed >= claimInterval)) {
                day = 0;
                mask = 0;
                lastClaim = 0L;
                claimable = 1;
                save(characterId, day, mask, lastClaim);
            } else if (elapsed >= claimInterval) {
                claimable = Math.min(day + 1, DailyCheckinRewards.CYCLE_DAYS);
            } else {
                cooldownSeconds = Math.max(1L, (claimInterval - elapsed + 999L) / 1000L);
            }
        }

        int viewDay = claimable > 0 ? claimable : day;
        return new Snapshot(viewDay, mask, claimable, cooldownSeconds);
    }

    private static StoredState load(int characterId) {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT checkinDay, claimedMask, lastClaim FROM daily_checkin_state WHERE characterId = ?")) {
            ps.setInt(1, characterId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new StoredState(
                            rs.getInt("checkinDay"),
                            rs.getInt("claimedMask"),
                            rs.getLong("lastClaim"));
                }
            }
        } catch (SQLException e) {
            log.error("Unable to load daily check-in state for character {}", characterId, e);
        }
        return new StoredState(0, 0, 0L);
    }

    private static boolean save(int characterId, int day, int mask, long lastClaim) {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT INTO daily_checkin_state (characterId, checkinDay, claimedMask, lastClaim) "
                             + "VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE "
                             + "checkinDay = VALUES(checkinDay), claimedMask = VALUES(claimedMask), "
                             + "lastClaim = VALUES(lastClaim)")) {
            ps.setInt(1, characterId);
            ps.setInt(2, day);
            ps.setInt(3, mask);
            ps.setLong(4, lastClaim);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            log.error("Unable to save daily check-in state for character {}", characterId, e);
            return false;
        }
    }

    private static Object lockFor(int characterId) {
        return locks.computeIfAbsent(characterId, ignored -> new Object());
    }
}
