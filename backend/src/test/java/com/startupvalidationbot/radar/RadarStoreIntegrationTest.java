package com.startupvalidationbot.radar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.startupvalidationbot.radar.RadarRequests.ManualDiscovery;
import com.startupvalidationbot.radar.service.RadarDiscoveryService;
import com.startupvalidationbot.radar.service.RadarTrendService;

@SpringBootTest(properties = "radar.run-token=test-token")
@AutoConfigureMockMvc
@Transactional
class RadarStoreIntegrationTest {
    @Autowired
    private RadarDiscoveryService discoveryService;

    @Autowired
    private RadarStore store;

    @Autowired
    private RadarTrendService trendService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void deduplicatesByDomainAndPersistsWatchlistAndTrends() {
        var first = discoveryService.ingestManual(discovery("Signal Grid, Inc.", "https://www.signalgrid.test/a"));
        var duplicate = discoveryService.ingestManual(discovery("Signal Grid LLC", "https://signalgrid.test"));
        var second = discoveryService.ingestManual(discovery("Current Works", "https://currentworks.test"));

        assertThat(duplicate.id()).isEqualTo(first.id());
        assertThat(store.listCompanies()).hasSize(2);
        assertThat(store.getCompanyDetail(first.id()).snapshots()).hasSize(2);

        store.watchCompany(first.id(), "Track customer launches", null);
        assertThat(store.findCompany(first.id()).orElseThrow().watched()).isTrue();

        assertThat(trendService.rebuild()).isEqualTo(1);
        assertThat(trendService.list()).singleElement()
                .satisfies(trend -> assertThat(trend.companies()).extracting("id")
                        .containsExactlyInAnyOrder(first.id(), second.id()));
    }

    @Test
    void protectsRadarViewsAndKeepsSanitizedProjectionsSeparate() throws Exception {
        var company = discoveryService.ingestManual(discovery("Public Radar Company", "https://public-radar.test"));
        mockMvc.perform(get("/api/radar/health")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
        mockMvc.perform(get("/api/radar/companies")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/radar/companies").header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].personalScore").doesNotExist())
                .andExpect(jsonPath("$[0].watched").doesNotExist())
                .andExpect(jsonPath("$[0].ignored").doesNotExist());
        mockMvc.perform(get("/api/radar/companies/{id}", company.id())
                .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latestAnalysis.personalScore").doesNotExist())
                .andExpect(jsonPath("$.snapshots[0].snapshotJson").doesNotExist())
                .andExpect(jsonPath("$.researchSources[0].excerpt").doesNotExist());
        mockMvc.perform(get("/api/radar/admin/companies")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/radar/admin/companies").header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].personalScore").exists())
                .andExpect(jsonPath("$[0].watched").exists());
        mockMvc.perform(post("/api/radar/jobs/discovery").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/radar/jobs/discovery").contentType("application/json").content("{}")
                .header("Authorization", "Bearer test-token")).andExpect(status().isOk());
        mockMvc.perform(options("/api/radar/companies")
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk());
    }

    @Test
    void doesNotMergeDifferentDomainsThatShareACompanyName() {
        var first = discoveryService.ingestManual(discovery("Common Name", "https://first-common.test"));
        var second = discoveryService.ingestManual(discovery("Common Name, Inc.", "https://second-common.test"));

        assertThat(second.id()).isNotEqualTo(first.id());
    }

    @Test
    void preventsOverlappingJobsAndKeepsIdempotencyAfterCompletion() {
        var first = store.beginJob("discovery", "fixture-run", Duration.ofMinutes(30));
        var overlap = store.beginJob("discovery", "different-key", Duration.ofMinutes(30));

        assertThat(first.acquired()).isTrue();
        assertThat(overlap.acquired()).isFalse();
        assertThat(overlap.duplicate()).isFalse();

        store.completeJob("discovery", "fixture-run", first.leaseToken(), "COMPLETED", java.util.Map.of(), null);
        var duplicate = store.beginJob("discovery", "fixture-run", Duration.ofMinutes(30));
        assertThat(duplicate.acquired()).isFalse();
        assertThat(duplicate.duplicate()).isTrue();
    }

    private static ManualDiscovery discovery(String name, String website) {
        return new ManualDiscovery(name, website,
                "Grid software for energy infrastructure with enterprise customers.", "Energy",
                List.of("Grid Software"), "New York", 2025, website + "/source", null, null);
    }
}
