package com.startupvalidationbot.radar.web;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.startupvalidationbot.radar.auth.RadarBrowserAuthService;
import com.startupvalidationbot.radar.auth.RadarBrowserAuthService.BrowserSession;
import com.startupvalidationbot.radar.auth.RadarClientKeyResolver;
import com.startupvalidationbot.radar.auth.RadarLoginThrottle;
import com.startupvalidationbot.radar.auth.RadarOriginPolicy;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/radar/auth")
public class RadarAuthController {
    private final RadarBrowserAuthService auth;
    private final RadarLoginThrottle throttle;
    private final RadarOriginPolicy originPolicy;
    private final RadarClientKeyResolver clientKeys;

    public RadarAuthController(RadarBrowserAuthService auth, RadarLoginThrottle throttle,
            RadarOriginPolicy originPolicy, RadarClientKeyResolver clientKeys) {
        this.auth = auth;
        this.throttle = throttle;
        this.originPolicy = originPolicy;
        this.clientKeys = clientKeys;
    }

    @PostMapping("/login")
    public ResponseEntity<BrowserSession> login(@Valid @RequestBody LoginRequest login,
            HttpServletRequest request, HttpServletResponse response) {
        originPolicy.requireAllowed(request);
        String clientKey = clientKeys.resolve(request);
        throttle.requireAllowed(clientKey);
        try {
            BrowserSession session = auth.login(login.password().toCharArray(), response);
            throttle.recordSuccess(clientKey);
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(session);
        } catch (ResponseStatusException error) {
            if (error.getStatusCode() == HttpStatus.UNAUTHORIZED) throttle.recordFailure(clientKey);
            throw error;
        }
    }

    @GetMapping("/session")
    public ResponseEntity<BrowserSession> session(HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(auth.status(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        originPolicy.requireAllowed(request);
        auth.logout(request, response);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    public record LoginRequest(@NotBlank @Size(max = 256) String password) {
    }
}
