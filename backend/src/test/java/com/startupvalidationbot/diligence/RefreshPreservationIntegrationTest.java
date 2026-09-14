package com.startupvalidationbot.diligence;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static com.startupvalidationbot.offering.OfferingDomain.*;
import static com.startupvalidationbot.diligence.DiligenceDomain.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.startupvalidationbot.offering.*;
import com.startupvalidationbot.offering.intake.NativeOfferingDomain.NativeOfferingCandidate;
import com.startupvalidationbot.offering.intake.NativeOfferingDomain.ReconciliationStatus;
import com.startupvalidationbot.offering.intake.NativeOfferingStore;
import com.startupvalidationbot.diligence.DiligenceStore.EvidenceDraft;
import com.startupvalidationbot.diligence.DiligenceStore.PacketDraft;
import com.startupvalidationbot.diligence.notification.DiligenceNotificationService;
import com.startupvalidationbot.diligence.notification.DiligenceEmailSender;
import com.startupvalidationbot.diligence.platform.PlatformHttpClient;
import com.startupvalidationbot.diligence.platform.RepublicOfferingEnricher;
import com.startupvalidationbot.radar.RadarStore;
import com.startupvalidationbot.radar.service.RadarQueryService;

@SpringBootTest
@Transactional
class RefreshPreservationIntegrationTest {
    private static final String ACCESSION = "0001234567-26-000081";
    @Autowired OfferingStore offerings;
    @Autowired DiligenceStore diligence;
    @Autowired NativeOfferingStore nativeStore;
    @Autowired OfferingProjectionRepair repair;
    @Autowired JdbcTemplate jdbc;
    @Autowired RadarStore radar;
    @Autowired RadarQueryService queries;
    @Autowired SecFinancialExtractor financials;
    @Autowired EvidenceReconciler reconciler;
    long companyId;

    @BeforeEach
    void company() {
        var now = LocalDateTime.now();
        jdbc.update("""
                INSERT INTO radar_companies(name,normalized_name,domain,website_url,description,sector,categories_json,
                  aliases_json,radar_score,personal_score,score_reasoning,source_count,first_seen_at,last_seen_at,ignored,
                  accelerator,accelerator_batch,created_at,updated_at)
                VALUES ('Refresh Fixture','refresh fixture','refresh.example','https://refresh.example','','Energy',
                  '[]','[]',0,0,'',0,?,?,false,'','',?,?)
                """, now, now, now, now);
        companyId = jdbc.queryForObject("SELECT id FROM radar_companies WHERE domain='refresh.example'", Long.class);
    }

    @ParameterizedTest
    @EnumSource(value=RetrievalQuality.class, names={"INDEX_ONLY","PARTIAL_DETAIL"})
    void sameAccessionIncompleteRefreshPreservesEstablishedFactsAndIdentity(RetrievalQuality quality) {
        Offering first = initial("Preferred Stock", "100000", "1050000", "2027-08-17");
        var timestamp = offerings.facts(first.id()).get("_observedAt.targetAmount");
        Offering next = offerings.upsert(candidate(ACCESSION, "C", quality, null, null, null, null, null), weak()).offering();
        assertThat(next.securityType()).isEqualTo("Preferred Stock");
        assertThat(next.targetAmount()).isEqualByComparingTo("100000");
        assertThat(next.maximumAmount()).isEqualByComparingTo("1050000");
        assertThat(next.minimumInvestment()).isEqualByComparingTo("100");
        assertThat(next.deadline()).isEqualTo("2027-08-17");
        assertThat(next.matchStatus()).isEqualTo(MatchStatus.CONFIRMED);
        assertThat(next.matchConfidence()).isEqualTo(100);
        assertThat(next.platform()).isEqualTo("Republic");
        assertThat(next.intermediaryName()).isEqualTo("OpenDeal Portal LLC");
        assertThat(next.fileNumber()).isEqualTo("020-refresh");
        assertThat(offerings.storedCandidate(first.id()).intermediaryCik()).isEqualTo("0000123456");
        assertThat(offerings.storedCandidate(first.id()).issuerWebsite()).isEqualTo("https://refresh.example");
        assertThat(offerings.facts(first.id()).get("_observedAt.targetAmount")).isEqualTo(timestamp);
        assertThat(offerings.facts(first.id()).get("_retrievalQuality")).isEqualTo(quality.name());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM radar_offerings", Integer.class)).isEqualTo(1);
    }

