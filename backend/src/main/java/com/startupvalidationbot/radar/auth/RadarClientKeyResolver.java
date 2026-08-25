package com.startupvalidationbot.radar.auth;

import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Derives the throttling key for a login attempt.
 *
 * Browser traffic arrives through the same-origin Vercel proxy, so {@code getRemoteAddr()} is the
 * proxy's address for every visitor. Keying on it would put all attempts in one bucket, which lets
 * anyone lock the single legitimate user out of the application. When the deployment is known to sit
 * behind a proxy we therefore key on the client address the proxy forwards.
 *
 * A forwarded header is attacker-controlled if the backend is reached directly, so this is not a
 * trust boundary: it only distributes throttling buckets. The actual defence against password
 * guessing is the PBKDF2-SHA256 verifier at 310,000 iterations plus a strong password.
 */
@Component
public class RadarClientKeyResolver {
    private static final int MAX_KEY_LENGTH = 200;

    /** Set by our own Vercel proxy from the platform-provided client address. Not client settable. */
    public static final String PROXY_CLIENT_HEADER = "X-Radar-Client-Ip";

    private final boolean trustForwardedFor;

    public RadarClientKeyResolver(@Value("${radar.auth.trust-forwarded-for:true}") boolean trustForwardedFor) {
        this.trustForwardedFor = trustForwardedFor;
    }

    public String resolve(HttpServletRequest request) {
        if (trustForwardedFor) {
            String proxied = sanitizeAddress(request.getHeader(PROXY_CLIENT_HEADER));
            if (!proxied.isBlank()) return truncate("ip:" + proxied);

            String realIp = sanitizeAddress(request.getHeader("X-Real-IP"));
            if (!realIp.isBlank()) return truncate("ip:" + realIp);

            // Fall back to the right-most forwarded entry: it is appended by the closest proxy and is
            // therefore the hardest for a client to control.
            String forwarded = nearestForwardedAddress(request.getHeader("X-Forwarded-For"));
            if (!forwarded.isBlank()) return truncate("ip:" + forwarded);
        }
        String remote = request.getRemoteAddr();
        return truncate("ip:" + (remote == null || remote.isBlank() ? "unknown" : remote));
    }

    static String nearestForwardedAddress(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) return "";
        String[] parts = headerValue.split(",");
        return sanitizeAddress(parts[parts.length - 1]);
    }

    static String sanitizeAddress(String value) {
        if (value == null || value.isBlank()) return "";
        String first = value.trim().toLowerCase(Locale.ROOT);
        // Reject anything that is not plausibly an address so a hostile header cannot inflate the key
        // space with arbitrary text or smuggle SQL-looking values into logs.
        if (first.isEmpty() || first.length() > 64) return "";
        for (int index = 0; index < first.length(); index++) {
            char character = first.charAt(index);
            boolean allowed = (character >= 'a' && character <= 'f') || (character >= '0' && character <= '9')
                    || character == '.' || character == ':' || character == '[' || character == ']'
                    || character == '%';
            if (!allowed) return "";
        }
        return first;
    }

    private static String truncate(String value) {
        return value.length() <= MAX_KEY_LENGTH ? value : value.substring(0, MAX_KEY_LENGTH);
    }
}
