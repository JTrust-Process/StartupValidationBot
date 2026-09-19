package com.startupvalidationbot.diligence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.startupvalidationbot.diligence.DiligenceDomain.CampaignStatus;
import com.startupvalidationbot.diligence.DiligenceDomain.EvidenceClassification;
import com.startupvalidationbot.diligence.DiligenceDomain.FinancialPeriod;
import com.startupvalidationbot.diligence.DiligenceDomain.PacketStatus;
import com.startupvalidationbot.diligence.DiligenceDomain.PlatformCampaign;
import com.startupvalidationbot.offering.OfferingDomain.MatchStatus;
import com.startupvalidationbot.offering.OfferingDomain.Offering;
import com.startupvalidationbot.offering.OfferingDomain.Status;

class EffectiveOfferingTermsTest {
    @Test
    void secTermsOverridePlatformWhilePlatformFillsSecNullFieldsWithHonestProvenance() {
        Offering offering = offering("SEC_EDGAR", "SEC_RECONCILED", MatchStatus.CONFIRMED,
                "Preferred Stock", new BigDecimal("250"), null, new BigDecimal("1000000"),
                "$20M valuation", LocalDate.of(2026, 12, 31));
        PlatformCampaign campaign = campaign("SAFE", new BigDecimal("100"), new BigDecimal("100000"),
                new BigDecimal("1200000"), null, new BigDecimal("7000000"), LocalDate.of(2026, 10, 1));

        var terms = EffectiveOfferingTerms.resolve(offering, campaign);

        assertThat(terms.securityType()).isEqualTo("Preferred Stock");
        assertThat(terms.minimumInvestment()).isEqualByComparingTo("250");
        assertThat(terms.targetAmount()).isEqualByComparingTo("100000");
        assertThat(terms.valuation()).isEqualByComparingTo("20000000");
        assertThat(terms.valuationCap()).isEqualByComparingTo("7000000");
        assertThat(terms.deadline()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(terms.provenance().get("securityType").classification())
                .isEqualTo(EvidenceClassification.SEC_FILED_FACT);
        assertThat(terms.provenance().get("targetAmount").classification())
                .isEqualTo(EvidenceClassification.PLATFORM_ISSUER_CLAIM);
        assertThat(terms.provenance().get("targetAmount").sourceType()).isEqualTo("REPUBLIC public campaign");
    }

    @Test
    void platformTermsDriveCompletenessAndMissingFieldsWithoutBecomingFiledFacts() {
        Offering offering = offering("PLATFORM_OFFERING", "PLATFORM_CONFIRMED", MatchStatus.LIKELY,
                null, null, null, null, null, null);
        PlatformCampaign campaign = campaign("SAFE", new BigDecimal("100"), null, null,
                null, new BigDecimal("7000000"), LocalDate.of(2026, 10, 1));
        var terms = EffectiveOfferingTerms.resolve(offering, campaign);

        assertThat(AutonomousDiligenceService.completeness(offering, terms, List.of())).isEqualTo(55);
        assertThat(AutonomousDiligenceService.missing(terms, List.of()))
                .doesNotContain("Minimum investment", "Valuation or valuation cap", "Security type", "Offering deadline")
                .contains("Structured multi-period SEC financials", "Target or maximum amount");
        assertThat(terms.provenance().get("minimumInvestment").classification())
                .isEqualTo(EvidenceClassification.PLATFORM_ISSUER_CLAIM);
        assertThat(AutonomousDiligenceService.status(offering, terms, List.of(), List.of()))
                .isEqualTo(PacketStatus.PARTIAL);
        assertThat(AutonomousDiligenceService.questions(offering, terms, List.of(), List.of()))
                .doesNotContain("Does the issuer website domain independently match the tracked company?")
                .contains("Can the platform issuer be reconciled to an SEC Form C legal issuer?");
    }

    @Test
    void lowConfidenceCampaignValuesDoNotBecomeEffectiveTerms() {
        Offering offering = offering("PLATFORM_OFFERING", "PLATFORM_CONFIRMED", MatchStatus.LIKELY,
                null, null, null, null, null, null);
        PlatformCampaign campaign = campaign("SAFE", new BigDecimal("100"), null, null,
                null, new BigDecimal("7000000"), LocalDate.of(2026, 10, 1), 50);

        var terms = EffectiveOfferingTerms.resolve(offering, campaign);

        assertThat(terms.officialCampaignVerified()).isFalse();
        assertThat(terms.securityType()).isNull();
        assertThat(terms.minimumInvestment()).isNull();
        assertThat(terms.valuationCap()).isNull();
        assertThat(AutonomousDiligenceService.missing(terms, List.of()))
                .contains("Verified public campaign page", "Minimum investment", "Valuation or valuation cap");
    }

    @Test
    void suspiciousTermsAndIdentityConflictsRequireReviewWhileReadyRemainsStrict() {
        Offering nativeOffering = offering("PLATFORM_OFFERING", "PLATFORM_CONFIRMED", MatchStatus.LIKELY,
                null, null, null, null, null, null);
        PlatformCampaign inconsistent = campaign("SAFE", new BigDecimal("1000"), new BigDecimal("2000000"),
                new BigDecimal("1000000"), new BigDecimal("1.90"), null, LocalDate.of(2026, 10, 1));
        var suspicious = EffectiveOfferingTerms.resolve(nativeOffering, inconsistent);
        assertThat(suspicious.targetAmount()).isNull();
        assertThat(suspicious.maximumAmount()).isNull();
        assertThat(suspicious.amountRaised()).isNull();
        assertThat(suspicious.issues()).hasSize(2);
        assertThat(AutonomousDiligenceService.status(nativeOffering, suspicious, List.of(), suspicious.issues()))
                .isEqualTo(PacketStatus.NEEDS_REVIEW);

        Offering ambiguous = offering("PLATFORM_OFFERING", "PLATFORM_CONFIRMED", MatchStatus.AMBIGUOUS,
                null, null, null, null, null, null);
        var usable = EffectiveOfferingTerms.resolve(ambiguous, campaign("SAFE", new BigDecimal("100"), null,
                null, null, new BigDecimal("7000000"), LocalDate.of(2026, 10, 1)));
        assertThat(AutonomousDiligenceService.status(ambiguous, usable, List.of(), List.of()))
                .isEqualTo(PacketStatus.NEEDS_REVIEW);

        Offering confirmed = offering("SEC_EDGAR", "SEC_RECONCILED", MatchStatus.CONFIRMED,
                "SAFE", new BigDecimal("100"), new BigDecimal("100000"), new BigDecimal("1000000"),
                "$7M valuation cap", LocalDate.of(2026, 10, 1));
        assertThat(AutonomousDiligenceService.status(confirmed,
                EffectiveOfferingTerms.resolve(confirmed, usableCampaign()), List.of(financial()), List.of()))
                .isEqualTo(PacketStatus.READY);
        assertThat(AutonomousDiligenceService.status(confirmed,
                EffectiveOfferingTerms.resolve(confirmed, usableCampaign()), List.of(), List.of()))
                .isEqualTo(PacketStatus.PARTIAL);
    }

    private static PlatformCampaign usableCampaign() {
        return campaign("SAFE", new BigDecimal("100"), new BigDecimal("100000"),
                new BigDecimal("1000000"), null, new BigDecimal("7000000"), LocalDate.of(2026, 10, 1));
    }

    private static FinancialPeriod financial() {
        return new FinancialPeriod("2025", BigDecimal.ONE, null, null, null, null, null,
                null, null, null, "accession", "https://www.sec.gov/example");
    }

    private static PlatformCampaign campaign(String security, BigDecimal minimum, BigDecimal target,
            BigDecimal maximum, BigDecimal raised, BigDecimal cap, LocalDate deadline) {
        return campaign(security, minimum, target, maximum, raised, cap, deadline, 95);
    }

    private static PlatformCampaign campaign(String security, BigDecimal minimum, BigDecimal target,
            BigDecimal maximum, BigDecimal raised, BigDecimal cap, LocalDate deadline, int confidence) {
        LocalDateTime now = LocalDateTime.now();
        return new PlatformCampaign(1L, 1L, "REPUBLIC", "https://republic.com/example",
                "PUBLIC_CAMPAIGN_URL", confidence, CampaignStatus.ACTIVE, "Example", security, minimum,
                null, null, cap, null, target, maximum, raised, 20, deadline, "Example",
                Map.of(), "raw:source", now, now);
    }

    private static Offering offering(String provenance, String reconciliation, MatchStatus match,
            String security, BigDecimal minimum, BigDecimal target, BigDecimal maximum,
            String valuation, LocalDate deadline) {
        LocalDateTime now = LocalDateTime.now();
        return new Offering(1L, 7L, "Example", "Example, Inc.", "0001234567", "Republic", "Republic",
                "https://republic.com/example", "SEC_EDGAR".equals(provenance) ? "https://www.sec.gov/example" : null,
                "accession", "020-1", "C", LocalDate.now(), "REG_CF", security, minimum, target,
                maximum, valuation, deadline, null, Status.ACTIVE, "TEST", match, 85, "Matched", now, now,
                provenance, reconciliation, "ACTIVE");
    }
}
