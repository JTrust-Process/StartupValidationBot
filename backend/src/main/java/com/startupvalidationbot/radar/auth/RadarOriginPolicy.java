package com.startupvalidationbot.radar.auth;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;

@Component
public class RadarOriginPolicy {
    private final String allowedOrigin;

    public RadarOriginPolicy(@Value("${radar.auth.browser-origin:http://localhost:5173}") String allowedOrigin) {
        this.allowedOrigin = normalize(allowedOrigin);
    }

    public void requireAllowed(HttpServletRequest request) {
        String origin = normalize(request.getHeader("Origin"));
        if (allowedOrigin.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "RADAR_BROWSER_ORIGIN is not configured");
        }
        if (!allowedOrigin.equals(origin)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Browser origin is not allowed");
        }
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) return "";
        try {
            URI uri = URI.create(value.trim());
            if (uri.getScheme() == null || uri.getHost() == null || uri.getRawUserInfo() != null
                    || (uri.getPath() != null && !uri.getPath().isBlank() && !"/".equals(uri.getPath()))
                    || uri.getQuery() != null || uri.getFragment() != null) {
                return "";
            }
            int port = uri.getPort();
            return uri.getScheme().toLowerCase() + "://" + uri.getHost().toLowerCase()
                    + (port < 0 ? "" : ":" + port);
        } catch (IllegalArgumentException error) {
            return "";
        }
    }
}
