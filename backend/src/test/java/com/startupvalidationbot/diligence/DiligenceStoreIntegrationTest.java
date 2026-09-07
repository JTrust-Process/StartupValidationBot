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

        PacketDraft packet = packet(companyId, offeringId, "packet-one");
        long firstId = store.savePacket(packet).id();
        long secondId = store.savePacket(packet(companyId, offeringId, "packet-two")).id();

        assertThat(secondId).isEqualTo(firstId);
        assertThat(store.findCampaign(offeringId)).get().extracting(PlatformCampaign::sourceFingerprint)
                .isEqualTo("fingerprint-two");
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

    private PlatformCampaign campaign(long offeringId, String fingerprint) {
        LocalDateTime now = LocalDateTime.now();
        return new PlatformCampaign(null, offeringId, "WEFUNDER", "https://wefunder.com/acme", "SEC_OFFERING_URL",
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
