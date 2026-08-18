package com.startupvalidationbot.radar.web;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.startupvalidationbot.radar.auth.RadarBrowserAuthService;
import com.startupvalidationbot.radar.auth.RadarOriginPolicy;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class RadarAuthInterceptor implements HandlerInterceptor {
    private final RadarTokenGuard tokenGuard;
    private final RadarBrowserAuthService browserAuth;
    private final RadarOriginPolicy originPolicy;

    public RadarAuthInterceptor(RadarTokenGuard tokenGuard, RadarBrowserAuthService browserAuth,
            RadarOriginPolicy originPolicy) {
        this.tokenGuard = tokenGuard;
        this.browserAuth = browserAuth;
        this.originPolicy = originPolicy;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        if (isPublic(request)) return true;
        if (tokenGuard.matches(request.getHeader("Authorization"), request.getHeader("X-Radar-Run-Token"))) {
            request.setAttribute("radarAuthentication", "WORKER_TOKEN");
            return true;
        }
        browserAuth.requireSession(request);
        if (!isSafeMethod(request.getMethod())) originPolicy.requireAllowed(request);
        request.setAttribute("radarAuthentication", "BROWSER_SESSION");
        return true;
    }

    private static boolean isPublic(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        if ("GET".equalsIgnoreCase(method)) {
            return path.equals("/api/radar/health")
                    || path.equals("/api/radar/companies")
                    || path.matches("/api/radar/companies/\\d+")
                    || path.equals("/api/radar/sources")
                    || path.equals("/api/radar/trends")
                    || path.equals("/api/radar/auth/session");
        }
        return "POST".equalsIgnoreCase(method)
                && (path.equals("/api/radar/auth/login") || path.equals("/api/radar/auth/logout"));
    }

    private static boolean isSafeMethod(String method) {
        return "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method);
    }
}
