package com.startupvalidationbot.diligence;

import static com.startupvalidationbot.diligence.DiligenceDomain.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ResearchTriageTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 8);
    private static final LocalDateTime NOW = TODAY.atTime(16, 0);
    private static final String SEC = "https://www.sec.gov/Archives/test.xml";

    @Test void deadlinesPrecedeSubstantiveResearchAndEarlierDeadlinesBreakTies() {
        Fixture ordinary = new Fixture(1);
        Fixture later = new Fixture(2); later.deadline = TODAY.plusDays(5);
        Fixture earlier = new Fixture(3); earlier.deadline = TODAY.plusDays(2);
        var queue = queue(ordinary, later, earlier);
        assertThat(queue.attention()).extracting(e -> e.packet().id()).containsExactly(3L, 2L, 1L);
        assertThat(queue.attention().get(0).reasons()).filteredOn(r -> r.code().equals("APPROACHING_SOURCE_DEADLINE"))
                .singleElement().satisfies(r -> assertThat(r.supports().get(0).sourceUrl()).isEqualTo(SEC));
    }
    @Test void sameDayIsTimeSensitiveButExpiredOfferingsRemainHistorical() {
        Fixture today = new Fixture(1); today.deadline = TODAY;
        Fixture expired = new Fixture(2); expired.deadline = TODAY.minusDays(1);
        var queue = queue(today, expired);
        assertThat(queue.attention()).hasSize(1);
        assertThat(queue.entries().get(1).category()).isEqualTo(ResearchTriage.Category.ENDED);
        assertThat(queue.entries().get(1).researchPriority()).isZero();
    }
    @Test void completedWithdrawnAndTerminatedOverrideFutureDeadlinesWhileExtensionDoesNotClose() {
        Fixture completed = new Fixture(1); completed.completed = true;
        Fixture withdrawn = new Fixture(2); withdrawn.lifecycle = "WITHDRAWN";
        Fixture terminated = new Fixture(3); terminated.lifecycle = "TERMINATED";
        Fixture extended = new Fixture(4); extended.filedLifecycle = "EXTENDED";
        assertThat(queue(completed, withdrawn, terminated, extended).attention()).extracting(e -> e.packet().id()).containsExactly(4L);
    }
    @Test void unknownLifecycleAndUnprovenDeadlineNeverCreateUrgency() {
        Fixture unknown = new Fixture(1); unknown.lifecycle = "UNKNOWN"; unknown.deadline = TODAY.plusDays(1);
        Fixture unproven = new Fixture(2); unproven.deadline = TODAY.plusDays(1); unproven.provenance = Map.of();
        Fixture unlinked = new Fixture(3); unlinked.deadline = TODAY.plusDays(1);
        unlinked.provenance = Map.of("deadline", new TermProvenance("SEC_EDGAR", null, EvidenceClassification.SEC_FILED_FACT));
        assertThat(queue(unknown, unproven, unlinked).entries()).noneMatch(e -> e.category() == ResearchTriage.Category.TIME_SENSITIVE);
    }
    @Test void ambiguousIdentityPrioritizesInvestigationWithoutChangingPacketStatusOrMatch() {
        Fixture ambiguous = new Fixture(2); ambiguous.identity = "AMBIGUOUS"; ambiguous.confidence = 40;
        var queue = queue(new Fixture(1), ambiguous);
        assertThat(queue.attention().get(0).packet().id()).isEqualTo(2);
        assertThat(queue.attention().get(0).packet().status()).isEqualTo(PacketStatus.PARTIAL);
        assertThat(queue.attention().get(0).currentIdentityConfidence()).isEqualTo(40);
        assertThat(queue.attention().get(0).reasons()).anyMatch(r -> r.code().equals("ISSUER_IDENTITY_UNRESOLVED"));
    }
    @Test void blockedAccessAloneNeverPromotesPacketEvenWhenVeryStale() {
        Fixture blocked = new Fixture(1); blocked.fetch = "HTTP_403"; blocked.financials = List.of(); blocked.evidence = List.of();
        blocked.refresh = NOW.minusDays(90);
        Fixture substantive = new Fixture(2);
        var queue = queue(blocked, substantive);
        assertThat(queue.attention()).extracting(e -> e.packet().id()).containsExactly(2L);
        assertThat(queue.entries().get(1).category()).isEqualTo(ResearchTriage.Category.BLOCKED);
        assertThat(queue.entries().get(1).researchPriority()).isZero();
        assertThat(queue.entries().get(1).reasons()).filteredOn(r -> r.code().equals("SOURCE_ACCESS_BLOCKED"))
                .singleElement().satisfies(r -> assertThat(r.weight()).isZero());
    }
    @Test void blockedCampaignDoesNotHideSubstantiveSecResearchAnd403AddsNoScore() {
        Fixture clear = new Fixture(1); Fixture blocked = new Fixture(2); blocked.fetch = "HTTP_403";
        var queue = queue(clear, blocked);
        assertThat(queue.entries().get(0).researchPriority()).isEqualTo(queue.entries().get(1).researchPriority());
        assertThat(queue.entries()).allMatch(e -> e.category() == ResearchTriage.Category.SUBSTANTIVE_RESEARCH);
    }
    @Test void transientRecheckRequiresSubstantiveEvidenceAndSevenDayCooldown() {
        Fixture old = new Fixture(1); old.fetch = "HTTP_503"; old.attempt = NOW.minusDays(8);
        Fixture recent = new Fixture(2); recent.fetch = "HTTP_503";
        Fixture unspecified = new Fixture(3); unspecified.fetch = "UNAVAILABLE"; unspecified.attempt = NOW.minusDays(20);
        var queue = queue(old, recent, unspecified);
        assertThat(queue.entries().get(0).reasons()).anyMatch(r -> r.code().equals("RECOVERABLE_SOURCE_FAILURE"));
        assertThat(queue.entries().subList(1, 3)).allMatch(e -> e.reasons().stream().noneMatch(r -> r.code().equals("RECOVERABLE_SOURCE_FAILURE")));
    }
    @Test void staleSubstantiveDataGetsBoundedAttentionAndFutureDatesDoNot() {
        Fixture old = new Fixture(2); old.refresh = NOW.minusDays(8);
        Fixture future = new Fixture(3); future.refresh = NOW.plusDays(2);
        assertThat(queue(new Fixture(1), old, future).attention().get(0).packet().id()).isEqualTo(2);
        assertThat(queue(future).entries().get(0).reasons()).noneMatch(r -> r.code().equals("STALE_DILIGENCE"));
    }
    @Test void missingValuesAreDisclosedNeutrallyAndAiSynthesisDoesNotCountAsEvidence() {
        Fixture empty = new Fixture(1); empty.security = null; empty.minimum = null; empty.deadline = null;
        empty.financials = List.of(); empty.evidence = List.of(new Evidence(1, "AI", SEC, "AI", "revenue", "100", null,
                EvidenceClassification.AI_SYNTHESIS, NOW, 100, "AI", Map.of()));
        var entry = queue(empty).entries().get(0);
        assertThat(entry.researchPriority()).isZero();
        assertThat(entry.latestEvidenceAt()).isNull();
        assertThat(entry.reasons()).anyMatch(r -> r.explanation().contains("not a negative investment signal"));
        assertThat(entry.questions()).anyMatch(q -> q.contains("minimum investment"));
    }
    @Test void recentAmendmentsUseFiledDatesAndNotImportTimesOrFutureDates() {
        Fixture recent = new Fixture(2); recent.filings = List.of(filing("new", "C/A", TODAY.minusDays(2)));
        Fixture old = new Fixture(1); old.filings = List.of(filing("old", "C/A", TODAY.minusDays(90)));
        Fixture future = new Fixture(3); future.filings = List.of(filing("future", "C/A", TODAY.plusDays(1)));
        Fixture reviewed = new Fixture(4); reviewed.filings = recent.filings; reviewed.reviewed = NOW;
        var queue = queue(old, recent, future, reviewed);
        assertThat(queue.attention().get(0).packet().id()).isEqualTo(2);
        assertThat(queue.entries().subList(1, 4)).allMatch(e -> e.reasons().stream().noneMatch(r -> r.code().equals("RECENT_SEC_AMENDMENT")));
    }
    @Test void comparableTermChangesHaveBothEvidenceReferencesButFormattingAndDifferentSemanticsDoNot() {
        Fixture changed = new Fixture(1);
        changed.filings = List.of(filing("old", "C", TODAY.minusDays(30)), filing("new", "C/A", TODAY.minusDays(1)));
        changed.evidence = List.of(termEvidence(1, "old", "100", "MINIMUM_INVESTMENT"), termEvidence(2, "new", "500", "MINIMUM_INVESTMENT"));
        var reason = queue(changed).entries().get(0).reasons().stream().filter(r -> r.code().equals("SOURCE_BACKED_TERM_CHANGE")).findFirst().orElseThrow();
        assertThat(reason.supports()).extracting(ResearchTriage.Support::evidenceId).containsExactly(1L, 2L);
        changed.evidence = List.of(termEvidence(1, "old", "100.00", "MINIMUM_INVESTMENT"), termEvidence(2, "new", "100", "MINIMUM_INVESTMENT"));
        assertThat(queue(changed).entries().get(0).reasons()).noneMatch(r -> r.code().equals("SOURCE_BACKED_TERM_CHANGE"));
        changed.evidence = List.of(termEvidence(1, "old", "100", "MINIMUM_INVESTMENT"), termEvidence(2, "new", "500", "CONDITIONAL_MINIMUM"));
        assertThat(queue(changed).entries().get(0).reasons()).noneMatch(r -> r.code().equals("SOURCE_BACKED_TERM_CHANGE"));
    }
    @Test void amendmentComparesLatestApplicablePriorFilingNotAnyOlderDifferentValue() {
        Fixture repeated = new Fixture(1);
        repeated.filings = List.of(filing("old", "C", TODAY.minusDays(90)), filing("prior", "C/A", TODAY.minusDays(30)),
                filing("new", "C/A", TODAY.minusDays(1)));
        repeated.evidence = List.of(termEvidence(99, "old", "100", "MINIMUM_INVESTMENT"),
                termEvidence(2, "prior", "500", "MINIMUM_INVESTMENT"), termEvidence(3, "new", "500", "MINIMUM_INVESTMENT"));
        assertThat(queue(repeated).entries().get(0).reasons()).noneMatch(r -> r.code().equals("SOURCE_BACKED_TERM_CHANGE"));
        repeated.evidence = List.of(termEvidence(1, "prior", "100", "MINIMUM_INVESTMENT"),
                termEvidence(2, "prior", "200", "MINIMUM_INVESTMENT"), termEvidence(3, "new", "500", "MINIMUM_INVESTMENT"));
        assertThat(queue(repeated).entries().get(0).reasons()).noneMatch(r -> r.code().equals("SOURCE_BACKED_TERM_CHANGE"));
    }
    @Test void tiesUseStablePacketIdsAndAttentionHasFiveDistinctCompanies() {
        Fixture duplicate = new Fixture(2); duplicate.company = 1;
        var queue = queue(new Fixture(7), new Fixture(6), new Fixture(5), new Fixture(4), new Fixture(3), duplicate, new Fixture(1));
        assertThat(queue.entries()).extracting(e -> e.packet().id()).containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L);
        assertThat(queue.attention()).extracting(e -> e.packet().id()).containsExactly(1L, 3L, 4L, 5L, 6L);
    }
    @Test void rejectedIdentityIsBlockedAndDiscrepanciesHaveTraceableNeutralReasons() {
        Fixture rejected = new Fixture(1); rejected.identity = "REJECTED"; rejected.deadline = TODAY;
        Fixture discrepancy = new Fixture(2); discrepancy.discrepancies = List.of("SEC minimum differs from platform claim");
        var queue = queue(rejected, discrepancy);
        assertThat(queue.attention()).extracting(e -> e.packet().id()).containsExactly(2L);
        assertThat(queue.attention().get(0).reasons()).filteredOn(r -> r.code().equals("RECORDED_DISCREPANCIES"))
                .singleElement().satisfies(r -> assertThat(r.supports().get(0).field()).isEqualTo("packet.materialDiscrepancies"));
    }
    private static ResearchTriage.Queue queue(Fixture... fixtures) {
        return ResearchTriage.queue(java.util.Arrays.stream(fixtures).map(Fixture::input).toList(), NOW, TODAY, "America/New_York");
    }
    private static ResearchTriage.Filing filing(String accession, String form, LocalDate date) {
        return new ResearchTriage.Filing(accession, form, date, SEC);
    }
    private static Evidence termEvidence(long id, String accession, String value, String semantic) {
        return new Evidence(id, "SEC_EDGAR", SEC, "SEC term", "filed_minimum", value, null, EvidenceClassification.SEC_FILED_FACT,
                NOW, 95, "Filed term", Map.of("accessionNumber", accession, "semanticType", semantic, "units", "USD"));
    }
    private static class Fixture {
        long id, company;
        String identity = "CONFIRMED", lifecycle = "ACTIVE", filedLifecycle, fetch, security = "SAFE";
        int confidence = 95;
        boolean completed;
        LocalDate deadline = TODAY.plusDays(90);
        LocalDateTime refresh = NOW.minusDays(1), attempt = NOW.minusDays(1), reviewed;
        BigDecimal minimum = new BigDecimal("100");
        List<String> discrepancies = List.of();
        List<ResearchTriage.Filing> filings = List.of();
        Map<String, TermProvenance> provenance = Map.of("deadline", new TermProvenance("SEC_EDGAR", SEC, EvidenceClassification.SEC_FILED_FACT));
        List<Evidence> evidence = List.of(new Evidence(1, "SEC_EDGAR", SEC, "SEC", "target_amount", "10000", null,
                EvidenceClassification.SEC_FILED_FACT, NOW.minusDays(1), 95, "Filed", Map.of()));
        List<FinancialPeriod> financials = List.of(new FinancialPeriod("FY2025", BigDecimal.ZERO, null, null, null, null, null,
                null, null, null, "old", SEC));
        Fixture(long id) { this.id = id; this.company = id; }
        ResearchTriage.Input input() {
            Packet packet = new Packet(id, company, "Company " + id, id, "Wefunder", null, SEC, PacketStatus.PARTIAL,
                    identity, 65, confidence, "", List.of(), List.of(), List.of(), List.of(), discrepancies, List.of(), List.of(), List.of(),
                    NOW.minusDays(10), refresh, reviewed, security, minimum, new BigDecimal("10000"), new BigDecimal("1000000"),
                    null, "$12M cap", deadline, evidence, financials, null, new BigDecimal("12000000"), null, "SEC_CONFIRMED", "SEC", provenance);
            return new ResearchTriage.Input(packet, lifecycle, identity, confidence, filedLifecycle, completed, fetch, attempt, filings);
        }
    }
}
