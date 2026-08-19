package com.startupvalidationbot.radar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.startupvalidationbot.radar.RadarRequests.ManualDiscovery;
import com.startupvalidationbot.radar.intel.ChangeSignificance.Tier;
import com.startupvalidationbot.radar.intel.InteractionSignal;
import com.startupvalidationbot.radar.intel.SnapshotChangeDetector.DetectedChange;
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
    private RadarIntelStore intelStore;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

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
    void requiresAuthenticationForEveryRadarReadAndProtectsAdminJobs() throws Exception {
        var company = discoveryService.ingestManual(discovery("Public Radar Company", "https://public-radar.test"));
        mockMvc.perform(get("/api/radar/health")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
        // This is a private application: every Radar data read requires a credential.
        mockMvc.perform(get("/api/radar/companies")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/radar/companies/{id}", company.id())).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/radar/sources")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/radar/trends")).andExpect(status().isUnauthorized());

        // The non-admin projection still withholds personal scoring and raw snapshot/source text.
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

    @Test
    void detectsAcceleratorBatchChangesFromPersistedSnapshots() {
        var source = store.upsertSource("accelerator-fixture", "RSS", "Accelerator fixture",
                "https://fixture.test/feed", true);
        var first = new RadarDomain.Candidate(source.sourceKey(), "batch-labs", "Batch Labs",
                "https://batch-labs.test", "Developer infrastructure.", "Software", List.of("DevTools"),
                "New York", 2025, "https://fixture.test/batch-labs", LocalDateTime.now(),
                "Batch Labs joined Y Combinator W25.", "Y Combinator", "W25");
        long companyId = store.upsertCompany(first).company().id();
        store.saveDiscoveryAndSnapshot(companyId, source, first);

        var second = new RadarDomain.Candidate(source.sourceKey(), "batch-labs", "Batch Labs",
                "https://batch-labs.test", "Developer infrastructure.", "Software", List.of("DevTools"),
                "New York", 2025, "https://fixture.test/batch-labs", LocalDateTime.now(),
                "Batch Labs joined Y Combinator S25.", "Y Combinator", "S25");
        store.upsertCompany(second);
        var saved = store.saveDiscoveryAndSnapshot(companyId, source, second);

        assertThat(saved.changes()).extracting(change -> change.changeType())
                .contains("ACCELERATOR");
    }

    @Test
    void ordersRecentlyFundedCompaniesByEventTimeRatherThanCompanyId() {
        var newer = discoveryService.ingestManual(discovery("Newer Funding", "https://newer-funding.test"));
        var older = discoveryService.ingestManual(discovery("Older Funding", "https://older-funding.test"));
        var change = new DetectedChange("FUNDING_ROUND", Tier.MAJOR, "Funding round", "None", "Seed round",
                "New runway");
        intelStore.recordChanges(newer.id(), null, List.of(change));
        intelStore.recordChanges(older.id(), null, List.of(change));
        jdbc.update("UPDATE radar_company_changes SET detected_at = ? WHERE company_id = ?",
                LocalDateTime.now().minusHours(1), newer.id());
        jdbc.update("UPDATE radar_company_changes SET detected_at = ? WHERE company_id = ?",
                LocalDateTime.now().minusDays(2), older.id());

        assertThat(intelStore.companiesWithRecentFunding(7, 10))
                .containsSubsequence(newer.id(), older.id());
    }

    @Test
    void redactsCredentialBearingUrlsEverywhereInTheRadarExport() throws Exception {
        var source = store.upsertSource("secret-fixture", "RSS", "Secret fixture",
                "https://feed.test/rss?apikey=SOURCE-SECRET", true);
        var candidate = new RadarDomain.Candidate(source.sourceKey(), "secret-company", "Secret Company",
                "https://secret-company.test?token=WEBSITE-SECRET", "Public company description.", "Software",
                List.of("DevTools"), "New York", 2025,
                "https://news.test/secret-company?token=DISCOVERY-SECRET", LocalDateTime.now(),
                "Public launch text.", "", "");
        long companyId = store.upsertCompany(candidate).company().id();
        store.saveDiscoveryAndSnapshot(companyId, source, candidate);
        store.saveResearchSource(companyId, "RSS", "Public source",
                "https://research.test/item?key=RESEARCH-SECRET", "Public excerpt", true);
        store.watchCompany(companyId, "See https://notes.test/item?key=NOTES-SECRET", null);

        String json = objectMapper.writeValueAsString(store.exportRadar());

        assertThat(json).doesNotContain("SOURCE-SECRET", "WEBSITE-SECRET", "DISCOVERY-SECRET",
                "RESEARCH-SECRET", "NOTES-SECRET");
        assertThat(json).contains("<redacted>");
    }

    @Test
    void restoredCompaniesAreNotPermanentlySuppressedByHistoricalIgnoreSignals() {
        var company = discoveryService.ingestManual(discovery("Restored Company", "https://restored.test"));
        intelStore.recordSignal(company.id(), InteractionSignal.IGNORE);

        store.ignoreCompany(company.id(), true);
        assertThat(intelStore.signalSummary(company.id()).ignored()).isTrue();

        store.ignoreCompany(company.id(), false);
        assertThat(intelStore.signalSummary(company.id()).ignored()).isFalse();
        assertThat(intelStore.signalTotals()).anySatisfy(total -> {
            assertThat(total.signalType()).isEqualTo("IGNORE");
            assertThat(total.total()).isEqualTo(1);
        });
    }

    @Test
    void trendVelocityIncludesPriorWindowCompaniesThatWereNotSeenRecently() {
        var recentOne = discoveryService.ingestManual(discovery("Recent One", "https://recent-one.test"));
        var recentTwo = discoveryService.ingestManual(discovery("Recent Two", "https://recent-two.test"));
        var prior = discoveryService.ingestManual(discovery("Prior Company", "https://prior-company.test"));
        LocalDateTime now = LocalDateTime.now();
        jdbc.update("UPDATE radar_companies SET first_seen_at = ?, last_seen_at = ? WHERE id IN (?, ?)",
                now.minusDays(10), now.minusDays(1), recentOne.id(), recentTwo.id());
        jdbc.update("UPDATE radar_companies SET first_seen_at = ?, last_seen_at = ? WHERE id = ?",
                now.minusDays(45), now.minusDays(40), prior.id());

        trendService.rebuild();

        assertThat(trendService.listDetailed()).filteredOn(trend -> trend.key().equals("grid-software"))
                .singleElement().satisfies(trend -> {
                    assertThat(trend.recentDiscoveries()).isEqualTo(2);
                    assertThat(trend.priorDiscoveries()).isEqualTo(1);
                });
    }

    private static ManualDiscovery discovery(String name, String website) {
        return new ManualDiscovery(name, website,
                "Grid software for energy infrastructure with enterprise customers.", "Energy",
                List.of("Grid Software"), "New York", 2025, website + "/source", null, null);
    }
}
