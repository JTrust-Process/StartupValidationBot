package com.startupvalidationbot.radar.auth;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.startupvalidationbot.radar.ContentHash;

@Repository
public class RadarAdminSessionStore {
    private final JdbcTemplate jdbc;
    private final SecureRandom secureRandom = new SecureRandom();

    public RadarAdminSessionStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public IssuedSession issue(Duration lifetime) {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plus(lifetime);
        jdbc.update("""
                INSERT INTO radar_admin_sessions (token_hash, created_at, last_seen_at, expires_at)
                VALUES (?, ?, ?, ?)
                """, ContentHash.sha256(token), now, now, expiresAt);
        return new IssuedSession(token, expiresAt);
    }

    @Transactional
    public Optional<Session> validate(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        LocalDateTime now = LocalDateTime.now();
        Optional<Session> session = jdbc.query("""
                SELECT created_at, last_seen_at, expires_at
                FROM radar_admin_sessions
                WHERE token_hash = ? AND revoked_at IS NULL AND expires_at > ?
                """, (rs, row) -> new Session(rs.getTimestamp("created_at").toLocalDateTime(),
                        rs.getTimestamp("last_seen_at").toLocalDateTime(),
                        rs.getTimestamp("expires_at").toLocalDateTime()),
                ContentHash.sha256(token), now).stream().findFirst();
        session.ifPresent(value -> jdbc.update("""
                UPDATE radar_admin_sessions SET last_seen_at = ?
                WHERE token_hash = ? AND revoked_at IS NULL
                """, now, ContentHash.sha256(token)));
        return session;
    }

    public void revoke(String token) {
        if (token == null || token.isBlank()) return;
        jdbc.update("""
                UPDATE radar_admin_sessions SET revoked_at = ?
                WHERE token_hash = ? AND revoked_at IS NULL
                """, LocalDateTime.now(), ContentHash.sha256(token));
    }

    public void deleteExpired() {
        jdbc.update("DELETE FROM radar_admin_sessions WHERE expires_at < ? OR revoked_at IS NOT NULL",
                LocalDateTime.now().minusDays(7));
    }

    public record IssuedSession(String token, LocalDateTime expiresAt) {
    }

    public record Session(LocalDateTime createdAt, LocalDateTime lastSeenAt, LocalDateTime expiresAt) {
    }
}
