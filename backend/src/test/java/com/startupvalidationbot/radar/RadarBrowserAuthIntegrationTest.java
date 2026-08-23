package com.startupvalidationbot.radar;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
                .andExpect(jsonPath("$.authenticated").value(false))
                .andExpect(jsonPath("$.configured").value(true));
    }

    @Test
    void protectsModernAndLegacyDealDataFromAnonymousAndWorkerAccess() throws Exception {
        mockMvc.perform(get("/api/deal-workspaces")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/deal-workspaces")
                .contentType("application/json").content("{\"companyName\":\"Acme\",\"platform\":\"Republic\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/deals")).andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/deal-workspaces")
                .header("Authorization", "Bearer test-worker-token"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/deals")
                .header("X-Radar-Run-Token", "test-worker-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void allowsAuthenticatedDealReadsAndValidOriginWritesButRejectsWrongOrigin() throws Exception {
        MockCookie cookie = loginCookie("198.51.100.22");

        mockMvc.perform(get("/api/deal-workspaces").cookie(cookie)).andExpect(status().isOk());
        MvcResult created = mockMvc.perform(post("/api/deal-workspaces")
                .cookie(cookie).header("Origin", "https://radar.example")
                .contentType("application/json")
                .content("{\"companyName\":\"Acme\",\"platform\":\"Republic\",\"documents\":[{\"title\":\"Form C\"}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.documents[0].title").value("Form C"))
                .andReturn();
        long id = ((Number) com.jayway.jsonpath.JsonPath.read(
                created.getResponse().getContentAsString(), "$.id")).longValue();

        mockMvc.perform(put("/api/deal-workspaces/" + id)
                .cookie(cookie).header("Origin", "https://attacker.example")
                .contentType("application/json")
                .content("{\"companyName\":\"Acme\",\"platform\":\"Republic\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/deal-workspaces/" + id)
                .cookie(cookie).header("Origin", "https://radar.example"))
                .andExpect(status().isNoContent());
    }

    @Test
    void rejectsMalformedOrIncompleteAuthenticatedDealPayloads() throws Exception {
        MockCookie cookie = loginCookie("198.51.100.23");
        mockMvc.perform(post("/api/deal-workspaces")
                .cookie(cookie).header("Origin", "https://radar.example")
                .contentType("application/json").content("[1,2,3]"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/deal-workspaces")
                .cookie(cookie).header("Origin", "https://radar.example")
                .contentType("application/json").content("{\"companyName\":\"Missing platform\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/deal-workspaces")
                .cookie(cookie).header("Origin", "https://radar.example")
                .contentType("application/json").content("{bad json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAnonymousRadarDataReads() throws Exception {
        mockMvc.perform(get("/api/radar/companies")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/radar/companies/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/radar/sources")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/radar/trends")).andExpect(status().isUnauthorized());
        // Liveness and the session bootstrap remain anonymous so the SPA can render a login screen.
        mockMvc.perform(get("/api/radar/health")).andExpect(status().isOk());
        mockMvc.perform(get("/api/radar/auth/session")).andExpect(status().isOk());
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
                .andExpect(header().stringValues("Set-Cookie", org.hamcrest.Matchers.hasItem(containsString("Path=/api"))))
                .andExpect(header().stringValues("Set-Cookie", org.hamcrest.Matchers.hasItem(containsString("Path=/api/radar"))))
                .andExpect(header().string("Set-Cookie", containsString("SameSite=Strict")))
                .andReturn();
        MockCookie cookie = sessionCookie(login);

        mockMvc.perform(get("/api/radar/admin/companies").cookie(cookie)).andExpect(status().isOk());
        // Authenticated browser reads of the Radar data surface succeed with the same session cookie.
        mockMvc.perform(get("/api/radar/companies").cookie(cookie)).andExpect(status().isOk());
        mockMvc.perform(get("/api/radar/sources").cookie(cookie)).andExpect(status().isOk());
        mockMvc.perform(get("/api/radar/trends").cookie(cookie)).andExpect(status().isOk());
        mockMvc.perform(get("/api/radar/auth/session").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.expiresAt").exists());

        mockMvc.perform(post("/api/radar/auth/logout").header("Origin", "https://radar.example").cookie(cookie))
                .andExpect(status().isNoContent())
                .andExpect(header().stringValues("Set-Cookie", org.hamcrest.Matchers.everyItem(containsString("Max-Age=0"))));
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

        // A different client is unaffected: one attacker cannot lock the real user out.
        mockMvc.perform(post("/api/radar/auth/login")
                .with(request -> { request.setRemoteAddr("198.51.100.7"); return request; })
                .header("Origin", "https://radar.example")
                .contentType("application/json")
                .content("{\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/radar/admin/companies")
                .header("Authorization", "Bearer test-worker-token"))
                .andExpect(status().isOk());
    }

    private MockCookie loginCookie(String address) throws Exception {
        MvcResult login = mockMvc.perform(post("/api/radar/auth/login")
                .with(request -> { request.setRemoteAddr(address); return request; })
                .header("Origin", "https://radar.example")
                .contentType("application/json")
                .content("{\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return sessionCookie(login);
    }

    private MockCookie sessionCookie(MvcResult login) {
        var issued = java.util.Arrays.stream(login.getResponse().getCookies())
                .filter(cookie -> RadarBrowserAuthService.COOKIE_NAME.equals(cookie.getName()))
                .filter(cookie -> "/api".equals(cookie.getPath()))
                .filter(cookie -> !cookie.getValue().isBlank())
                .findFirst().orElseThrow();
        return new MockCookie(issued.getName(), issued.getValue());
    }
}
