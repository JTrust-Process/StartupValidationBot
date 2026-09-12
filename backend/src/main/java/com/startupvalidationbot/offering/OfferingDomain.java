package com.startupvalidationbot.offering;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

public final class OfferingDomain {
    private OfferingDomain() {
    }

    public enum Status { ACTIVE, POSSIBLY_ACTIVE, ENDED, WITHDRAWN, TERMINATED, UNKNOWN }
    public enum MatchStatus { CONFIRMED, LIKELY, AMBIGUOUS, UNMATCHED, REJECTED }

    public record Candidate(String issuerName, String issuerCik, String issuerWebsite, String platform,
            String intermediaryName, String intermediaryCik, String offeringUrl, String secFilingUrl,
            String accessionNumber, String fileNumber, String filingType, LocalDate filingDate,
            String securityType, BigDecimal minimumInvestment, BigDecimal targetAmount,
            BigDecimal maximumAmount, String valuationOrCap, LocalDate deadline, BigDecimal amountRaised,
            String source, Map<String, String> facts) {
    }

    public record Match(Long companyId, MatchStatus status, int confidence, String reason) {
    }

    public record Offering(long id, Long radarCompanyId, String companyName, String issuerName, String issuerCik,
            String platform, String intermediaryName, String offeringUrl, String secFilingUrl,
            String accessionNumber, String fileNumber, String filingType, LocalDate filingDate,
            String offeringExemption, String securityType, BigDecimal minimumInvestment,
            BigDecimal targetAmount, BigDecimal maximumAmount, String valuationOrCap, LocalDate deadline,
            BigDecimal amountRaised, Status status, String source, MatchStatus matchStatus,
            int matchConfidence, String matchReason, LocalDateTime firstSeenAt, LocalDateTime lastSeenAt,
            String provenance, String reconciliationStatus, String platformStatus) {

        public Offering(long id, Long radarCompanyId, String companyName, String issuerName, String issuerCik,
                String platform, String intermediaryName, String offeringUrl, String secFilingUrl,
                String accessionNumber, String fileNumber, String filingType, LocalDate filingDate,
                String offeringExemption, String securityType, BigDecimal minimumInvestment,
                BigDecimal targetAmount, BigDecimal maximumAmount, String valuationOrCap, LocalDate deadline,
                BigDecimal amountRaised, Status status, String source, MatchStatus matchStatus,
                int matchConfidence, String matchReason, LocalDateTime firstSeenAt, LocalDateTime lastSeenAt) {
            this(id, radarCompanyId, companyName, issuerName, issuerCik, platform, intermediaryName,
                    offeringUrl, secFilingUrl, accessionNumber, fileNumber, filingType, filingDate,
                    offeringExemption, securityType, minimumInvestment, targetAmount, maximumAmount,
                    valuationOrCap, deadline, amountRaised, status, source, matchStatus, matchConfidence,
                    matchReason, firstSeenAt, lastSeenAt, "SEC_EDGAR", "SEC_RECONCILED", null);
        }
    }

    public record DiscoveryResult(int recordsInspected, int created, int updated, int confirmed,
            int possible, int errors, java.util.List<String> errorMessages) {
    }

    public record Diagnostics(long offeringsStored, long confirmedMatches, long possibleMatches,
            String sourceStatus, LocalDateTime sourceLastSuccessAt, String sourceError,
            String lastJobStatus, LocalDateTime lastJobStartedAt, LocalDateTime lastJobCompletedAt,
            Long lastJobDurationMs, int recordsInspected, int newOfferings, int updatedOfferings,
            int errorCount) {
    }
}
