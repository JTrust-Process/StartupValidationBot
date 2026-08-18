package com.startupvalidationbot.radar;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.startupvalidationbot.radar.auth.RadarAdminSessionStore;
import com.startupvalidationbot.radar.auth.RadarBrowserAuthService;
import com.startupvalidationbot.radar.auth.RadarPasswordHasher;

@SpringBootTest(properties = {
        "radar.run-token=test-worker-token",
        "app.allowed-origins=https://radar.example",
        "radar.auth.browser-origin=https://radar.example",
        "radar.auth.secure-cookie=false"
})
@AutoConfigureMockMvc
@Transactional
class RadarBrowserAuthIntegrationTest {
    private static final String PASSWORD = "correct horse battery staple";
    private static final String PASSWORD_HASH = RadarPasswordHasher.hash(PASSWORD.toCharArray());

    @DynamicPropertySource
    static void authProperties(DynamicPropertyRegistry registry) {
        registry.add("radar.auth.admin-password-hash", () -> PASSWORD_HASH);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RadarAdminSessionStore sessions;

    @Test
    void rejectsUnauthenticatedAndInvalidSessions() throws Exception {
        mockMvc.perform(get("/api/radar/admin/companies")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/radar/admin/companies")
                .cookie(new MockCookie(RadarBrowserAuthService.COOKIE_NAME, "invalid-session")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/radar/auth/session")
                .cookie(new MockCookie(RadarBrowserAuthService.COOKIE_NAME, "invalid-session")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false));
    }

    @Test
    void logsInUsesHttpOnlySessionAndLogsOut() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/radar/auth/login")
                .header("Origin", "https://radar.example")
                .contentType("application/json")
                .content("{\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                .andExpect(header().string("Set-Cookie", containsString("SameSite=Strict")))
                .andReturn();
        String token = login.getResponse().getCookie(RadarBrowserAuthService.COOKIE_NAME).getValue();
        MockCookie cookie = new MockCookie(RadarBrowserAuthService.COOKIE_NAME, token);

        mockMvc.perform(get("/api/radar/admin/companies").cookie(cookie)).andExpect(status().isOk());
        mockMvc.perform(get("/api/radar/auth/session").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.expiresAt").exists());

        mockMvc.perform(post("/api/radar/auth/logout").header("Origin", "https://radar.example").cookie(cookie))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));
        mockMvc.perform(get("/api/radar/admin/companies").cookie(cookie)).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsExpiredSessionAndWrongOrigin() throws Exception {
        String expiredToken = sessions.issue(Duration.ofSeconds(-1)).token();
        mockMvc.perform(get("/api/radar/admin/companies")
                .cookie(new MockCookie(RadarBrowserAuthService.COOKIE_NAME, expiredToken)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/radar/auth/login")
                .header("Origin", "https://attacker.example")
                .contentType("application/json")
                .content("{\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void throttlesRepeatedInvalidPasswordsAndKeepsWorkerAuthSeparate() throws Exception {
        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(post("/api/radar/auth/login")
                    .with(request -> { request.setRemoteAddr("203.0.113.44"); return request; })
                    .header("Origin", "https://radar.example")
                    .contentType("application/json")
                    .content("{\"password\":\"wrong-password\"}"))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/api/radar/auth/login")
                .with(request -> { request.setRemoteAddr("203.0.113.44"); return request; })
                .header("Origin", "https://radar.example")
                .contentType("application/json")
                .content("{\"password\":\"wrong-password\"}"))
                .andExpect(status().isTooManyRequests());

        mockMvc.perform(get("/api/radar/admin/companies")
                .header("Authorization", "Bearer test-worker-token"))
                .andExpect(status().isOk());
    }
}
