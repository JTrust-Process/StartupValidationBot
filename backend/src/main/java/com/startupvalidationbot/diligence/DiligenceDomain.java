package com.startupvalidationbot.diligence;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.startupvalidationbot.diligence.discovery.CampaignDiscoveryDomain;

public final class DiligenceDomain {
    private DiligenceDomain() { }

    public enum PacketStatus { PENDING, RESOLVING_IDENTITY, GATHERING_EVIDENCE, READY, PARTIAL, NEEDS_REVIEW, FAILED }
    public enum EvidenceClassification {
        SEC_FILED_FACT, PLATFORM_ISSUER_CLAIM, ISSUER_WEBSITE_CLAIM,
        PUBLIC_REPORTING, DETERMINISTIC_INFERENCE, AI_SYNTHESIS
    }
    public enum CampaignStatus { ACTIVE, FUNDED, CLOSED, RESERVATION, UNKNOWN, UNAVAILABLE }

    public record Evidence(long id, String sourceType, String sourceUrl, String sourceTitle,
            String factKey, String factValue, String period, EvidenceClassification classification,
            LocalDateTime observedAt, int confidence, String rawExcerpt, Map<String, Object> metadata) { }

    public record FinancialPeriod(String period, BigDecimal revenue, BigDecimal costOfGoods,
            BigDecimal netIncome, BigDecimal cash, BigDecimal assets, BigDecimal liabilities,
            BigDecimal shortTermDebt, BigDecimal longTermDebt, BigDecimal taxesPaid,
            String sourceAccessionNumber, String sourceUrl) { }

    public record PlatformCampaign(Long id, long offeringId, String platform, String campaignUrl,
            String campaignUrlSource, int campaignUrlConfidence, CampaignStatus status,
            String issuerName, String securityType, BigDecimal minimumInvestment,
            BigDecimal pricePerShare, BigDecimal valuation, BigDecimal valuationCap,
            BigDecimal discountPercent, BigDecimal targetAmount, BigDecimal maximumAmount,
            BigDecimal amountRaised, Integer investorCount, LocalDate deadline, String headline,
            Map<String, String> facts, String sourceFingerprint, LocalDateTime lastCheckedAt,
            LocalDateTime lastVerifiedAt) { }

    public record Packet(long id, long radarCompanyId, String companyName, long offeringId,
            String platform, String campaignUrl, String secFilingUrl, PacketStatus status,
            String identityStatus, int completeness, int confidence, String summary,
            List<String> bullCase, List<String> bearCase, List<String> keyRisks,
            List<String> unansweredQuestions, List<String> materialDiscrepancies,
            List<String> nextMonitoringMilestones, List<String> sourcesChecked,
            List<String> dataNotFound, LocalDateTime generatedAt, LocalDateTime lastRefreshedAt,
            LocalDateTime reviewedAt, String securityType, BigDecimal minimumInvestment,
            BigDecimal targetAmount, BigDecimal maximumAmount, BigDecimal amountRaised,
            String valuationOrCap, LocalDate deadline, List<Evidence> evidence,
            List<FinancialPeriod> financials) { }

    public record AvailabilityCheck(String sourceType, String status, String resultSummary,
            String unresolvedQuestion, LocalDateTime checkedAt) { }

    public record CompanyAvailability(long companyId, LocalDateTime lastFullCheck,
            List<AvailabilityCheck> checks) { }

    public record RunResult(int companiesConsidered, int offeringsConsidered, int identitiesResolved,
            int campaignsResolved, int packetsReady, int packetsPartial, int needsReview,
            int platformErrors, int aiFallbacks, int emailsQueued, int emailsSent, int emailsFailed,
            List<String> errors, CampaignDiscoveryDomain.RunResult campaignDiscovery) { }

    public record PlatformDiagnostic(String platform, LocalDateTime lastCheckedAt, String status,
            int requests, int campaignsFound, String error) { }

    public record NotificationDiagnostic(boolean configured, String lastStatus, String lastMessageId,
            String lastError) { }

    public record Diagnostics(String lastRunStatus, LocalDateTime lastRunStartedAt,
            LocalDateTime lastRunCompletedAt, Long lastRunDurationMs, int companiesConsidered,
            int offeringsConsidered, int identitiesResolved, int campaignsResolved,
            int packetsReady, int packetsPartial, int needsReview, int platformErrors,
            int aiFallbacks, int emailsQueued, int emailsSent, int emailsFailed,
            List<PlatformDiagnostic> platforms, NotificationDiagnostic resend,
            int campaignCompaniesEligible, int campaignCompaniesSearched,
            int campaignCandidatesFound, int campaignConfirmed, int campaignPossible,
            int campaignRejected, int campaignCacheHits, int campaignDiscoveryErrors,
            List<CampaignDiscoveryDomain.PlatformDiagnostic> campaignDiscoveryPlatforms) { }
}
