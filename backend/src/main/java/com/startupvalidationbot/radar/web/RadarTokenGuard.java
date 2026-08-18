package com.startupvalidationbot.radar.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class RadarTokenGuard {
    private final String expectedToken;

    public RadarTokenGuard(@Value("${radar.run-token:}") String expectedToken) {
        this.expectedToken = expectedToken;
    }

    public void require(String authorization, String headerToken) {
        if (expectedToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "RADAR_RUN_TOKEN is not configured");
        }
        if (!matches(authorization, headerToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or missing Radar server token");
        }
    }

    public boolean matches(String authorization, String headerToken) {
        if (expectedToken.isBlank()) return false;
        String token = authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring("Bearer ".length()).trim()
                : value(headerToken);
        return MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),
                expectedToken.getBytes(StandardCharsets.UTF_8));
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}
