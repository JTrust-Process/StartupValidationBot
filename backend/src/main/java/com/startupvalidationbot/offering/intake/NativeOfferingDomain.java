package com.startupvalidationbot.offering.intake;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class NativeOfferingDomain {
    private NativeOfferingDomain() { }

    public enum Status {
        ACTIVE, RESERVATION, CLOSING_SOON, CLOSED, WITHDRAWN, TERMINATED, UNKNOWN;

        public boolean actionable() {
            return this == ACTIVE || this == RESERVATION || this == CLOSING_SOON;
        }
    }

    public enum ReconciliationStatus {
        PLATFORM_CONFIRMED, SEC_RECONCILED, POSSIBLE, NEEDS_REVIEW
    }

    public record NativeOfferingCandidate(
            String source,
            String platform,
            String externalId,
            String companyName,
            String canonicalUrl,
            String issuerWebsite,
            String issuerDomain,
            Status status,
            String exemption,
            String intermediaryName,
            String securityType,
            BigDecimal amountRaised,
            BigDecimal targetAmount,
            BigDecimal maximumAmount,
            String valuationOrCap,
            BigDecimal minimumInvestment,
            BigDecimal pricePerShare,
            LocalDate deadline,
            String category,
            String description,
            String issuerCik,
            String secAccessionNumber,
            String secFileNumber,
            String secFilingUrl,
            String filingType,
            LocalDate filingDate,
            Map<String, String> sourceEvidence,
            LocalDateTime retrievedAt) {

        public boolean isRegCf() {
            String value = exemption == null ? "" : exemption.toUpperCase();
            return value.contains("REG_CF") || value.contains("REG CF")
                    || value.contains("REGULATION CROWDFUNDING");
        }

        public boolean actionableRegCf() {
            return status.actionable() && isRegCf();
        }

        public boolean reviewableRecentSecRegCf() {
            if (!isRegCf() || status != Status.UNKNOWN || filingDate == null
                    || !source.startsWith("SEC_")) return false;
            String form = filingType == null ? "" : filingType.toUpperCase();
            return (form.equals("C") || form.equals("C/A"))
                    && !filingDate.isBefore(LocalDate.now().minusDays(30));
        }
    }

    public record SourceResult(String source, String capability, String status,
            boolean directoryFetched, int requests, int detailRequests,
            List<NativeOfferingCandidate> candidates, List<String> errors) {

        public static SourceResult failed(String source, String capability, String error, int requests) {
            return new SourceResult(source, capability, "UNAVAILABLE", false, requests, 0,
                    List.of(), List.of(error));
        }
    }

    public record SourceDiagnostic(String source, String capability, String status,
            LocalDateTime lastCheckedAt, boolean directoryFetched, int requests,
            int detailRequests, int candidates, int activeCandidates, int inserted,
            int matched, int rejected, String error) { }

    public record RunResult(int candidatesFound, int activeCandidates, int newCompanies,
            int matchedCompanies, int newOfferings, int updatedOfferings, int duplicatesPrevented,
            int possible, int rejected, int errors, int secRecentInspected,
            int secPlatformClassified, int secReconciled, int reviewQueueBefore,
            List<SourceDiagnostic> sources, List<String> errorMessages) {

        public static RunResult empty() {
            return new RunResult(0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, List.of(), List.of());
        }
    }
}
