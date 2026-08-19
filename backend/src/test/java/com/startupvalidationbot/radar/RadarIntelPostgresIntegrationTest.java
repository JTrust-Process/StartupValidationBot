package com.startupvalidationbot.radar;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.startupvalidationbot.radar.RadarIntelViews.InterestView;
import com.startupvalidationbot.radar.RadarRequests.ManualDiscovery;
import com.startupvalidationbot.radar.intel.ChangeSignificance.Tier;
import com.startupvalidationbot.radar.service.RadarDiscoveryService;
import com.startupvalidationbot.radar.service.RadarHomeService;
import com.startupvalidationbot.radar.service.RadarInterestService;
import com.startupvalidationbot.radar.service.RadarSimilarityService;

/**
 * PostgreSQL coverage for the Phase 2 intelligence layer.
 *
 * The scoring, significance and similarity rules themselves are unit-tested as pure functions; this
 * suite proves the persistence and wiring around them behave against the real database engine.
 *
 * Skipped automatically when no Docker daemon is present - skipped is not the same as passing.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "radar.run-token=intel-integration-token",
        "radar.ai.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class RadarIntelPostgresIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RadarStore store;

    @Autowired
    private RadarIntelStore intelStore;

    @Autowired
    private RadarDiscoveryService discoveryService;

    @Autowired
    private RadarInterestService interestService;

    @Autowired
    private RadarSimilarityService similarityService;

    @Autowired
    private RadarHomeService homeService;

    @Test
    void appliesTheIntelligenceMigration() {
        List<String> applied = jdbc.queryForList(
                "SELECT script FROM flyway_schema_history WHERE success ORDER BY installed_rank", String.class);
        assertThat(applied).anyMatch(script -> script.contains("V6__"));

        assertThat(tableExists("radar_interest_profile")).isTrue();
        assertThat(tableExists("radar_interaction_signals")).isTrue();
        assertThat(tableExists("radar_company_changes")).isTrue();
        assertThat(columnExists("radar_companies", "accelerator_batch")).isTrue();
        assertThat(columnExists("radar_trends", "velocity_note")).isTrue();
    }

    @Test
    void persistsAndReloadsTheInterestProfile() {
        var saved = interestService.save(List.of(
                new InterestView("Trading infrastructure", 20, List.of("trading", "exchange")),
                new InterestView("Robotics", 8, List.of("robotics"))));

        assertThat(saved.profile().interests()).extracting(InterestView::label)
                .containsExactly("Trading infrastructure", "Robotics");

        var reloaded = interestService.profile();
        assertThat(reloaded.interests()).hasSize(2);
        assertThat(reloaded.interests().get(0).weight()).isEqualTo(20);
        assertThat(reloaded.updatedAt()).isNotNull();

        // A single stored row is enforced by the CHECK constraint.
        Integer rows = jdbc.queryForObject("SELECT COUNT(*) FROM radar_interest_profile", Integer.class);
        assertThat(rows).isEqualTo(1);
    }

    @Test
    void recordsInteractionSignalsAndReflectsThemInRelevance() {
        Company company = discoveryService.ingestManual(manual("Signal Probe",
                "https://signal-probe.test", "Automated trading infrastructure for developers."));

        var before = interestService.explain(company.id());
        interestService.recordSignal(company.id(), "WATCH");
        interestService.recordSignal(company.id(), "DEEP_DIVE");
        var after = interestService.explain(company.id());

        assertThat(after.score()).isGreaterThan(before.score());
        assertThat(after.reasons()).anyMatch(reason -> reason.contains("watchlist"));

        // Ignoring holds the company down no matter how well it matches.
        store.ignoreCompany(company.id(), true);
        interestService.recordSignal(company.id(), "IGNORE");
        assertThat(interestService.explain(company.id()).score()).isLessThanOrEqualTo(12);

        Integer signalRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM radar_interaction_signals WHERE company_id = ?", Integer.class, company.id());
        assertThat(signalRows).isEqualTo(3);
    }

    @Test
    void persistsTieredChangesFromASecondSnapshot() {
        Source source = store.upsertSource("intel-test-feed", "RSS", "Intel test feed",
                "https://feed.test/intel", true);
        Candidate first = candidate(source, "Delta Metrics",
                "Delta Metrics is a trading terminal. 2,700 traders and $435M monthly volume.");
        long companyId = store.upsertCompany(first).company().id();
        var initial = store.saveDiscoveryAndSnapshot(companyId, source, first);
        intelStore.recordChanges(companyId, initial.snapshotId(), initial.changes());

        Candidate second = candidate(source, "Delta Metrics",
                "Delta Metrics is a trading terminal. 4,100 traders and $610M monthly volume. "
                        + "Series A led by Sequoia Capital.");
        var updated = store.saveDiscoveryAndSnapshot(companyId, source, second);
        intelStore.recordChanges(companyId, updated.snapshotId(), updated.changes());

        var changes = intelStore.changesForCompany(companyId, 20);
        assertThat(changes).extracting(view -> view.changeType())
                .contains("FUNDING_ROUND", "NEW_INVESTOR", "TRACTION_GROWTH");
        assertThat(changes).filteredOn(view -> view.changeType().equals("FUNDING_ROUND"))
                .allMatch(view -> "MAJOR".equals(view.significance()));

        assertThat(intelStore.recentChanges(Tier.IMPORTANT, 7, 50, false))
                .anyMatch(view -> view.companyId() == companyId);
        assertThat(intelStore.countMeaningfulChanges(7)).isGreaterThan(0);
        assertThat(intelStore.companiesWithRecentFunding(7, 10)).contains(companyId);
    }

    @Test
    void ignoresTrivialReworkingBetweenSnapshots() {
        Source source = store.upsertSource("intel-quiet-feed", "RSS", "Quiet feed",
                "https://feed.test/quiet", true);
        Candidate first = candidate(source, "Quiet Co", "We build fast developer tooling for teams.");
        long companyId = store.upsertCompany(first).company().id();
        var initial = store.saveDiscoveryAndSnapshot(companyId, source, first);
        intelStore.recordChanges(companyId, initial.snapshotId(), initial.changes());

        Candidate reworded = candidate(source, "Quiet Co",
                "We build fast developer tooling for engineering teams.");
        var second = store.saveDiscoveryAndSnapshot(companyId, source, reworded);
        intelStore.recordChanges(companyId, second.snapshotId(), second.changes());

        assertThat(intelStore.changesForCompany(companyId, 20)).isEmpty();
    }

    @Test
    void ranksSimilarCompaniesFromStoredFacets() {
        discoveryService.ingestManual(manual("Alpha Trading", "https://alpha-trading.test",
                "Trading infrastructure marketplace for global equities and derivatives."));
        Company beta = discoveryService.ingestManual(manual("Beta Trading", "https://beta-trading.test",
                "Trading infrastructure marketplace for equities and derivatives."));
        discoveryService.ingestManual(manual("Farm Sense", "https://farm-sense.test",
                "Soil monitoring hardware for regenerative agriculture."));

        var similar = similarityService.similarTo(beta.id(), 5);

        assertThat(similar).isNotEmpty();
        assertThat(similar).noneMatch(view -> view.companyId() == beta.id());
        assertThat(similar).allMatch(view -> !view.reasons().isEmpty());
        assertThat(similar.get(0).name()).isEqualTo("Alpha Trading");
    }

    @Test
    void buildsRadarHomeWithoutRepeatingACompanyAcrossSections() {
        for (int index = 0; index < 8; index++) {
            discoveryService.ingestManual(manual("Home Co " + index, "https://home-co-" + index + ".test",
                    "Developer automation platform number " + index + " for enterprise teams."));
        }

        var home = homeService.build();

        assertThat(home.sections()).extracting(section -> section.key())
                .containsExactly("watchlist-updates", "new-today", "recently-funded", "best-matches",
                        "high-momentum", "emerging-trends");
        assertThat(home.totalCompanies()).isGreaterThan(0);

        List<Long> shown = home.sections().stream()
                .flatMap(section -> section.companies().stream())
                .map(card -> card.id())
                .toList();
        // The whole point of the dedup pass: no company occupies two sections.
        assertThat(shown).doesNotHaveDuplicates();
        assertThat(home.sections().stream().flatMap(section -> section.companies().stream()))
                .allMatch(card -> !card.whyItMatters().isEmpty());
    }

    @Test
    void recomputesPersonalScoresDeterministicallyWithoutAi() {
        Company company = discoveryService.ingestManual(manual("Rescore Co", "https://rescore-co.test",
                "Robotics autonomy platform for warehouse operators."));

        interestService.save(List.of(new InterestView("Robotics", 25, List.of("robotics", "autonomy"))));
        int highScore = interestService.explain(company.id()).score();

        interestService.save(List.of(new InterestView("Agriculture", 25, List.of("farming", "soil"))));
        int lowScore = interestService.explain(company.id()).score();

        assertThat(highScore).isGreaterThan(lowScore);
        // Recompute is idempotent: running it twice changes nothing the second time.
        interestService.recomputePersonalScores();
        assertThat(interestService.recomputePersonalScores()).isZero();
    }

    private static ManualDiscovery manual(String name, String website, String description) {
        return new ManualDiscovery(name, website, description, "Infrastructure",
                List.of("Infrastructure", "Automation"), "New York", 2025, website + "/source", null, null);
    }

    private static Candidate candidate(Source source, String name, String description) {
        return new Candidate(source.sourceKey(), "ext-" + name.toLowerCase().replace(' ', '-'), name,
                null, description, "Fintech", List.of("Fintech", "Trading"), null, null,
                "https://feed.test/article", null, description);
    }

    private boolean tableExists(String table) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = ?",
                Integer.class, table);
        return count != null && count > 0;
    }

    private boolean columnExists(String table, String column) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = 'public' "
                        + "AND table_name = ? AND column_name = ?", Integer.class, table, column);
        return count != null && count > 0;
    }
}
