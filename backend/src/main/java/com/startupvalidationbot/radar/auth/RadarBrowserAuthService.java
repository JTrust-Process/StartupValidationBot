package com.startupvalidationbot.radar.auth;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.startupvalidationbot.radar.auth.RadarAdminSessionStore.IssuedSession;
import com.startupvalidationbot.radar.auth.RadarAdminSessionStore.Session;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Service
public class RadarBrowserAuthService {
    public static final String COOKIE_NAME = "radar_admin_session";

    private final RadarAdminSessionStore sessions;
    private final String passwordHash;
    private final Duration sessionLifetime;
    private final boolean secureCookie;
    private final String sameSite;

    public RadarBrowserAuthService(RadarAdminSessionStore sessions,
            @Value("${radar.auth.admin-password-hash:}") String passwordHash,
            @Value("${radar.auth.session-hours:8}") long sessionHours,
            @Value("${radar.auth.secure-cookie:true}") boolean secureCookie,
            @Value("${radar.auth.same-site:Strict}") String sameSite) {
        this.sessions = sessions;
        this.passwordHash = passwordHash == null ? "" : passwordHash.trim();
        this.sessionLifetime = Duration.ofHours(Math.max(1, Math.min(sessionHours, 24)));
        this.secureCookie = secureCookie;
        this.sameSite = requireSameSite(sameSite);
    }

    public BrowserSession login(char[] password, HttpServletResponse response) {
        if (passwordHash.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "RADAR_ADMIN_PASSWORD_HASH is not configured");
        }
        try {
            if (!RadarPasswordHasher.verify(password, passwordHash)) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid login credentials");
            }
            sessions.deleteExpired();
            IssuedSession issued = sessions.issue(sessionLifetime);
            response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie(issued.token(), sessionLifetime).toString());
            return new BrowserSession(true, issued.expiresAt());
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    public BrowserSession status(HttpServletRequest request) {
        return session(request).map(value -> new BrowserSession(true, value.expiresAt()))
                .orElseGet(() -> new BrowserSession(false, null));
    }

    public Optional<Session> session(HttpServletRequest request) {
        return sessions.validate(cookieValue(request));
    }

    public void requireSession(HttpServletRequest request) {
        if (session(request).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Radar admin session is invalid or expired");
        }
    }

    public void logout(HttpServletRequest request, HttpServletResponse response) {
        sessions.revoke(cookieValue(request));
        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie("", Duration.ZERO).toString());
    }

    private ResponseCookie sessionCookie(String value, Duration maxAge) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite(sameSite)
                .path("/api/radar")
                .maxAge(maxAge)
                .build();
    }

    private static String cookieValue(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return "";
        return Arrays.stream(cookies).filter(cookie -> COOKIE_NAME.equals(cookie.getName()))
                .map(Cookie::getValue).findFirst().orElse("");
    }

    private static String requireSameSite(String value) {
        if (value == null) return "Strict";
        return switch (value.trim().toLowerCase()) {
            case "strict" -> "Strict";
            case "lax" -> "Lax";
            default -> throw new IllegalArgumentException("Radar auth SameSite must be Strict or Lax");
        };
    }

    public record BrowserSession(boolean authenticated, LocalDateTime expiresAt) {
    }
}