    @Test
    void aceStyleOtherSecurityAndAmountsArePreserved() {
        Offering first = initial("Other", "25562.50", "4989800", "2027-08-11");
        Offering next = offerings.upsert(candidate(ACCESSION, "C", RetrievalQuality.INDEX_ONLY, null, null, null, null, null), weak()).offering();
        assertThat(next.securityType()).isEqualTo("Other");
        assertThat(next.targetAmount()).isEqualByComparingTo("25562.50");
        assertThat(next.maximumAmount()).isEqualByComparingTo("4989800");
        assertThat(next.deadline()).isEqualTo("2027-08-11");
        assertThat(next.id()).isEqualTo(first.id());
    }

    @Test
    void explicitSameAccessionValueMayUpdateWithoutErasingMissingFields() {
        initial("Preferred Stock", "100000", "1050000", "2027-08-17");
        Offering next = offerings.upsert(candidate(ACCESSION, "C", RetrievalQuality.PARTIAL_DETAIL, null, "150000", null, null, null), weak()).offering();
        assertThat(next.targetAmount()).isEqualByComparingTo("150000");
        assertThat(next.maximumAmount()).isEqualByComparingTo("1050000");
        assertThat(next.matchStatus()).isEqualTo(MatchStatus.CONFIRMED);
    }

    @Test
    void newerAmendmentUpdatesAndSubsequentIncompleteAmendmentPreservesLatestValues() {
        initial("Preferred Stock", "100000", "1050000", "2027-08-17");
        String amendment = "0001234567-26-000082";
        Candidate amended = candidate(amendment, "C/A", RetrievalQuality.PARTIAL_DETAIL, null, "150000", null, null, null);
        Offering next = offerings.upsert(amended, weak()).offering();
        assertThat(next.targetAmount()).isEqualByComparingTo("150000");
        assertThat(next.accessionNumber()).isEqualTo(amendment);
        assertThat(offerings.facts(next.id()).get("_accession.maximumAmount")).isEqualTo(ACCESSION);
        next = offerings.upsert(candidate(amendment, "C/A", RetrievalQuality.INDEX_ONLY, null, null, null, null, null), weak()).offering();
        assertThat(next.targetAmount()).isEqualByComparingTo("150000");
        offerings.upsert(candidate(ACCESSION, "C", RetrievalQuality.INDEX_ONLY, null, null, null, null, null), weak());
        assertThat(offerings.list(null, null, null, companyId)).singleElement().satisfies(value -> {
            assertThat(value.accessionNumber()).isEqualTo(amendment);
            assertThat(value.targetAmount()).isEqualByComparingTo("150000");
        });
    }

    @Test
    void amendmentDoesNotReattributePriorRawFinancialAliasesToTheNewFiling() {
        Offering original = initial("Preferred Stock", "100000", "1050000", "2027-08-17");
        jdbc.update("UPDATE radar_offerings SET raw_facts_json=? WHERE id=?",
                "{\"REVENUEMOSTRECENTFISCALYEAR\":\"1000\"}", original.id());
        Offering updated = offerings.upsert(candidate("0001234567-26-000082", "C/A", RetrievalQuality.PARTIAL_DETAIL,
                null, "150000", null, null, null), weak()).offering();
        assertThat(offerings.facts(updated.id())).doesNotContainKey("REVENUEMOSTRECENTFISCALYEAR");
        assertThat(offerings.facts(updated.id()).get("_accession.maximumAmount")).isEqualTo(ACCESSION);
        assertThat(updated.maximumAmount()).isEqualByComparingTo("1050000");
    }

    @ParameterizedTest
    @ValueSource(strings={"C-W","C-TR"})
    void explicitLifecycleUpdatesWithoutErasingTerms(String form) {
        Offering first = initial("Preferred Stock", "100000", "1050000", "2027-08-17");
        Offering next = offerings.upsert(candidate("0001234567-26-000082", form, RetrievalQuality.PARTIAL_DETAIL,
                null, null, null, null, null), weak()).offering();
        assertThat(next.status()).isEqualTo(form.equals("C-W") ? Status.WITHDRAWN : Status.TERMINATED);
        assertThat(next.targetAmount()).isEqualByComparingTo(first.targetAmount());
        assertThat(diligence.eligibleOfferingIds(25)).doesNotContain(next.id());
    }

