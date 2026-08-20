package com.startupvalidationbot.radar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "radar.run-token=fixture-worker-token",
        "radar.ai.enabled=false",
        "radar.demo-fixture.enabled=true"
})
@AutoConfigureMockMvc
@Transactional
class RadarEndToEndFixtureIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void runsSyntheticPipelineAndProducesSanitizedExportAndStatus() throws Exception {
        mockMvc.perform(post("/api/radar/admin/fixtures/synthetic")
                .header("Authorization", "Bearer fixture-worker-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deduplicated").value(true))
                .andExpect(jsonPath("$.snapshotCount", greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.radarScore", greaterThan(0)))
                .andExpect(jsonPath("$.personalScore", greaterThan(0)))
                .andExpect(jsonPath("$.watched").value(true))
                .andExpect(jsonPath("$.analysisType").value("DEEP_DIVE"))
                .andExpect(jsonPath("$.analysisOrigin").value("DETERMINISTIC"))
                .andExpect(jsonPath("$.trendCount", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.digestPreviewGenerated").value(true));

        mockMvc.perform(get("/api/radar/admin/status")
                .header("Authorization", "Bearer fixture-worker-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.databaseHealthy").value(true))
                .andExpect(jsonPath("$.lastEnrichmentRun").exists())
                .andExpect(jsonPath("$.lastDigest").exists())
                .andExpect(jsonPath("$.discoveriesProcessed", greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.aiProvider").value("groq"))
                .andExpect(jsonPath("$.routineModel").value("openai/gpt-oss-20b"));

        MvcResult export = mockMvc.perform(get("/api/radar/admin/export")
                .header("Authorization", "Bearer fixture-worker-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemaVersion").value("startup-radar-export-v1"))
                .andExpect(jsonPath("$.companies.length()", greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.discoveries.length()", greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.snapshots.length()", greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.analyses.length()", greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.watchlist.length()", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.researchSourceReferences.length()", greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.discoveries[0].rawText").doesNotExist())
                .andExpect(jsonPath("$.publicSources[0].configJson").doesNotExist())
                .andReturn();

        String body = export.getResponse().getContentAsString();
        assertThat(body).doesNotContain("fixture-worker-token", "RADAR_RUN_TOKEN", "GROQ_API_KEY",
                "radar_admin_sessions", "authorization");
    }
}
