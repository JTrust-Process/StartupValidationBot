package com.startupvalidationbot.offering.intake;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.startupvalidationbot.diligence.DiligenceStore;
import com.startupvalidationbot.offering.OfferingDomain.Match;
import com.startupvalidationbot.offering.OfferingDomain.MatchStatus;
import com.startupvalidationbot.offering.OfferingDomain.Candidate;
import com.startupvalidationbot.offering.OfferingStore;
import com.startupvalidationbot.offering.intake.NativeOfferingDomain.NativeOfferingCandidate;
import com.startupvalidationbot.offering.intake.NativeOfferingDomain.ReconciliationStatus;
import com.startupvalidationbot.offering.intake.NativeOfferingDomain.Status;

@SpringBootTest
@Transactional
class NativeOfferingStoreIntegrationTest {
    @Autowired NativeOfferingStore nativeStore;
    @Autowired OfferingStore offerings;
    @Autowired DiligenceStore diligence;
    @Autowired JdbcTemplate jdbc;

    @Test
    void v14PersistsPlatformProvenanceAndPreventsDuplicateOfferingsAndDeals() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"version\"='14'",
                Integer.class)).isEqualTo(1);
        long companyId = company("Native Intake Co", "native-intake.example");
        Match match = new Match(companyId, MatchStatus.CONFIRMED, 100, "Exact verified domain.");
        NativeOfferingCandidate candidate = candidate("native-intake", Status.ACTIVE);

        assertThat(nativeStore.saveCandidate(candidate, ReconciliationStatus.PLATFORM_CONFIRMED,
                "CONFIRMED", 100, true)).isTrue();
        assertThat(nativeStore.saveCandidate(candidate, ReconciliationStatus.PLATFORM_CONFIRMED,
                "CONFIRMED", 100, true)).isFalse();
        var first = nativeStore.upsertPlatformOffering(candidate, match, ReconciliationStatus.PLATFORM_CONFIRMED);
        var repeated = nativeStore.upsertPlatformOffering(candidate, match, ReconciliationStatus.PLATFORM_CONFIRMED);

        assertThat(first.created()).isTrue();
        assertThat(repeated.created()).isFalse();
        assertThat(offerings.find(first.offeringId())).get().satisfies(offering -> {
            assertThat(offering.provenance()).isEqualTo("PLATFORM_OFFERING");
            assertThat(offering.reconciliationStatus()).isEqualTo("PLATFORM_CONFIRMED");
            assertThat(offering.secFilingUrl()).isNull();
            assertThat(offering.offeringUrl()).isEqualTo("https://republic.com/native-intake");
        });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM radar_native_offering_candidates", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM radar_offerings", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deals", Integer.class)).isZero();
    }

    @Test
    void closedOfferingCannotEnterActionableDiligenceQueue() {
        long companyId = company("Closed Native Co", "closed-native.example");
        Match match = new Match(companyId, MatchStatus.CONFIRMED, 100, "Exact verified domain.");
        long closedId = nativeStore.upsertPlatformOffering(candidate("closed-native", Status.CLOSED), match,
                ReconciliationStatus.PLATFORM_CONFIRMED).offeringId();
        long activeId = nativeStore.upsertPlatformOffering(candidate("active-native", Status.ACTIVE), match,
                ReconciliationStatus.PLATFORM_CONFIRMED).offeringId();

        assertThat(diligence.eligibleOfferingIds(25)).contains(activeId).doesNotContain(closedId);
    }

    @Test
    void laterSecEvidenceUpgradesCanonicalPlatformOfferingWithoutCreatingADuplicate() {
        long companyId = company("Native Intake Co", "native-intake.example");
        Match match = new Match(companyId, MatchStatus.CONFIRMED, 100, "Exact verified domain.");
        NativeOfferingCandidate platform = candidate("native-intake", Status.ACTIVE);
        long platformId = nativeStore.upsertPlatformOffering(platform, match,
                ReconciliationStatus.PLATFORM_CONFIRMED).offeringId();
        Candidate sec = new Candidate(platform.companyName(), "0001234567", platform.issuerWebsite(),
                platform.platform(), platform.intermediaryName(), null, platform.canonicalUrl(),
                "https://www.sec.gov/Archives/edgar/data/1234567/filing-index.html",
                "0001234567-26-000001", "020-12345", "C", LocalDate.now().minusDays(2),
                platform.securityType(), platform.minimumInvestment(), platform.targetAmount(),
                platform.maximumAmount(), platform.valuationOrCap(), platform.deadline(),
                platform.amountRaised(), "SEC_EDGAR_RECENT", Map.of("issuerWebsite", platform.issuerWebsite()));

        var reconciled = offerings.upsert(sec, match);

        assertThat(reconciled.created()).isFalse();
        assertThat(reconciled.offering().id()).isEqualTo(platformId);
        assertThat(reconciled.offering().provenance()).isEqualTo("SEC_EDGAR");
        assertThat(reconciled.offering().reconciliationStatus()).isEqualTo("SEC_RECONCILED");
        assertThat(reconciled.offering().accessionNumber()).isEqualTo("0001234567-26-000001");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM radar_offerings", Integer.class)).isEqualTo(1);
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

    private static NativeOfferingCandidate candidate(String slug, Status status) {
        LocalDateTime now = LocalDateTime.now();
        return new NativeOfferingCandidate("REPUBLIC_DIRECTORY", "Republic", slug,
                slug.contains("closed") ? "Closed Native Co" : "Native Intake Co",
                "https://republic.com/" + slug, "https://native-intake.example", "native-intake.example",
                status, "REG_CF", "Republic Funding Portal", "Crowd SAFE", new BigDecimal("250000"),
                new BigDecimal("100000"), new BigDecimal("1235000"), "$12M valuation cap",
                new BigDecimal("100"), null, LocalDate.now().plusDays(30), "Technology",
                "Public Reg CF campaign.", null, null, null, null, null, null,
                Map.of("listingText", "Reg CF active campaign"), now);
    }
}
