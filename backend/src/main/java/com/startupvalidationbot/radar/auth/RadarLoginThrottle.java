package com.startupvalidationbot.radar.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class RadarLoginThrottle {
    private final ConcurrentHashMap<String, AttemptState> attempts = new ConcurrentHashMap<>();
    private final int maxAttempts;
    private final Duration window;
    private final Duration lockout;
    private final Clock clock;

    @Autowired
    public RadarLoginThrottle(@Value("${radar.auth.max-login-attempts:5}") int maxAttempts,
            @Value("${radar.auth.login-window-minutes:15}") long windowMinutes,
            @Value("${radar.auth.login-lockout-minutes:15}") long lockoutMinutes) {
        this(Math.max(2, maxAttempts), Duration.ofMinutes(Math.max(1, windowMinutes)),
                Duration.ofMinutes(Math.max(1, lockoutMinutes)), Clock.systemUTC());
    }

    RadarLoginThrottle(int maxAttempts, Duration window, Duration lockout, Clock clock) {
        this.maxAttempts = maxAttempts;
        this.window = window;
        this.lockout = lockout;
        this.clock = clock;
    }

    public void requireAllowed(String clientKey) {
        AttemptState state = attempts.get(clientKey);
        if (state != null && state.blockedUntil() != null && state.blockedUntil().isAfter(clock.instant())) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many login attempts. Try again later.");
        }
    }

    public void recordFailure(String clientKey) {
        Instant now = clock.instant();
        attempts.compute(clientKey, (key, existing) -> {
            AttemptState current = existing;
            if (current == null || current.windowStartedAt().plus(window).isBefore(now)) {
                current = new AttemptState(0, now, null);
            }
            int failures = current.failures() + 1;
            Instant blockedUntil = failures >= maxAttempts ? now.plus(lockout) : null;
            return new AttemptState(failures, current.windowStartedAt(), blockedUntil);
        });
    }

    public void recordSuccess(String clientKey) {
        attempts.remove(clientKey);
    }

    private record AttemptState(int failures, Instant windowStartedAt, Instant blockedUntil) {
    }
}
