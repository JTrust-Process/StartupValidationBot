package com.startupvalidationbot.diligence;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.startupvalidationbot.diligence.CampaignVerification.*;
import com.startupvalidationbot.diligence.discovery.CampaignUrlLineage;

class CampaignVerificationTest {
    private static final String URL = "https://wefunder.com/acme";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 13, 17, 0);
    static Map<String, String> lineage() {
        return Map.of("_secCampaign.state", "CONFIRMED", "_secCampaign.url", URL,
                "_secCampaign.sourceUrl", "https://www.sec.gov/Archives/primary_doc.xml");
    }
    static Observation observation(PageState state, LocalDateTime at, String status) {
        return new Observation(1, URL, "PLATFORM_ISSUER_CLAIM", URL, at, state, status,
                state == PageState.VERIFIED ? "HTTP_200" : "HTTP_403", "Acme matches SEC issuer and intermediary",
                "Offering is open for investment.", "EXACT_ISSUER_AND_SEC", "Recorded public source statement", 1);
    }
    @Test void exactUrlDoesNotIndependentlyVerifyAPageOrOpening() {
        var view = CampaignVerification.project(1, URL, "SEC_EDGAR", lineage(), null, "ACTIVE", null, null, null, NOW);
        assertThat(view.urlEvidence().state()).isEqualTo("EXPLICIT_OBSERVED");
        assertThat(view.pageState()).isEqualTo(PageState.NOT_CHECKED);
        assertThat(view.publicStatus()).isEqualTo("UNKNOWN");
        assertThat(view.fresh()).isFalse();
    }
    @Test void freshIssuerMatchedEvidenceEstablishesOnlyPublicStatus() {
        var evidence = observation(PageState.VERIFIED, NOW.minusHours(1), "ACTIVE");
        var view = CampaignVerification.project(1, URL, "SEC_EDGAR", lineage(), null, "ACTIVE", null, evidence, evidence, NOW);
        assertThat(view.fresh()).isTrue(); assertThat(view.publicStatus()).isEqualTo("ACTIVE");
        assertThat(view.successfulObservation().statusExcerpt()).isEqualTo(evidence.statusExcerpt());
        assertThat(view.limitations()).anyMatch(v -> v.contains("personal eligibility"));
    }
    @Test void blockedRefreshRetainsSuccessfulEvidenceButNotCurrentOpening() {
        var success = observation(PageState.VERIFIED, NOW.minusHours(2), "ACTIVE");
        var failure = observation(PageState.ACCESS_BLOCKED, NOW, "UNKNOWN");
        var view = CampaignVerification.project(1, URL, "SEC_EDGAR", lineage(), null, "ACTIVE", null, failure, success, NOW);
        assertThat(view.pageState()).isEqualTo(PageState.ACCESS_BLOCKED);
        assertThat(view.publicStatus()).isEqualTo("STALE_OR_UNAVAILABLE");
        assertThat(view.lastSuccessfulVerification()).isEqualTo(success.observedAt());
        assertThat(view.historicalStatus()).isEqualTo("ACTIVE");
        assertThat(view.fresh()).isFalse();
    }
    @Test void positiveStatusExpiresAtSeventyTwoHours() {
        var old = observation(PageState.VERIFIED, NOW.minusHours(72), "ACTIVE");
        var view = CampaignVerification.project(1, URL, "SEC_EDGAR", lineage(), null, "ACTIVE", null, old, old, NOW);
        assertThat(view.pageState()).isEqualTo(PageState.HISTORICAL_ONLY);
        assertThat(view.publicStatus()).isEqualTo("STALE_OR_UNAVAILABLE");
    }
    @Test void futureObservationCannotBeCurrent() {
        var future = observation(PageState.VERIFIED, NOW.plusHours(1), "ACTIVE");
        assertThat(CampaignVerification.project(1, URL, "SEC_EDGAR", lineage(), null, "ACTIVE", null, future, future, NOW).fresh()).isFalse();
    }
    @Test void expiredSecDeadlineSurfacesConflictWithoutOverwritingEitherSource() {
        var page = observation(PageState.VERIFIED, NOW, "ACTIVE");
        var view = CampaignVerification.project(1, URL, "SEC_EDGAR", lineage(), null, "ACTIVE", NOW.minusDays(1).toLocalDate(), page, page, NOW);
        assertThat(view.publicStatus()).isEqualTo("CONFLICTING");
        assertThat(view.discrepancies()).hasSize(1);
        assertThat(view.latestObservation().publicStatus()).isEqualTo("ACTIVE");
    }
    @Test void midnightUtcDoesNotExpireTheCurrentNewYorkDeadlineEarly() {
        LocalDateTime utc = LocalDateTime.of(2026, 10, 14, 1, 0);
        var page = observation(PageState.VERIFIED, utc, "ACTIVE");
        assertThat(CampaignVerification.project(1, URL, "SEC_EDGAR", lineage(), null, "ACTIVE", NOW.toLocalDate(), page, page, utc).publicStatus()).isEqualTo("ACTIVE");
    }
    @Test void withdrawnFilingAndFundedPageRemainDisclosedAsConflicting() {
        var page = observation(PageState.VERIFIED, NOW, "FUNDED");
        var view = CampaignVerification.project(1, URL, "SEC_EDGAR", lineage(), null, "WITHDRAWN", null, page, page, NOW);
        assertThat(view.publicStatus()).isEqualTo("CONFLICTING");
        assertThat(view.latestObservation().publicStatus()).isEqualTo("FUNDED");
        assertThat(view.discrepancies()).hasSize(1);
    }
    @Test void conflictingExactUrlsRemainAmbiguous() {
        var facts = new java.util.HashMap<>(lineage());
        facts.putAll(Map.of("_issuerCampaign.state", "CONFIRMED", "_issuerCampaign.url", "https://wefunder.com/another",
                "_issuerCampaign.source", "ISSUER_WEBSITE_CLAIM", "_issuerCampaign.sourceUrl", "https://acme.example"));
        assertThat(CampaignUrlLineage.evidence(URL, facts, null, "SEC_EDGAR").state()).isEqualTo("CONFLICTING");
    }
    @Test void manualExactUrlHasProvenanceButRequiresIndependentPageVerification() {
        var evidence = CampaignUrlLineage.evidence(URL, Map.of("offeringUrl", URL), null, "MANUAL");
        assertThat(evidence.classification()).isEqualTo("USER_SUPPLIED_URL");
        assertThat(evidence.state()).isEqualTo("EXPLICIT_OBSERVED");
    }
}
