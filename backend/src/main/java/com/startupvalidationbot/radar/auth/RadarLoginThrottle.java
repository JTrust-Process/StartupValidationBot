package com.startupvalidationbot.radar.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.startupvalidationbot.radar.auth.RadarLoginAttemptStore.AttemptRecord;

/**
 * Login throttling backed by PostgreSQL so lockouts survive deploys and Fly machine auto-stop.
 *
 * Attempted passwords are never passed to, stored by, or logged from this class.
 */
@Component
public class RadarLoginThrottle {
    private final RadarLoginAttemptStore attempts;
    private final int maxAttempts;
    private final Duration window;
    private final Duration lockout;
    private final Duration retention;
    private final Clock clock;

    @Autowired
    public RadarLoginThrottle(RadarLoginAttemptStore attempts,
            @Value("${radar.auth.max-login-attempts:5}") int maxAttempts,
            @Value("${radar.auth.login-window-minutes:15}") long windowMinutes,
            @Value("${radar.auth.login-lockout-minutes:15}") long lockoutMinutes,
            @Value("${radar.auth.attempt-retention-hours:72}") long retentionHours) {
        this(attempts, Math.max(2, maxAttempts), Duration.ofMinutes(Math.max(1, windowMinutes)),
                Duration.ofMinutes(Math.max(1, lockoutMinutes)), Duration.ofHours(Math.max(1, retentionHours)),
                Clock.systemUTC());
    }

    /** Deterministic-clock constructor used by tests and by restart-persistence checks. */
    public RadarLoginThrottle(RadarLoginAttemptStore attempts, int maxAttempts, Duration window, Duration lockout,
            Duration retention, Clock clock) {
        this.attempts = attempts;
        this.maxAttempts = maxAttempts;
        this.window = window;
        this.lockout = lockout;
        this.retention = retention;
        this.clock = clock;
    }

    public void requireAllowed(String clientKey) {
        LocalDateTime now = now();
        if (attempts.find(clientKey).filter(record -> record.isBlockedAt(now)).isPresent()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many login attempts. Try again later.");
        }
    }

    public void recordFailure(String clientKey) {
        LocalDateTime now = now();
        AttemptRecord record = attempts.recordFailure(clientKey, now, window, maxAttempts, lockout);
        if (record.isBlockedAt(now)) {
            attempts.deleteStale(now, retention);
        }
    }

    public void recordSuccess(String clientKey) {
        attempts.clear(clientKey);
        attempts.deleteStale(now(), retention);
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneId.systemDefault());
    }
}
