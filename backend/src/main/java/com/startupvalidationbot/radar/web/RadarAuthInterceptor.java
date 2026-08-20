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

    /**
     * The minimum public surface for a private single-user application.
     *
     * Only liveness and the session bootstrap are anonymous. Every Radar data read - companies,
     * company detail, sources and trends - now requires the browser session, because the Fly backend
     * is a public HTTPS service and those responses expose the full research dataset and the
     * configured source list.
     *
     * There is deliberately no fail-open branch: when RADAR_ADMIN_PASSWORD_HASH is unset no session
     * can ever validate, so these routes stay closed rather than reverting to anonymous access.
     */
    private static boolean isPublic(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        if ("GET".equalsIgnoreCase(method)) {
            return path.equals("/api/radar/health")
                    || path.equals("/api/radar/auth/session");
        }
        return "POST".equalsIgnoreCase(method)
                && (path.equals("/api/radar/auth/login") || path.equals("/api/radar/auth/logout"));
    }

    private static boolean isSafeMethod(String method) {
        return "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method);
    }
}
