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

import com.startupvalidationbot.diligence.DiligenceDomain.CampaignStatus;
import com.startupvalidationbot.diligence.DiligenceDomain.EvidenceClassification;
import com.startupvalidationbot.diligence.DiligenceDomain.FinancialPeriod;
import com.startupvalidationbot.diligence.DiligenceDomain.PacketStatus;
import com.startupvalidationbot.diligence.DiligenceDomain.PlatformCampaign;
import com.startupvalidationbot.diligence.DiligenceStore.EvidenceDraft;
import com.startupvalidationbot.diligence.DiligenceStore.PacketDraft;
import com.startupvalidationbot.offering.OfferingDomain.Candidate;
import com.startupvalidationbot.offering.OfferingDomain.Match;
import com.startupvalidationbot.offering.OfferingDomain.MatchStatus;
import com.startupvalidationbot.offering.OfferingStore;

@SpringBootTest
@Transactional
class DiligenceStoreIntegrationTest {
    @Autowired DiligenceStore store;
    @Autowired OfferingStore offerings;
    @Autowired JdbcTemplate jdbc;

    @Test
    void campaignPacketEvidenceFinancialAndNotificationWritesAreIdempotent() {
        long companyId = company();
        long offeringId = offerings.upsert(candidate(), new Match(companyId, MatchStatus.CONFIRMED, 100, "matched")).offering().id();
        PlatformCampaign campaign = campaign(offeringId, "fingerprint-one");
        store.upsertCampaign(campaign);
        store.upsertCampaign(campaign(offeringId, "fingerprint-two"));
        store.upsertCampaign(campaign(offeringId, "PUBLIC_CAMPAIGN_URL", "raw:detail"));
        store.upsertCampaign(campaign(offeringId, "PUBLIC_LIVE_DIRECTORY", "raw:directory"));

        PacketDraft packet = packet(companyId, offeringId, "packet-one");
        long firstId = store.savePacket(packet).id();
        var stored = store.savePacket(packet(companyId, offeringId, "packet-two"));
        long secondId = stored.id();

        assertThat(secondId).isEqualTo(firstId);
        assertThat(store.findCampaign(offeringId)).get().extracting(PlatformCampaign::sourceFingerprint)
                .isEqualTo("raw:detail");
        assertThat(stored.securityType()).isEqualTo("SAFE");
        assertThat(stored.minimumInvestment()).isEqualByComparingTo("100");
        assertThat(stored.valuationCap()).isEqualByComparingTo("12000000");
        assertThat(stored.termProvenance().get("minimumInvestment").classification())
                .isEqualTo(EvidenceClassification.SEC_FILED_FACT);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM radar_platform_campaigns", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM radar_diligence_packets", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM radar_diligence_evidence", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM radar_diligence_financials", Integer.class)).isEqualTo(1);
        assertThat(store.queueNotification("DILIGENCE_READY", "PACKET", firstId, "same-fingerprint", "to@example.com",
                "subject", "text", "html")).isTrue();
        assertThat(store.queueNotification("DILIGENCE_READY", "PACKET", firstId, "same-fingerprint", "to@example.com",
                "subject", "text", "html")).isFalse();
    }

    private PacketDraft packet(long companyId, long offeringId, String fingerprint) {
        var evidence = new EvidenceDraft("SEC_EDGAR", "https://www.sec.gov/example", "Form C", "revenue",
                "35992", "FY2025", EvidenceClassification.SEC_FILED_FACT, 95, "Filed value", Map.of());
        var financial = new FinancialPeriod("FY2025", new BigDecimal("35992"), null,
                new BigDecimal("-14000"), new BigDecimal("9000"), new BigDecimal("25000"), null,
                null, null, null, "000123", "https://www.sec.gov/example");
        return new PacketDraft(companyId, offeringId, PacketStatus.READY, "CONFIRMED", 90, 95,
                "Public evidence packet", List.of("Bull"), List.of("Bear"), List.of("Illiquidity"),
                List.of("Verify claims"), List.of(), List.of("Monitor amendments"), List.of("SEC_EDGAR"),
                List.of(), fingerprint, List.of(evidence), List.of(financial));
    }

    @Test
    void selectionUsesDiligenceAgeIncludesUnresolvedAndExcludesTerminalRows() {
        long companyId = company();
        LocalDateTime now = LocalDateTime.of(2026, 10, 6, 8, 0);
        var ids = new java.util.ArrayList<Long>();
        for (int i = 1; i <= 100; i++) {
            Candidate c = candidate();
            Candidate unique = new Candidate("Issuer " + i, String.format("%010d", i), c.issuerWebsite(),
                    c.platform(), c.intermediaryName(), c.intermediaryCik(), null, c.secFilingUrl(),
                    String.format("%010d-26-000001", i), "020-" + i, "C", now.toLocalDate(), c.securityType(),
                    c.minimumInvestment(), c.targetAmount(), c.maximumAmount(), c.valuationOrCap(),
                    now.plusYears(1).toLocalDate(), null, "TEST", Map.of());
            long id = offerings.upsert(unique, new Match(companyId, i > 50 && i <= 75 ? MatchStatus.AMBIGUOUS
                    : MatchStatus.CONFIRMED, 80, "fixture")).offering().id();
            ids.add(id);
            if (i > 25) {
                store.savePacket(packet(companyId, id, "packet-" + i));
                LocalDateTime age = now.minusDays(i <= 75 ? 20 : 2);
                jdbc.update("UPDATE radar_diligence_packets SET status=?,last_refreshed_at=? WHERE offering_id=?",
                        i <= 75 ? "NEEDS_REVIEW" : "PARTIAL", age, id);
                jdbc.update("UPDATE radar_offerings SET last_diligence_attempt_at=? WHERE id=?", age, id);
            }
            jdbc.update("UPDATE radar_offerings SET created_at=?,updated_at=? WHERE id=?", now.minusDays(60).plusMinutes(i), now, id);
        }
        jdbc.update("UPDATE radar_offerings SET status='CLOSED' WHERE id=?", ids.get(0));
        jdbc.update("UPDATE radar_offerings SET deadline=? WHERE id=?", now.minusDays(1).toLocalDate(), ids.get(1));
        var selected = store.selectOfferings(25, now);
        assertThat(store.refreshCandidates(now)).hasSize(98);
        assertThat(selected.ids()).hasSize(25).doesNotHaveDuplicates().doesNotContain(ids.get(0), ids.get(1));
        assertThat(selected.counts().values()).allMatch(value -> value > 0);
        store.markDiligenceAttempt(selected.ids().get(0));
        assertThat(jdbc.queryForObject("SELECT updated_at FROM radar_offerings WHERE id=?",
                LocalDateTime.class, selected.ids().get(0))).isEqualTo(now);
    }

    @Test
    void secRepresentationDuplicatesShareSemanticIdentityAndPartialRefreshKeepsFinancials() {
        long companyId = company();
        long offeringId = offerings.upsert(candidate(), new Match(companyId, MatchStatus.CONFIRMED, 100, "matched")).offering().id();
        var base = packet(companyId, offeringId, "first");
        var xml = new EvidenceDraft("SEC_EDGAR", "https://www.sec.gov/primary.xml", "Form C", "revenue",
                "100", "MOST_RECENT_FISCAL_YEAR", EvidenceClassification.SEC_FILED_FACT, 95, "revenue", Map.of("accessionNumber", "one"));
        var html = new EvidenceDraft("SEC_EDGAR", "https://www.sec.gov/primary.html", "Form C", "revenue",
                "100", "MOST_RECENT_FISCAL_YEAR", EvidenceClassification.SEC_FILED_FACT, 95, "revenue", Map.of("accessionNumber", "one"));
        var full = new FinancialPeriod("MOST_RECENT_FISCAL_YEAR", new BigDecimal("100"), new BigDecimal("20"),
                null, null, null, null, null, null, null, "one", xml.sourceUrl(), new BigDecimal("80"),
                new BigDecimal("45"), new BigDecimal("10"), new BigDecimal("35"), LocalDate.of(2025, 12, 31));
        var partial = new FinancialPeriod(full.period(), null, null, new BigDecimal("12"), null, null, null,
                null, null, null, "two", "https://www.sec.gov/amendment.xml");
        long packetId = store.savePacket(new PacketDraft(companyId, offeringId, base.status(), base.identityStatus(),
                base.completeness(), base.confidence(), base.summary(), base.bullCase(), base.bearCase(), base.keyRisks(),
                base.unansweredQuestions(), base.discrepancies(), base.milestones(), base.sourcesChecked(),
                base.dataNotFound(), "first", List.of(xml, html), List.of(full))).id();
        store.savePacket(new PacketDraft(companyId, offeringId, base.status(), base.identityStatus(), base.completeness(),
                base.confidence(), base.summary(), base.bullCase(), base.bearCase(), base.keyRisks(), base.unansweredQuestions(),
                base.discrepancies(), base.milestones(), base.sourcesChecked(), base.dataNotFound(), "second", List.of(), List.of(partial)));
        assertThat(store.find(packetId).orElseThrow().evidence()).hasSize(1);
        assertThat(store.find(packetId).orElseThrow().evidence().get(0).metadata())
                .containsEntry("sourceDocuments", List.of(xml.sourceUrl(), html.sourceUrl()));
        var retained = store.find(packetId).orElseThrow().financials().get(0);
        assertThat(retained.revenue()).isEqualByComparingTo("100");
        assertThat(retained.grossProfit()).isEqualByComparingTo("80");
        assertThat(retained.periodEndingDate()).isEqualTo(full.periodEndingDate());
        assertThat(retained.netIncome()).isEqualByComparingTo("12");
    }

    private PlatformCampaign campaign(long offeringId, String fingerprint) {
        return campaign(offeringId, "SEC_OFFERING_URL", fingerprint);
    }

    private PlatformCampaign campaign(long offeringId, String source, String fingerprint) {
        LocalDateTime now = LocalDateTime.now();
        return new PlatformCampaign(null, offeringId, "WEFUNDER", "https://wefunder.com/acme", source,
                100, CampaignStatus.ACTIVE, "Acme Technologies, Inc.", "Crowd SAFE", new BigDecimal("100"),
                null, null, new BigDecimal("12000000"), null, new BigDecimal("100000"),
                new BigDecimal("1235000"), new BigDecimal("640000"), 814, LocalDate.of(2026, 10, 31),
                "Acme Energy", Map.of("amountRaised", "640000"), fingerprint, now, now);
    }

    private long company() {
        LocalDateTime now = LocalDateTime.now();
        jdbc.update("""
                INSERT INTO radar_companies (name,normalized_name,domain,website_url,description,sector,categories_json,
                  aliases_json,radar_score,personal_score,score_reasoning,source_count,first_seen_at,last_seen_at,ignored,
                  accelerator,accelerator_batch,created_at,updated_at)
                VALUES ('Acme Technologies','acme technologies','acme.example','https://acme.example','','Energy',
                  '[]','[]',0,0,'',0,?,?,false,'','',?,?)
                """, now, now, now, now);
        return jdbc.queryForObject("SELECT id FROM radar_companies WHERE domain='acme.example'", Long.class);
    }

    private Candidate candidate() {
        return new Candidate("Acme Technologies, Inc.", "0001234567", "https://acme.example", "Wefunder",
                "Wefunder Portal LLC", null, "https://wefunder.com/acme", "https://www.sec.gov/example",
                "0001234567-26-000001", "020-1", "C", LocalDate.now(), "Crowd SAFE", new BigDecimal("100"),
                new BigDecimal("100000"), new BigDecimal("1235000"), "$12M cap", LocalDate.of(2026, 10, 31),
                new BigDecimal("640000"), "TEST", Map.of());
    }
}
