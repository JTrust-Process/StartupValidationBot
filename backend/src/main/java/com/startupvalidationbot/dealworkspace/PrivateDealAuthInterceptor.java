package com.startupvalidationbot.dealworkspace;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.startupvalidationbot.radar.auth.RadarBrowserAuthService;
import com.startupvalidationbot.radar.auth.RadarOriginPolicy;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class PrivateDealAuthInterceptor implements HandlerInterceptor {
    private final RadarBrowserAuthService browserAuth;
    private final RadarOriginPolicy originPolicy;

    public PrivateDealAuthInterceptor(RadarBrowserAuthService browserAuth, RadarOriginPolicy originPolicy) {
        this.browserAuth = browserAuth;
        this.originPolicy = originPolicy;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        browserAuth.requireSession(request);
        if (!isSafeMethod(request.getMethod())) originPolicy.requireAllowed(request);
        request.setAttribute("dealAuthentication", "BROWSER_SESSION");
        return true;
    }

    private static boolean isSafeMethod(String method) {
        return "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method);
    }
}
