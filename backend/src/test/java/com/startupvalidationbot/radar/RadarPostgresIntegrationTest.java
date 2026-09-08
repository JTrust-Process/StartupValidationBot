package com.startupvalidationbot.radar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.startupvalidationbot.radar.RadarDomain.Candidate;
import com.startupvalidationbot.radar.RadarDomain.Company;
import com.startupvalidationbot.radar.RadarDomain.Source;
import com.startupvalidationbot.radar.RadarRequests.ManualDiscovery;
import com.startupvalidationbot.radar.RadarStore.JobStart;
import com.startupvalidationbot.radar.auth.RadarAdminSessionStore;
import com.startupvalidationbot.radar.auth.RadarLoginAttemptStore;
import com.startupvalidationbot.radar.auth.RadarLoginThrottle;
import com.startupvalidationbot.radar.service.RadarDiscoveryService;
import com.startupvalidationbot.radar.service.RadarScoringService;
import com.startupvalidationbot.radar.source.RssStartupSourceAdapter;
import com.startupvalidationbot.radar.source.SourceFetchException;
import com.startupvalidationbot.dealworkspace.DealWorkspaceStore;
import com.startupvalidationbot.diligence.DiligenceDomain.CampaignStatus;
import com.startupvalidationbot.diligence.DiligenceDomain.EvidenceClassification;
import com.startupvalidationbot.diligence.DiligenceDomain.FinancialPeriod;
import com.startupvalidationbot.diligence.DiligenceDomain.PacketStatus;
import com.startupvalidationbot.diligence.DiligenceDomain.PlatformCampaign;
import com.startupvalidationbot.diligence.DiligenceStore;
import com.startupvalidationbot.diligence.DiligenceStore.EvidenceDraft;
import com.startupvalidationbot.diligence.DiligenceStore.PacketDraft;
import com.startupvalidationbot.diligence.discovery.CampaignDiscoveryDomain.CampaignCandidate;
import com.startupvalidationbot.diligence.discovery.CampaignDiscoveryDomain.IdentityDecision;
import com.startupvalidationbot.diligence.discovery.CampaignDiscoveryDomain.IdentityStatus;
import com.startupvalidationbot.diligence.discovery.CampaignDiscoveryDomain.PlatformRun;
import com.startupvalidationbot.diligence.discovery.CampaignDiscoveryStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.startupvalidationbot.offering.OfferingDomain.Match;
import com.startupvalidationbot.offering.OfferingDomain.MatchStatus;
import com.startupvalidationbot.offering.OfferingMatchService;
import com.startupvalidationbot.offering.OfferingStore;
import com.startupvalidationbot.offering.intake.NativeOfferingDomain.NativeOfferingCandidate;
import com.startupvalidationbot.offering.intake.NativeOfferingDomain.ReconciliationStatus;
import com.startupvalidationbot.offering.intake.NativeOfferingDomain.Status;
import com.startupvalidationbot.offering.intake.NativeOfferingStore;

