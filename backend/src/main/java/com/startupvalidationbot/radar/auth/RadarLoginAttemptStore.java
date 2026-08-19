package com.startupvalidationbot.radar.auth;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Durable storage for failed-login bookkeeping.
 *
 * Everything here is derived from request metadata only. Attempted passwords are never written,
 * logged or hashed into the client key.
 */
@Repository
public class RadarLoginAttemptStore {
    private final JdbcTemplate jdbc;

    public RadarLoginAttemptStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<AttemptRecord> find(String clientKey) {
        return jdbc.query("""
                SELECT client_key, failures, window_started_at, blocked_until
                FROM radar_login_attempts WHERE client_key = ?
                """, (rs, row) -> new AttemptRecord(rs.getString("client_key"), rs.getInt("failures"),
                        rs.getTimestamp("window_started_at").toLocalDateTime(),
                        rs.getTimestamp("blocked_until") == null ? null
                                : rs.getTimestamp("blocked_until").toLocalDateTime()),
                clientKey).stream().findFirst();
    }

    /**
     * Records a failure and returns the resulting state. The read-modify-write is performed under a
     * row lock so two concurrent attempts cannot both observe the same failure count.
     */
    @Transactional
    public AttemptRecord recordFailure(String clientKey, LocalDateTime now, Duration window, int maxAttempts,
            Duration lockout) {
        ensureRow(clientKey, now);

        AttemptRecord locked = jdbc.query("""
                SELECT client_key, failures, window_started_at, blocked_until
                FROM radar_login_attempts WHERE client_key = ? FOR UPDATE
                """, (rs, row) -> new AttemptRecord(rs.getString("client_key"), rs.getInt("failures"),
                        rs.getTimestamp("window_started_at").toLocalDateTime(),
                        rs.getTimestamp("blocked_until") == null ? null
                                : rs.getTimestamp("blocked_until").toLocalDateTime()),
                clientKey).stream().findFirst().orElse(new AttemptRecord(clientKey, 0, now, null));

        boolean windowExpired = locked.windowStartedAt().plus(window).isBefore(now);
        int failures = (windowExpired ? 0 : locked.failures()) + 1;
        LocalDateTime windowStartedAt = windowExpired ? now : locked.windowStartedAt();
        LocalDateTime blockedUntil = failures >= maxAttempts ? now.plus(lockout) : null;

        jdbc.update("""
                UPDATE radar_login_attempts
                   SET failures = ?, window_started_at = ?, blocked_until = ?, updated_at = ?
                 WHERE client_key = ?
                """, failures, windowStartedAt, blockedUntil, now, clientKey);

        return new AttemptRecord(clientKey, failures, windowStartedAt, blockedUntil);
    }

    public void clear(String clientKey) {
        jdbc.update("DELETE FROM radar_login_attempts WHERE client_key = ?", clientKey);
    }

    /** Drops rows that are no longer blocking and have not been touched recently. */
    public int deleteStale(LocalDateTime now, Duration retention) {
        return jdbc.update("""
                DELETE FROM radar_login_attempts
                 WHERE updated_at < ?
                   AND (blocked_until IS NULL OR blocked_until < ?)
                """, now.minus(retention), now);
    }

    private void ensureRow(String clientKey, LocalDateTime now) {
        try {
            jdbc.update("""
                    INSERT INTO radar_login_attempts (client_key, failures, window_started_at, updated_at)
                    VALUES (?, 0, ?, ?)
                    """, clientKey, now, now);
        } catch (DuplicateKeyException ignored) {
            // Another request created the row first; the FOR UPDATE read below serialises us.
        }
    }

    public record AttemptRecord(String clientKey, int failures, LocalDateTime windowStartedAt,
            LocalDateTime blockedUntil) {
        public boolean isBlockedAt(LocalDateTime now) {
            return blockedUntil != null && blockedUntil.isAfter(now);
        }
    }
}
