package com.startupvalidationbot.diligence;

import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import com.startupvalidationbot.diligence.CampaignVerification.*;
import com.startupvalidationbot.diligence.DiligenceDomain.*;
import com.startupvalidationbot.diligence.DiligenceStore.PacketDraft;
import com.startupvalidationbot.offering.OfferingDomain.*;
import com.startupvalidationbot.offering.OfferingStore;

@SpringBootTest
@Transactional
class CampaignVerificationIntegrationTest {
    @Autowired DiligenceStore store;
    @Autowired OfferingStore offerings;
    @Autowired JdbcTemplate jdbc;
    private static final String URL = "https://wefunder.com/acme";
    private long companyId;
    private Offering seed() {
        LocalDateTime now = LocalDateTime.now();
        jdbc.update("INSERT INTO radar_companies (name,normalized_name,domain,first_seen_at,last_seen_at,created_at,updated_at) VALUES ('Verification fixture','verification fixture','verification.example',?,?,?,?)", now, now, now, now);
        companyId = jdbc.queryForObject("SELECT id FROM radar_companies WHERE domain='verification.example'", Long.class);
        Map<String,String> facts = Map.of("_secCampaign.state", "CONFIRMED", "_secCampaign.url", URL,
                "_secCampaign.sourceUrl", "https://www.sec.gov/Archives/primary_doc.xml", "intermediaryCik", "0001670254");
        return offerings.upsert(new Candidate("Acme Energy", "0001234567", "https://acme.example", "Wefunder", "Wefunder Portal LLC", "0001670254",
                URL, "https://www.sec.gov/Archives/primary_doc.xml", "0001234567-26-000001", "020-v12d", "C", LocalDate.now(), "SAFE",
                new BigDecimal("100"), new BigDecimal("10000"), new BigDecimal("100000"), "$7M valuation cap", LocalDate.now().plusDays(30), null,
                "SEC_EDGAR", facts), new Match(companyId, MatchStatus.CONFIRMED, 100, "fixture")).offering();
    }
    private Packet packet(Offering offering) {
        return store.savePacket(new PacketDraft(companyId, offering.id(), PacketStatus.PARTIAL, "CONFIRMED", 80, 100, "Public fixture",
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of("SEC_EDGAR"), List.of(), "fixture", List.of(), List.of()));
    }
    private Observation observation(Offering offering, PageState state, LocalDateTime at) {
        return new Observation(offering.id(), URL, "PLATFORM_ISSUER_CLAIM", URL, at, state, state == PageState.VERIFIED ? "ACTIVE" : "UNKNOWN",
                state == PageState.VERIFIED ? "HTTP_200" : "HTTP_403", "Exact issuer and SEC intermediary", "Offering is open for investment.",
                "EXACT_ISSUER_AND_SEC", "Public fixture", 2);
    }
    @Test void observationReplayIsIdempotentAndReadModelKeepsEvidence() {
        var offering = seed();
        var success = observation(offering, PageState.VERIFIED, LocalDateTime.now().minusHours(1));
        store.recordCampaignObservation(success); store.recordCampaignObservation(success);
        var view = packet(offering).campaignVerification();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM radar_campaign_verification_attempts", Integer.class)).isEqualTo(1);
        assertThat(view.publicStatus()).isEqualTo("ACTIVE");
        assertThat(view.successfulObservation().statusExcerpt()).isEqualTo(success.statusExcerpt());
        assertThat(view.latestObservation().verificationMethod()).isEqualTo(success.verificationMethod());
    }
    @Test void failedRefreshRetainsHistoryAndEveryCanonicalField() {
        var offering = seed(); var before = offerings.find(offering.id()).orElseThrow();
        var success = observation(offering, PageState.VERIFIED, LocalDateTime.now().minusHours(1));
        store.recordCampaignObservation(success);
        store.recordCampaignObservation(observation(offering, PageState.ACCESS_BLOCKED, LocalDateTime.now()));
        var packet = packet(offering);
        assertThat(packet.campaignVerification().publicStatus()).isEqualTo("STALE_OR_UNAVAILABLE");
        assertThat(packet.campaignVerification().successfulObservation().statusExcerpt()).isEqualTo(success.statusExcerpt());
        assertThat(offerings.find(offering.id()).orElseThrow()).isEqualTo(before);
        assertThat(packet.minimumInvestment()).isEqualByComparingTo("100");
        assertThat(packet.valuationCap()).isEqualByComparingTo("7000000");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM radar_campaign_verification_attempts", Integer.class)).isEqualTo(2);
    }
    @Test void olderReplayCannotReplaceTheLatestAttempt() {
        var offering = seed(); var now = LocalDateTime.now();
        store.recordCampaignObservation(observation(offering, PageState.ACCESS_BLOCKED, now));
        store.recordCampaignObservation(observation(offering, PageState.VERIFIED, now.minusDays(4)));
        assertThat(store.latestCampaignObservation(offering.id())).get().extracting(Observation::pageState).isEqualTo(PageState.ACCESS_BLOCKED);
        assertThat(packet(offering).campaignVerification().fresh()).isFalse();
    }
    @Test void noLineageNoAttemptNoIndependentVerification() {
        var offering = seed();
        jdbc.update("UPDATE radar_offerings SET offering_url=NULL,raw_facts_json='{}' WHERE id=?", offering.id());
        var view = packet(offering).campaignVerification();
        assertThat(view.urlEvidence().url()).isNull(); assertThat(view.publicStatus()).isEqualTo("UNKNOWN");
        assertThat(view.latestAttempt()).isNull(); assertThat(view.lastSuccessfulVerification()).isNull();
    }
    @Test void verificationOfAnotherUrlCannotHideTheMatchingHistoricalEvidence() {
        var offering = seed();
        var original = observation(offering, PageState.VERIFIED, LocalDateTime.now().minusHours(2));
        store.recordCampaignObservation(original);
        store.recordCampaignObservation(new Observation(offering.id(), "https://wefunder.com/another", "PLATFORM_ISSUER_CLAIM",
                "https://wefunder.com/another", LocalDateTime.now(), PageState.VERIFIED, "CLOSED", "HTTP_200",
                "Different observed URL", "Offering is closed.", "EXACT_ISSUER_AND_SEC", "Retained alternate page", 1));
        var view = packet(offering).campaignVerification();
        assertThat(view.fresh()).isFalse();
        assertThat(view.successfulObservation().campaignUrl()).isEqualTo(URL);
        assertThat(view.lastSuccessfulVerification()).isEqualTo(original.observedAt());
        assertThat(view.publicStatus()).isEqualTo("STALE_OR_UNAVAILABLE");
    }
    @Test void verificationDoesNotCreateNotificationsDealsOrDecisions() {
        var offering = seed();
        store.recordCampaignObservation(observation(offering, PageState.VERIFIED, LocalDateTime.now()));
        packet(offering);
        for (String table : List.of("radar_notification_events", "deal_workspaces", "decisions"))
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class)).isZero();
    }
}
