package com.startupvalidationbot.offering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.startupvalidationbot.offering.OfferingDomain.Candidate;
import com.startupvalidationbot.offering.OfferingDomain.Match;
import com.startupvalidationbot.offering.OfferingDomain.MatchStatus;
import com.startupvalidationbot.offering.OfferingDomain.Status;

@SpringBootTest(properties = "radar.run-token=offering-test-token")
@AutoConfigureMockMvc
@Transactional
class OfferingStoreIntegrationTest {
    @Autowired OfferingStore store;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mockMvc;

    @Test
    void v12AppliesAndOfferingUpsertIsAccessionIdempotent() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"version\"='12'", Integer.class)).isEqualTo(1);
        long companyId = company("Acme Technologies", "acme.example");
        Match confirmed = new Match(companyId, MatchStatus.CONFIRMED, 100, "Exact name and domain.");
        var first = store.upsert(candidate("0001234567-26-000001", "C", LocalDate.now(), "020-12345"), confirmed);
        var repeated = store.upsert(candidate("0001234567-26-000001", "C", LocalDate.now(), "020-12345"), confirmed);
        assertThat(first.created()).isTrue();
        assertThat(repeated.created()).isFalse();
        assertThat(store.list(null, null, null, companyId)).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM radar_offering_filings", Integer.class)).isEqualTo(1);
    }

    @Test
    void linksAmendmentsByFileNumberAndPersistsLifecycleChanges() {
        long companyId = company("Acme Technologies", "acme.example");
        Match confirmed = new Match(companyId, MatchStatus.CONFIRMED, 90, "Exact name.");
        store.upsert(candidate("0001234567-26-000001", "C", LocalDate.of(2026, 8, 1), "020-12345"), confirmed);
        store.upsert(candidate("0001234567-26-000002", "C-W", LocalDate.of(2026, 8, 20), "020-12345"), confirmed);
        List<OfferingDomain.Offering> offerings = store.list(null, null, "CONFIRMED", companyId);
        assertThat(offerings).hasSize(1);
        assertThat(offerings.get(0).status()).isEqualTo(Status.WITHDRAWN);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM radar_offering_filings", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT change_type FROM radar_company_changes", String.class))
                .contains("NEW_OFFERING", "OFFERING_WITHDRAWN");

        store.upsert(candidate("0001234567-26-000001", "C", LocalDate.of(2026, 8, 1), "020-12345"), confirmed);
        assertThat(store.list(null, null, "CONFIRMED", companyId)).singleElement()
                .satisfies(offering -> {
                    assertThat(offering.status()).isEqualTo(Status.WITHDRAWN);
                    assertThat(offering.accessionNumber()).isEqualTo("0001234567-26-000002");
                });
    }

    @Test
    void offeringApisRequireBrowserAuthenticationAndWorkerCannotReadThem() throws Exception {
        mockMvc.perform(get("/api/radar/offerings")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/radar/offerings").header("X-Radar-Run-Token", "offering-test-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void diagnosticsExposeLatestOfferingJobCounts() {
        LocalDateTime started = LocalDateTime.now().minusSeconds(2);
        jdbc.update("""
                INSERT INTO radar_job_runs (job_type, idempotency_key, status, summary_json, started_at, completed_at)
                VALUES ('offering-discovery', 'diagnostics-test', 'COMPLETED', ?, ?, ?)
                """, "{\"processed\":42,\"created\":3,\"updated\":5,\"errorCount\":1}", started, started.plusSeconds(1));

        var diagnostics = store.diagnostics();
        assertThat(diagnostics.recordsInspected()).isEqualTo(42);
        assertThat(diagnostics.newOfferings()).isEqualTo(3);
        assertThat(diagnostics.updatedOfferings()).isEqualTo(5);
        assertThat(diagnostics.errorCount()).isEqualTo(1);
    }

    private long company(String name, String domain) {
        LocalDateTime now = LocalDateTime.now();
        jdbc.update("""
                INSERT INTO radar_companies (name,normalized_name,domain,website_url,description,sector,categories_json,
                  aliases_json,radar_score,personal_score,score_reasoning,source_count,first_seen_at,last_seen_at,ignored,
                  accelerator,accelerator_batch,created_at,updated_at)
                VALUES (?,?,?,?,?,'Unknown','[]','[]',0,0,'',0,?,?,false,'','',?,?)
                """, name, com.startupvalidationbot.radar.CompanyIdentity.normalizeName(name), domain,
                "https://" + domain, "", now, now, now, now);
        return jdbc.queryForObject("SELECT id FROM radar_companies WHERE domain=?", Long.class, domain);
    }

    private Candidate candidate(String accession, String form, LocalDate filingDate, String fileNumber) {
        return new Candidate("Acme Technologies, Inc.", "0001234567", "https://acme.example", "Wefunder",
                "Wefunder Portal LLC", null, null,
                "https://www.sec.gov/Archives/edgar/data/1234567/" + accession.replace("-", "") + "/" + accession + "-index.html",
                accession, fileNumber, form, filingDate, "Crowd SAFE", new BigDecimal("100"),
                new BigDecimal("100000"), new BigDecimal("500000"), null,
                LocalDate.of(2026, 12, 31), null, "TEST", Map.of("form", form));
    }
}
