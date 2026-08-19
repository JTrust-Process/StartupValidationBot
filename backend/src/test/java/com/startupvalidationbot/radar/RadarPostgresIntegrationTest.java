package com.startupvalidationbot.radar;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
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

/**
 * Production-representative persistence coverage.
 *
 * The fast suite runs on H2, which cannot prove PostgreSQL behaviour for the things this system
 * actually depends on: Flyway V1-V6 applying in order, UNIQUE constraints on company identity and
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

        // V4 brought the legacy diligence tables under Flyway. The context booting with
        // ddl-auto=validate is itself the assertion that the JPA mapping matches them.
        assertThat(tableExists("deals")).isTrue();
        assertThat(tableExists("quick_screens")).isTrue();
        assertThat(tableExists("decisions")).isTrue();
        assertThat(tableExists("deep_diligence")).isTrue();
        assertThat(tableExists("reviews")).isTrue();
        assertThat(tableExists("radar_login_attempts")).isTrue();
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