    @Test
    void explicitPastDeadlineEndsOffering() {
        initial("Preferred Stock", "100000", "1050000", "2027-08-17");
        Offering ended = offerings.upsert(candidate("0001234567-26-000082", "C/A", RetrievalQuality.PARTIAL_DETAIL,
                null, null, null, LocalDate.now().minusDays(1).toString(), null), weak()).offering();
        assertThat(ended.status()).isEqualTo(Status.ENDED);
        assertThat(diligence.eligibleOfferingIds(25)).doesNotContain(ended.id());
    }

    @Test
    void matchRevalidationPreservesAbsenceButHonorsConcreteConflict() {
        Offering first = initial("Preferred Stock", "100000", "1050000", "2027-08-17");
        offerings.updateMatch(first.id(), weak());
        assertThat(offerings.find(first.id()).orElseThrow().matchStatus()).isEqualTo(MatchStatus.CONFIRMED);
        offerings.updateMatch(first.id(), new Match(companyId, MatchStatus.REJECTED, 15, "Verified domain conflict", true));
        assertThat(offerings.find(first.id()).orElseThrow().matchStatus()).isEqualTo(MatchStatus.REJECTED);
    }

    @Test
    void newObservedConflictingDomainDoesNotRemainConfirmed() {
        initial("Preferred Stock", "100000", "1050000", "2027-08-17");
        Offering next = offerings.upsert(candidate(ACCESSION, "C", RetrievalQuality.PARTIAL_DETAIL,
                null, null, null, null, "https://conflicting.example"), confirmed()).offering();
        assertThat(next.matchStatus()).isEqualTo(MatchStatus.REJECTED);
    }

    @Test
    void malformedIncomingValuesDoNotDestroyCanonicalValues() {
        initial("Preferred Stock", "100000", "1050000", "2027-08-17");
        Offering next = offerings.upsert(candidate(ACCESSION, "C", RetrievalQuality.PARTIAL_DETAIL,
                "senior to SAFE and a claim about future returns", "-1", "2", null, null), weak()).offering();
        assertThat(next.securityType()).isEqualTo("Preferred Stock");
        assertThat(next.targetAmount()).isEqualByComparingTo("100000");
        assertThat(next.maximumAmount()).isEqualByComparingTo("1050000");
        assertThat(offerings.facts(next.id())).containsKey("_rejected.securityType");
    }

    @Test
    void platformPartialAndUnavailableRefreshPreserveTermsAndObservationTime() {
        Offering offering = initial("Other", "100000", "1050000", "2027-08-17");
        PlatformCampaign first = diligence.upsertCampaign(campaign(offering.id(), true, CampaignStatus.ACTIVE));
        PlatformCampaign next = diligence.upsertCampaign(campaign(offering.id(), false, CampaignStatus.UNKNOWN));
        assertThat(next.securityType()).isEqualTo("SAFE");
        assertThat(next.minimumInvestment()).isEqualByComparingTo("100");
        assertThat(next.valuationCap()).isEqualByComparingTo("7000000");
        assertThat(next.deadline()).isEqualTo(first.deadline());
        assertThat(next.lastVerifiedAt()).isEqualTo(first.lastVerifiedAt());
        assertThat(next.facts().get("_observedAt.minimumInvestment")).isEqualTo(first.facts().get("_observedAt.minimumInvestment"));
        diligence.markPlatform("REPUBLIC", "DEGRADED", 1, 0, "HTTP 403");
        diligence.upsertCampaign(campaign(offering.id(), false, CampaignStatus.UNAVAILABLE));
        assertThat(diligence.findCampaign(offering.id()).orElseThrow().minimumInvestment()).isEqualByComparingTo("100");
        assertThat(jdbc.queryForObject("SELECT last_status FROM radar_platform_source_state WHERE platform='REPUBLIC'", String.class)).isEqualTo("DEGRADED");
    }