/**
 * Production-representative persistence coverage.
 *
 * The fast suite runs on H2, which cannot prove PostgreSQL behaviour for the things this system
 * actually depends on: Flyway V1-V10 applying in order, UNIQUE constraints on company identity and
 * analysis cache keys, row-locked job leases, and durable login throttling. Those run here against
 * the real engine.
 *
 * The class is skipped automatically when no Docker daemon is present, so it never breaks a build on
 * a machine without containers. Skipped is not the same as passing: a green build on such a machine
 * has compile-validated this suite only.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "radar.run-token=postgres-integration-token",
        "radar.ai.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class RadarPostgresIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        // Hibernate must never mutate the schema: Flyway owns it, including the legacy deal tables.
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RadarStore store;

    @Autowired
    private RadarDiscoveryService discoveryService;

    @Autowired
    private RadarScoringService scoringService;

    @Autowired
    private RadarAdminSessionStore sessions;

    @Autowired
    private RadarLoginAttemptStore loginAttempts;

    @Autowired
    private DealWorkspaceStore dealWorkspaces;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OfferingStore offeringStore;

    @Autowired
    private OfferingMatchService offeringMatcher;

    @Autowired
    private DiligenceStore diligenceStore;

    @Autowired
    private NativeOfferingStore nativeOfferingStore;

    @Autowired
    private CampaignDiscoveryStore campaignDiscoveryStore;

    @Test
    void appliesEveryMigrationAndValidatesTheJpaMappingAgainstIt() {
        List<String> applied = jdbc.queryForList(
                "SELECT script FROM flyway_schema_history WHERE success ORDER BY installed_rank", String.class);

        assertThat(applied)
                .anyMatch(script -> script.contains("V1__"))
                .anyMatch(script -> script.contains("V2__"))
                .anyMatch(script -> script.contains("V3__"))
                .anyMatch(script -> script.contains("V4__"))
                .anyMatch(script -> script.contains("V5__"))
                .anyMatch(script -> script.contains("V6__"));
        assertThat(applied).anyMatch(script -> script.contains("V9__"));
        assertThat(applied).anyMatch(script -> script.contains("V10__"));
        assertThat(applied).anyMatch(script -> script.contains("V11__"));
        assertThat(applied).anyMatch(script -> script.contains("V12__"));
        assertThat(applied).anyMatch(script -> script.contains("V13__"));
        assertThat(applied).anyMatch(script -> script.contains("V14__"));

        // V4 brought the legacy diligence tables under Flyway. The context booting with
        // ddl-auto=validate is itself the assertion that the JPA mapping matches them.
        assertThat(tableExists("deals")).isTrue();
        assertThat(tableExists("quick_screens")).isTrue();
        assertThat(tableExists("decisions")).isTrue();
        assertThat(tableExists("deep_diligence")).isTrue();
        assertThat(tableExists("reviews")).isTrue();
        assertThat(tableExists("radar_login_attempts")).isTrue();
        assertThat(tableExists("deal_workspaces")).isTrue();
        assertThat(tableExists("radar_diligence_packets")).isTrue();
        assertThat(tableExists("radar_notification_events")).isTrue();
        assertThat(tableExists("radar_campaign_discovery_results")).isTrue();
        assertThat(tableExists("radar_native_offering_candidates")).isTrue();
        assertThat(tableExists("radar_native_offering_source_state")).isTrue();
    }

    @Test
    void roundTripsCompleteDealWorkspaceJsonAndSupportsCrud() throws Exception {
        var request = objectMapper.readTree("""
                {
                  "id": 999,
                  "createdAt": "2000-01-01T00:00:00Z",
                  "updatedAt": "2000-01-01T00:00:00Z",
                  "companyName": "GridCool Systems",
                  "platform": "Republic",
                  "offeringUrl": "https://republic.example/gridcool",
                  "radarCompanyId": 42,
                  "offeringDiscoveryId": 77,
                  "secFilingUrl": "https://www.sec.gov/Archives/example",
                  "documents": [{"id": 1, "title": "Form C", "pastedText": "Nested text"}],
                  "evidenceClaims": [{"id": 1, "claim": "Revenue", "verified": true}],
                  "redFlags": {"illiquidityNotDisclosed": true},
                  "dealMemo": {"content": "Editable memo"},
                  "importRecords": [{"id": 1, "dealId": 999, "rawText": "Campaign copy"}]
                }
                """);

        var created = dealWorkspaces.create(request);
        long id = created.get("id").asLong();
        assertThat(id).isPositive().isNotEqualTo(999);
        assertThat(created.get("createdAt").asText()).isNotEqualTo("2000-01-01T00:00:00Z");
        assertThat(created.at("/importRecords/0/dealId").asLong()).isEqualTo(id);
        assertThat(created.at("/documents/0/pastedText").asText()).isEqualTo("Nested text");
        assertThat(created.get("radarCompanyId").asLong()).isEqualTo(42);
        assertThat(created.get("offeringDiscoveryId").asLong()).isEqualTo(77);

        var loaded = dealWorkspaces.find(id).orElseThrow();
        assertThat(loaded.at("/evidenceClaims/0/verified").asBoolean()).isTrue();
        assertThat(dealWorkspaces.list()).extracting(node -> node.get("id").asLong()).contains(id);

        ((com.fasterxml.jackson.databind.node.ObjectNode) loaded).put("platform", "Wefunder");
        var updated = dealWorkspaces.update(id, loaded);
        assertThat(updated.get("platform").asText()).isEqualTo("Wefunder");
        assertThat(updated.get("createdAt").asText()).isEqualTo(created.get("createdAt").asText());
        assertThat(updated.get("updatedAt").asText()).isNotEqualTo("2000-01-01T00:00:00Z");

        dealWorkspaces.delete(id);
        assertThat(dealWorkspaces.find(id)).isEmpty();
    }

    @Test
    void persistsIdempotentOfferingAndAmendmentHistoryOnPostgres() {
        var company = store.upsertCompany(new Candidate("postgres-offering", "offering-1",
                "Postgres Offering Co", "https://offering.example", "", "Fintech", List.of("fintech"),
                null, null, "https://example.com/source", LocalDateTime.now(), "")).company();
        Match match = new Match(company.id(), MatchStatus.CONFIRMED, 100, "Exact name and domain.");
        var first = offeringStore.upsert(offeringCandidate("0002099999-26-000001", "C", "020-99991",
                java.time.LocalDate.of(2026, 8, 1)), match);
        var repeated = offeringStore.upsert(offeringCandidate("0002099999-26-000001", "C", "020-99991",
                java.time.LocalDate.of(2026, 8, 1)), match);
        assertThat(offeringStore.attachCampaignUrl(first.offering().id(), "WEFUNDER",
                "https://wefunder.com/postgres-offering")).isTrue();
        offeringStore.upsert(offeringCandidate("0002099999-26-000002", "C-W", "020-99991",
                java.time.LocalDate.of(2026, 8, 20)), match);

        assertThat(first.created()).isTrue();
        assertThat(repeated.created()).isFalse();
        assertThat(offeringStore.list(null, null, "CONFIRMED", company.id())).singleElement()
                .satisfies(offering -> {
                    assertThat(offering.status().name()).isEqualTo("WITHDRAWN");
                    assertThat(offering.offeringUrl()).isEqualTo("https://wefunder.com/postgres-offering");
                });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM radar_offering_filings WHERE offering_id=?",
                Integer.class, first.offering().id())).isEqualTo(2);

        Match ambiguous = new Match(null, MatchStatus.AMBIGUOUS, 45,
                "Issuer name matches more than one tracked company.");
        offeringStore.upsert(offeringCandidate("0002088888-26-000001", "C", "020-88881",
                java.time.LocalDate.of(2026, 8, 22)), ambiguous);

        assertThat(offeringStore.list("ACTIVE", "Wefunder", "AMBIGUOUS", null)).singleElement()
                .satisfies(offering -> {
                    assertThat(offering.radarCompanyId()).isNull();
                    assertThat(offering.matchStatus()).isEqualTo(MatchStatus.AMBIGUOUS);
                });

        var storedIdentity = offeringStore.listStoredIdentities().stream()
                .filter(identity -> identity.offeringId() == first.offering().id()).findFirst().orElseThrow();
        offeringStore.updateMatch(storedIdentity.offeringId(), offeringMatcher.match(storedIdentity.issuerName(),
                storedIdentity.issuerWebsite(), store.listCompanies()));
        assertThat(offeringStore.find(first.offering().id())).get().satisfies(offering -> {
            assertThat(offering.matchStatus()).isEqualTo(MatchStatus.LIKELY);
            assertThat(offering.matchConfidence()).isEqualTo(85);
            assertThat(offering.matchReason()).contains("corroborating identity evidence is unavailable");
        });
    }

    @Test
    void persistsIdempotentDiligenceStateOnPostgres() {
        var company = store.upsertCompany(new Candidate("postgres-diligence", "diligence-1",
                "Postgres Diligence Co", "https://diligence.example", "", "Energy", List.of("energy"),
                null, null, "https://example.com/diligence-source", LocalDateTime.now(), "")).company();
        var offeringCandidate = new com.startupvalidationbot.offering.OfferingDomain.Candidate(
                "Postgres Diligence Co", "0002077777", "https://diligence.example", "Wefunder",
                "Wefunder Portal LLC", null, "https://wefunder.com/postgres-diligence",
                "https://www.sec.gov/Archives/edgar/data/2077777/000207777726000001/"
                        + "0002077777-26-000001-index.html",
                "0002077777-26-000001", "020-77771", "C", LocalDate.of(2026, 8, 25),
                "Crowd SAFE", new BigDecimal("100"), new BigDecimal("100000"),
                new BigDecimal("1235000"), "$12M cap", LocalDate.of(2026, 12, 31),
                new BigDecimal("640000"), "TEST", Map.of());
        long offeringId = offeringStore.upsert(offeringCandidate,
                new Match(company.id(), MatchStatus.CONFIRMED, 100, "Exact identity match.")).offering().id();

        assertThat(diligenceStore.eligibleOfferingIds(25)).contains(offeringId);

        LocalDateTime now = LocalDateTime.now();
        PlatformCampaign firstCampaign = new PlatformCampaign(null, offeringId, "WEFUNDER",
                "https://wefunder.com/postgres-diligence", "SEC_OFFERING_URL", 100, CampaignStatus.ACTIVE,
                "Postgres Diligence Co", "Crowd SAFE", new BigDecimal("100"), null, null,
                new BigDecimal("12000000"), null, new BigDecimal("100000"), new BigDecimal("1235000"),
                new BigDecimal("640000"), 814, LocalDate.of(2026, 12, 31), "Postgres Diligence",
                Map.of("amountRaised", "640000"), "campaign-one", now, now);
        diligenceStore.upsertCampaign(firstCampaign);
        diligenceStore.upsertCampaign(new PlatformCampaign(null, offeringId, "WEFUNDER",
                firstCampaign.campaignUrl(), "SEC_OFFERING_URL", 100, CampaignStatus.ACTIVE,
                firstCampaign.issuerName(), firstCampaign.securityType(), firstCampaign.minimumInvestment(),
                null, null, firstCampaign.valuationCap(), null, firstCampaign.targetAmount(),
                firstCampaign.maximumAmount(), new BigDecimal("650000"), 820, firstCampaign.deadline(),
                firstCampaign.headline(), Map.of("amountRaised", "650000"), "campaign-two", now, now));

        EvidenceDraft evidence = new EvidenceDraft("SEC_EDGAR", "https://www.sec.gov/diligence", "Form C",
                "revenue", "35992", "FY2025", EvidenceClassification.SEC_FILED_FACT, 95,
                "Filed revenue", Map.of());
        FinancialPeriod financial = new FinancialPeriod("FY2025", new BigDecimal("35992"), null,
                new BigDecimal("-14000"), new BigDecimal("9000"), new BigDecimal("25000"), null,
                null, null, null, "0002077777-26-000001", "https://www.sec.gov/diligence");
        PacketDraft firstPacket = new PacketDraft(company.id(), offeringId, PacketStatus.READY, "CONFIRMED",
                90, 95, "PostgreSQL diligence packet", List.of("Bull"), List.of("Bear"),
                List.of("Illiquidity"), List.of("Verify claims"), List.of(), List.of("Monitor amendments"),
                List.of("SEC_EDGAR"), List.of(), "packet-one", List.of(evidence), List.of(financial));
        long packetId = diligenceStore.savePacket(firstPacket).id();
        long repeatedPacketId = diligenceStore.savePacket(new PacketDraft(company.id(), offeringId,
                PacketStatus.READY, "CONFIRMED", 92, 96, "Updated PostgreSQL diligence packet",
                firstPacket.bullCase(), firstPacket.bearCase(), firstPacket.keyRisks(),
                firstPacket.unansweredQuestions(), firstPacket.discrepancies(), firstPacket.milestones(),
                firstPacket.sourcesChecked(), firstPacket.dataNotFound(), "packet-two", List.of(evidence),
                List.of(financial))).id();

        assertThat(repeatedPacketId).isEqualTo(packetId);
        assertThat(diligenceStore.findCampaign(offeringId)).get().extracting(PlatformCampaign::sourceFingerprint)
                .isEqualTo("campaign-two");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM radar_platform_campaigns WHERE offering_id=?",
                Integer.class, offeringId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM radar_diligence_packets WHERE offering_id=?",
                Integer.class, offeringId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM radar_diligence_evidence WHERE packet_id=?",
                Integer.class, packetId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM radar_diligence_financials WHERE packet_id=?",
                Integer.class, packetId)).isEqualTo(1);
        assertThat(diligenceStore.queueNotification("DILIGENCE_READY", "PACKET", packetId,
                "postgres-diligence-notification", "owner@example.com", "Subject", "Text", "<p>Text</p>"))
                .isTrue();
        assertThat(diligenceStore.queueNotification("DILIGENCE_READY", "PACKET", packetId,
                "postgres-diligence-notification", "owner@example.com", "Subject", "Text", "<p>Text</p>"))
                .isFalse();
    }

    @Test
    void persistsCampaignDiscoveryProvenanceAndCacheStateOnPostgres() {
        var company = store.upsertCompany(new Candidate("postgres-campaign-discovery", "campaign-1",
                "Postgres Campaign Co", "https://campaign-company.example", "", "Energy", List.of("energy"),
                null, null, "https://example.com/campaign-source", LocalDateTime.now(), "")).company();
        LocalDateTime nextEligible = LocalDateTime.now().plusHours(24);
        campaignDiscoveryStore.saveCheck(company.id(), null, "STARTENGINE", "identity-one", "FOUND", 2, 1,
                "One corroborated public campaign was found.", null, nextEligible);
        CampaignCandidate candidate = new CampaignCandidate("STARTENGINE",
                "https://www.startengine.com/offering/postgres-campaign", "Postgres Campaign Co",
                "campaign-company.example", "ACTIVE", "postgres-campaign", "PUBLIC_DIRECTORY", 85,
                Map.of("listingText", "Public listing evidence"));
        campaignDiscoveryStore.saveCandidate(company.id(), null, candidate,
                new IdentityDecision(IdentityStatus.CONFIRMED, 98, "Exact name and domain."));
        campaignDiscoveryStore.markPlatform(new PlatformRun("STARTENGINE", "PUBLIC_DIRECTORY", "OK",
                2, 1, 1, null));

        assertThat(campaignDiscoveryStore.cached(company.id(), "STARTENGINE", "identity-one",
                LocalDateTime.now())).isTrue();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM radar_campaign_discovery_results
                WHERE radar_company_id=? AND identity_status='CONFIRMED'
                """, Integer.class, company.id())).isEqualTo(1);
        assertThat(campaignDiscoveryStore.platformDiagnostics().stream()
                .filter(value -> value.platform().equals("STARTENGINE")).toList()).singleElement().satisfies(value -> {
            assertThat(value.platform()).isEqualTo("STARTENGINE");
            assertThat(value.resolved()).isEqualTo(1);
        });
    }

    private com.startupvalidationbot.offering.OfferingDomain.Candidate offeringCandidate(
            String accession, String form, String fileNumber, java.time.LocalDate date) {
        return new com.startupvalidationbot.offering.OfferingDomain.Candidate("Postgres Offering Co",
                "0002099999", "https://offering.example", "Wefunder", "Wefunder Portal LLC", null, null,
                "https://www.sec.gov/Archives/edgar/data/2099999/" + accession.replace("-", "") + "/"
                        + accession + "-index.html",
                accession, fileNumber, form, date, "Crowd SAFE", new java.math.BigDecimal("100"),
                new java.math.BigDecimal("100000"), new java.math.BigDecimal("500000"), null,
                java.time.LocalDate.of(2026, 12, 31), null, "TEST", java.util.Map.of("form", form));
    }

    @Test
    void rejectsInvalidAndOversizedDealWorkspacePayloads() throws Exception {
        assertThatThrownBy(() -> dealWorkspaces.create(objectMapper.readTree("[]")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("JSON object");
        assertThatThrownBy(() -> dealWorkspaces.create(objectMapper.readTree("{\"companyName\":\"Acme\"}")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("platform is required");
        String huge = "x".repeat(1_048_576);
        var oversized = objectMapper.createObjectNode().put("companyName", "Acme").put("platform", "Republic")
                .put("rawDealText", huge);
        assertThatThrownBy(() -> dealWorkspaces.create(oversized))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("1 MB");
    }

    @Test
    void deduplicatesCompaniesByDomainAndKeepsDistinctDomainsApart() {
        Company first = discoveryService.ingestManual(manual("Northwind Systems, Inc.", "https://www.northwind.test/a"));
        Company duplicate = discoveryService.ingestManual(manual("Northwind Systems LLC", "https://northwind.test"));
        Company other = discoveryService.ingestManual(manual("Northwind Systems", "https://northwind-two.test"));

        assertThat(duplicate.id()).isEqualTo(first.id());
        assertThat(other.id()).isNotEqualTo(first.id());

        Integer domainRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM radar_companies WHERE domain = 'northwind.test'", Integer.class);
        assertThat(domainRows).isEqualTo(1);
    }

    @Test
    void ingestsRssCandidatesWithoutAdoptingThePublisherDomain() throws SourceFetchException {
        String xml = """
                <rss version="2.0"><channel>
                  <item><title>Helio Grid raises $18M Series A</title>
                    <link>https://techcrunch.com/2026/08/18/helio-grid/</link><guid>pg-rss-1</guid>
                    <description>Grid software.</description></item>
                  <item><title>Why grid software is hard</title>
                    <link>https://techcrunch.com/2026/08/18/opinion/</link><guid>pg-rss-2</guid>
                    <description>Opinion.</description></item>
                </channel></rss>
                """;
        Source source = store.upsertSource("pg-rss-feed", "RSS", "Postgres RSS feed",
                "https://feed.test/rss", true);

        List<Candidate> candidates = new RssStartupSourceAdapter().parse(source, xml, 10);
        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).companyName()).isEqualTo("Helio Grid");

        Candidate candidate = candidates.get(0);
        long companyId = store.upsertCompany(candidate).company().id();
        store.saveDiscoveryAndSnapshot(companyId, source, candidate);

        Company stored = store.findCompany(companyId).orElseThrow();
        assertThat(stored.name()).isEqualTo("Helio Grid");
        assertThat(stored.domain()).isNull();

        // Re-ingesting the same article is idempotent.
        assertThat(store.saveDiscoveryAndSnapshot(companyId, source, candidate).discoveryCreated()).isFalse();
        Integer discoveries = jdbc.queryForObject(
                "SELECT COUNT(*) FROM radar_discoveries WHERE external_id = 'pg-rss-1'", Integer.class);
        assertThat(discoveries).isEqualTo(1);
    }

    @Test
    void enforcesAnalysisCacheUniquenessUnderRepeatedWrites() {
        Company company = discoveryService.ingestManual(manual("Cache Probe", "https://cache-probe.test"));
        var payload = scoringService.score(company);

        var first = store.saveAnalysis(company.id(), "RADAR", "hash-stable", "prompt-v1", "schema-v1",
                "DETERMINISTIC", "deterministic", "deterministic-radar-v1", payload);
        var second = store.saveAnalysis(company.id(), "RADAR", "hash-stable", "prompt-v1", "schema-v1",
                "DETERMINISTIC", "deterministic", "deterministic-radar-v1", payload);

        assertThat(second.id()).isEqualTo(first.id());
        Integer rows = jdbc.queryForObject("""
                SELECT COUNT(*) FROM radar_company_analyses
                 WHERE company_id = ? AND analysis_type = 'RADAR' AND input_hash = 'hash-stable'
                """, Integer.class, company.id());
        assertThat(rows).isEqualTo(1);

        assertThat(store.findCachedAnalysis(company.id(), "RADAR", "hash-stable", "prompt-v1", "schema-v1",
                "deterministic", "deterministic-radar-v1")).isPresent();
    }

    @Test
    void grantsOneJobLeaseAtATimeAndReleasesItOnCompletion() {
        JobStart first = store.beginJob("pg-lease-job", "key-1", Duration.ofMinutes(30));
        assertThat(first.acquired()).isTrue();

        JobStart concurrent = store.beginJob("pg-lease-job", "key-2", Duration.ofMinutes(30));
        assertThat(concurrent.acquired()).isFalse();

        store.completeJob("pg-lease-job", "key-1", first.leaseToken(), "SUCCESS", java.util.Map.of("ok", true), null);

        JobStart afterRelease = store.beginJob("pg-lease-job", "key-3", Duration.ofMinutes(30));
        assertThat(afterRelease.acquired()).isTrue();
        store.completeJob("pg-lease-job", "key-3", afterRelease.leaseToken(), "SUCCESS", java.util.Map.of(), null);

        // Re-running a completed idempotency key is treated as a duplicate rather than a fresh run.
        JobStart replay = store.beginJob("pg-lease-job", "key-1", Duration.ofMinutes(30));
        assertThat(replay.acquired()).isFalse();
        assertThat(replay.duplicate()).isTrue();
    }

    @Test
    void storesAdminSessionsAsHashesAndHonoursRevocationAndExpiry() {
        var issued = sessions.issue(Duration.ofHours(1));

        assertThat(sessions.validate(issued.token())).isPresent();
        Integer plaintextRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM radar_admin_sessions WHERE token_hash = ?", Integer.class, issued.token());
        assertThat(plaintextRows).isZero();

        sessions.revoke(issued.token());
        assertThat(sessions.validate(issued.token())).isEmpty();

        var expired = sessions.issue(Duration.ofSeconds(-1));
        assertThat(sessions.validate(expired.token())).isEmpty();
    }

    @Test
    void throttleSurvivesRestartAndIsolatesClients() {
        RadarLoginThrottle throttle = newThrottle();
        String attacker = "ip:203.0.113.44";
        String owner = "ip:198.51.100.7";

        for (int attempt = 0; attempt < 5; attempt++) {
            throttle.recordFailure(attacker);
        }

        assertThatBlocked(throttle, attacker);
        // A different client is untouched: one attacker cannot lock the real user out.
        throttle.requireAllowed(owner);

        // Simulating a deploy or Fly machine restart: a brand-new instance still sees the lockout,
        // which the previous in-memory implementation could not do.
        assertThatBlocked(newThrottle(), attacker);

        // A successful login clears the record.
        newThrottle().recordSuccess(attacker);
        newThrottle().requireAllowed(attacker);
        assertThat(loginAttempts.find(attacker)).isEmpty();
    }

    @Test
    void expiresThrottleWindowsAndCleansUpStaleRows() {
        String client = "ip:192.0.2.10";
        LocalDateTime now = LocalDateTime.now();

        // A window that has already elapsed resets the counter instead of accumulating.
        loginAttempts.recordFailure(client, now.minusHours(2), Duration.ofMinutes(15), 5, Duration.ofMinutes(15));
        var afterReset = loginAttempts.recordFailure(client, now, Duration.ofMinutes(15), 5, Duration.ofMinutes(15));
        assertThat(afterReset.failures()).isEqualTo(1);
        assertThat(afterReset.isBlockedAt(now)).isFalse();

        assertThat(loginAttempts.deleteStale(now.plusDays(30), Duration.ofHours(72))).isGreaterThanOrEqualTo(1);
        assertThat(loginAttempts.find(client)).isEmpty();
    }

    @Test
    void supportsWatchlistMutationsAndRedactsSourceCredentialsInTheExport() {
        Company company = discoveryService.ingestManual(manual("Watch Target", "https://watch-target.test"));

        store.watchCompany(company.id(), "Track interconnection launches", null);
        assertThat(store.findCompany(company.id()).orElseThrow().watched()).isTrue();
        assertThat(store.listWatchedCompanies()).extracting(Company::id).contains(company.id());

        store.unwatchCompany(company.id());
        assertThat(store.findCompany(company.id()).orElseThrow().watched()).isFalse();

        store.upsertSource("pg-secret-feed", "RSS", "Feed with credential",
                "https://feed.test/rss?apikey=SUPER-SECRET", true);

        var export = store.exportRadar();
        assertThat(export.publicSources()).isNotEmpty();
        assertThat(export.publicSources()).allSatisfy(source ->
                assertThat(source.url() == null ? "" : source.url()).doesNotContain("SUPER-SECRET"));
        assertThat(export.companies()).isNotEmpty();
        // The export must never carry raw discovery text or source configuration.
        assertThat(export.discoveries()).allSatisfy(discovery ->
                assertThat(discovery.rawTextHash()).isNotNull());
    }

    @Test
    void persistsNativePlatformOfferingIdempotentlyOnPostgres() {
        Company company = store.upsertCompany(new Candidate("postgres-native", "native-one",
                "Postgres Native Co", "https://postgres-native.example", "Public Reg CF offering.",
                "Infrastructure", List.of("Infrastructure", "Reg CF"), null, null,
                "https://republic.com/postgres-native", LocalDateTime.now(), "Reg CF active")).company();
        LocalDateTime now = LocalDateTime.now();
        NativeOfferingCandidate candidate = new NativeOfferingCandidate("REPUBLIC_DIRECTORY", "Republic",
                "postgres-native", "Postgres Native Co", "https://republic.com/postgres-native",
                company.websiteUrl(), company.domain(), Status.ACTIVE, "REG_CF", "Republic Funding Portal",
                "Crowd SAFE", new BigDecimal("250000"), new BigDecimal("100000"),
                new BigDecimal("1235000"), "$12M valuation cap", new BigDecimal("100"), null,
                LocalDate.now().plusDays(30), "Infrastructure", "Public Reg CF offering.", null,
                null, null, null, null, null, Map.of("listingText", "Reg CF active"), now);
        Match match = new Match(company.id(), MatchStatus.CONFIRMED, 100, "Exact domain.");

        assertThat(nativeOfferingStore.saveCandidate(candidate, ReconciliationStatus.PLATFORM_CONFIRMED,
                "CONFIRMED", 100, true)).isTrue();
        assertThat(nativeOfferingStore.saveCandidate(candidate, ReconciliationStatus.PLATFORM_CONFIRMED,
                "CONFIRMED", 100, true)).isFalse();
        long first = nativeOfferingStore.upsertPlatformOffering(candidate, match,
                ReconciliationStatus.PLATFORM_CONFIRMED).offeringId();
        long second = nativeOfferingStore.upsertPlatformOffering(candidate, match,
                ReconciliationStatus.PLATFORM_CONFIRMED).offeringId();

        assertThat(second).isEqualTo(first);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM radar_offerings WHERE native_candidate_key IS NOT NULL",
                Integer.class)).isEqualTo(1);
        assertThat(offeringStore.find(first)).get().satisfies(offering -> {
            assertThat(offering.provenance()).isEqualTo("PLATFORM_OFFERING");
            assertThat(offering.accessionNumber()).isNull();
        });
    }

    private RadarLoginThrottle newThrottle() {
        return new RadarLoginThrottle(loginAttempts, 5, Duration.ofMinutes(15), Duration.ofMinutes(15),
                Duration.ofHours(72), Clock.systemUTC());
    }

    private static void assertThatBlocked(RadarLoginThrottle throttle, String clientKey) {
        try {
            throttle.requireAllowed(clientKey);
            throw new AssertionError("expected client " + clientKey + " to be throttled");
        } catch (org.springframework.web.server.ResponseStatusException expected) {
            assertThat(expected.getStatusCode().value()).isEqualTo(429);
        }
    }

    private boolean tableExists(String table) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                 WHERE table_schema = 'public' AND table_name = ?
                """, Integer.class, table);
        return count != null && count > 0;
    }

    private static ManualDiscovery manual(String name, String website) {
        return new ManualDiscovery(name, website, "Deterministic integration fixture.", "Infrastructure",
                List.of("Infrastructure", "Automation"), "New York", 2025,
                website + "/source", null, null);
    }
}