    @Test
    void republicHttp403InRealPacketPipelineRetainsCampaignWithoutFreshVerification() {
        Offering offering = initial("SAFE", "100000", "1050000", "2027-08-17");
        PlatformCampaign first = diligence.upsertCampaign(campaign(offering.id(), true, CampaignStatus.ACTIVE));
        PlatformHttpClient http = mock(PlatformHttpClient.class);
        when(http.get(any(), any())).thenThrow(new IllegalStateException("HTTP 403"));
        DiligenceNotificationService notifications = mock(DiligenceNotificationService.class);
        when(notifications.sendPending()).thenReturn(new DiligenceNotificationService.SendCounts(0, 0));

        var result = new AutonomousDiligenceService(diligence, offerings, radar, queries, financials, reconciler,
                notifications, List.of(new RepublicOfferingEnricher(http)), 25).run();

        assertThat(result.platformErrors()).isEqualTo(1);
        assertThat(result.packetsPartial()).isEqualTo(1);
        assertThat(diligence.findCampaign(offering.id()).orElseThrow()).isEqualTo(first);
        assertThat(jdbc.queryForObject("SELECT last_status FROM radar_platform_source_state WHERE platform='REPUBLIC'", String.class)).isEqualTo("DEGRADED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deal_workspaces", Integer.class)).isZero();
        verify(http).get(any(), any());
    }

    @Test
    void nativePlatformCanonicalRefreshPreservesFactsMatchAndReconciliationButAllowsClosure() {
        long id = nativeStore.upsertPlatformOffering(nativeCandidate(true, com.startupvalidationbot.offering.intake.NativeOfferingDomain.Status.ACTIVE),
                confirmed(), ReconciliationStatus.PLATFORM_CONFIRMED).offeringId();
        String observed = offerings.facts(id).get("_observedAt.minimumInvestment");
        nativeStore.upsertPlatformOffering(nativeCandidate(false, com.startupvalidationbot.offering.intake.NativeOfferingDomain.Status.UNKNOWN),
                weak(), ReconciliationStatus.POSSIBLE);
        Offering refreshed = offerings.find(id).orElseThrow();
        assertThat(refreshed.securityType()).isEqualTo("SAFE");
        assertThat(refreshed.minimumInvestment()).isEqualByComparingTo("100");
        assertThat(refreshed.valuationOrCap()).isEqualTo("$7M cap");
        assertThat(refreshed.matchStatus()).isEqualTo(MatchStatus.CONFIRMED);
        assertThat(refreshed.reconciliationStatus()).isEqualTo("PLATFORM_CONFIRMED");
        assertThat(refreshed.offeringExemption()).isEqualTo("REG_CF");
        assertThat(refreshed.status()).isEqualTo(Status.ACTIVE);
        assertThat(offerings.facts(id).get("_observedAt.minimumInvestment")).isEqualTo(observed);
        nativeStore.upsertPlatformOffering(nativeCandidate(false, com.startupvalidationbot.offering.intake.NativeOfferingDomain.Status.CLOSED),
                weak(), ReconciliationStatus.POSSIBLE);
        assertThat(offerings.find(id).orElseThrow().status()).isEqualTo(Status.ENDED);
        assertThat(diligence.eligibleOfferingIds(25)).doesNotContain(id);
    }

    @Test
    void packetTermsAndUnsavedHandoffProjectionStayStableWithoutDuplicateNotificationOrDeal() {
        Offering offering = initial("Preferred Stock", "100000", "1050000", "2027-08-17");
        var first = diligence.savePacket(packet(offering, List.of()));
        DiligenceEmailSender sender = org.mockito.Mockito.mock(DiligenceEmailSender.class);
        var notifications = new DiligenceNotificationService(diligence, sender, "fixture@example.com", "https://example.com/#/radar");
        assertThat(notifications.queueNewConfirmed(offering, first)).isTrue();
        Offering refreshed = offerings.upsert(candidate(ACCESSION, "C", RetrievalQuality.INDEX_ONLY, null, null, null, null, null), weak()).offering();
        var next = diligence.find(first.id()).orElseThrow();
        assertThat(next.securityType()).isEqualTo(first.securityType());
        assertThat(next.targetAmount()).isEqualByComparingTo(first.targetAmount());
        assertThat(next.deadline()).isEqualTo(first.deadline());
        assertThat(next.termProvenance().get("targetAmount").classification()).isEqualTo(EvidenceClassification.SEC_FILED_FACT);
        assertThat(notifications.queueNewConfirmed(refreshed, next)).isFalse();
        var terms = EffectiveOfferingTerms.resolve(refreshed, null, offerings.facts(refreshed.id()));
        assertThat(AutonomousDiligenceService.completeness(refreshed, terms, List.of())).isEqualTo(first.completeness());
        assertThat(AutonomousDiligenceService.status(refreshed, terms, List.of(), List.of())).isEqualTo(PacketStatus.PARTIAL);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deal_workspaces", Integer.class)).isZero();
        org.mockito.Mockito.verifyNoInteractions(sender);
    }

    @ParameterizedTest
    @ValueSource(strings={"Other|25562.50|4989800|2027-08-11","Preferred Stock|100000|1050000|2027-08-17"})
    void retainedEvidenceRepairIsDryByDefaultAndApplyIsIdempotent(String fixture) {
        String[] values = fixture.split("\\|");
        Offering offering = initial(values[0], values[1], values[2], values[3]);
        retainAndDegrade(offering);
        var plan = repair.dryRun(25);
        assertThat(plan.dryRun()).isTrue();
        assertThat(plan.mutationCount()).isZero();
        assertThat(plan.proposals()).hasSize(4);
        assertThat(offerings.find(offering.id()).orElseThrow().targetAmount()).isNull();
        assertThat(plan.proposals()).allSatisfy(p -> {
            assertThat(p.classification()).isEqualTo("SEC_FILED_FACT");
            assertThat(p.evidenceSource()).isEqualTo(offering.secFilingUrl());
            assertThat(p.evidenceId()).isPositive();
        });
        assertThat(repair.apply(plan, Set.of(offering.id()))).isEqualTo(4);
        assertThat(repair.apply(plan, Set.of(offering.id()))).isZero();
        assertThat(offerings.find(offering.id()).orElseThrow().targetAmount()).isEqualByComparingTo(values[1]);
        assertThat(offerings.find(offering.id()).orElseThrow().securityType()).isEqualTo(values[0]);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM radar_notification_events", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deal_workspaces", Integer.class)).isZero();
    }

    @Test
    void repairNeverOverwritesNewerCanonicalValueAndRejectsStaleApply() {
        Offering offering = initial("Preferred Stock", "100000", "1050000", "2027-08-17");
        retainAndDegrade(offering);
        var plan = repair.dryRun(25);
        jdbc.update("UPDATE radar_offerings SET target_amount=150000 WHERE id=?", offering.id());
        assertThat(repair.dryRun(25).proposals()).noneMatch(p -> p.field().equals("target_amount"));
        assertThatThrownBy(() -> repair.apply(plan, Set.of(offering.id()))).isInstanceOf(IllegalStateException.class);
        assertThat(offerings.find(offering.id()).orElseThrow().targetAmount()).isEqualByComparingTo("150000");
    }

    @Test
    void repairRejectsOldAccessionConflictingEvidenceAndIdentityGuessing() {
        Offering offering = initial("Preferred Stock", "100000", "1050000", "2027-08-17");
        retainAndDegrade(offering);
        jdbc.update("UPDATE radar_offerings SET sec_accession_number='0001234567-26-000082',match_status='LIKELY' WHERE id=?", offering.id());
        var plan = repair.dryRun(25);
        assertThat(plan.proposals()).isEmpty();
        assertThat(plan.unresolved()).anyMatch(message -> message.contains("identity not repaired"));
        assertThatThrownBy(() -> repair.apply(plan, Set.of())).isInstanceOf(IllegalArgumentException.class);
    }

    private void retainAndDegrade(Offering offering) {
        List<EvidenceDraft> facts = List.of(
                evidence(offering, "security_type", offering.securityType()),
                evidence(offering, "target_amount", offering.targetAmount().toPlainString()),
                evidence(offering, "maximum_amount", offering.maximumAmount().toPlainString()),
                evidence(offering, "deadline", offering.deadline().toString()));
        diligence.savePacket(packet(offering, facts));
        jdbc.update("UPDATE radar_offerings SET security_type=NULL,target_amount=NULL,maximum_amount=NULL,deadline=NULL WHERE id=?", offering.id());
    }
    private EvidenceDraft evidence(Offering offering, String key, String value) {
        return new EvidenceDraft("SEC_EDGAR", offering.secFilingUrl(), "SEC Form C", key, value, null,
                EvidenceClassification.SEC_FILED_FACT, 95, "Retained filed value", Map.of("accessionNumber", offering.accessionNumber()));
    }
    private PacketDraft packet(Offering offering, List<EvidenceDraft> facts) {
        var terms = EffectiveOfferingTerms.resolve(offering, null);
        return new PacketDraft(companyId, offering.id(), PacketStatus.PARTIAL, "CONFIRMED",
                AutonomousDiligenceService.completeness(offering, terms, List.of()), 95, "Public fixture",
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of("SEC_EDGAR"), List.of(),
                "fixture", facts, List.of());
    }
    private Offering initial(String security, String target, String maximum, String deadline) {
        return offerings.upsert(candidate(ACCESSION, "C", RetrievalQuality.DETAIL_COMPLETE, security, target, maximum, deadline,
                "https://refresh.example"), confirmed()).offering();
    }
    private Match confirmed() { return new Match(companyId, MatchStatus.CONFIRMED, 100, "Verified name and domain"); }
    private Match weak() { return new Match(companyId, MatchStatus.LIKELY, 85, "Domain unavailable this run"); }
    private Candidate candidate(String accession, String form, RetrievalQuality quality, String security,
            String target, String maximum, String deadline, String website) {
        boolean complete = quality == RetrievalQuality.DETAIL_COMPLETE;
        return new Candidate("Refresh Fixture", "0001234567", website, complete ? "Republic" : "UNKNOWN",
                complete ? "OpenDeal Portal LLC" : null, complete ? "0000123456" : null,
                complete ? "https://republic.com/refresh-fixture" : null,
                "https://www.sec.gov/Archives/edgar/data/1234567/" + accession.replace("-", "") + "/" + accession + "-index.html",
                accession, quality == RetrievalQuality.INDEX_ONLY ? null : "020-refresh", form,
                LocalDate.of(2026, 8, 1), security, complete ? new BigDecimal("100") : null,
                target == null ? null : new BigDecimal(target), maximum == null ? null : new BigDecimal(maximum),
                complete ? "7000000 valuation cap" : null, deadline == null ? null : LocalDate.parse(deadline),
                complete ? new BigDecimal("500000") : null, "SEC_EDGAR_RECENT",
                website == null ? Map.of() : Map.of("issuerWebsite", website), quality);
    }
    private PlatformCampaign campaign(long id, boolean populated, CampaignStatus status) {
        var time = populated ? LocalDateTime.of(2026, 8, 1, 12, 0) : LocalDateTime.of(2026, 8, 2, 12, 0);
        return new PlatformCampaign(null, id, "REPUBLIC", "https://republic.com/refresh-fixture", "PUBLIC_CAMPAIGN_URL",
                95, status, "Refresh Fixture", populated ? "SAFE" : null, populated ? new BigDecimal("100") : null,
                null, null, populated ? new BigDecimal("7000000") : null, null,
                null, null, null, null, populated ? LocalDate.of(2027, 8, 17) : null,
                "Refresh Fixture", Map.of(), populated ? "raw:complete" : "raw:partial", time, time);
    }
    private NativeOfferingCandidate nativeCandidate(boolean populated, com.startupvalidationbot.offering.intake.NativeOfferingDomain.Status status) {
        return new NativeOfferingCandidate("REPUBLIC_DIRECTORY", "Republic", "refresh-fixture", "Refresh Fixture",
                "https://republic.com/refresh-fixture", populated ? "https://refresh.example" : null, null, status,
                populated ? "REG_CF" : "UNKNOWN", populated ? "OpenDeal Portal LLC" : null, populated ? "SAFE" : null,
                null, null, null, populated ? "$7M cap" : null, populated ? new BigDecimal("100") : null, null,
                populated ? LocalDate.of(2027, 8, 17) : null, null, null, null, null, null, null, null, null,
                Map.of("_retrievalQuality", populated ? "DETAIL_COMPLETE" : "PARTIAL_DETAIL"), LocalDateTime.now());
    }
}
